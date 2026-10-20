package com.llk.hexextra.pattern.switch_case;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.utils.TreeList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * switch 家族({@link SwitchIota} 表 与 {@link SwitchIota.Case} 一条 case)的全部编解码器,
 * 集中在一个<b>中立</b>的持有类里。
 *
 * <h2>⚠ 为什么不能放在 {@link SwitchIota} 或 {@link SwitchIota.Case} 里</h2>
 * 这是本项目踩过一次、代价是<b>整个服务端起不来</b>的坑(见 {@code IfElseCodecs} 的注释):
 * 若内层类型的静态字段引用了外层类的静态字段,就会形成循环静态初始化 ——
 * 「嵌套类初始化不触发外部类初始化」这条规则救不了你,因为循环正是那条跨层引用造成的。
 *
 * <p>这里之所以两个类互相需要(表要 case 的编解码器,case 的编解码器又要知道自己属于谁)
 * 却没出问题,是因为本类<b>只用方法引用与构造器引用</b>去指它们
 * (如 {@code SwitchIota.Case::getValue}、{@code SwitchIota.Case::new}),从不读它们的静态字段 ——
 * 方法引用只触发类的<b>加载</b>,不触发<b>初始化</b>,所以不会回环。
 *
 * <p><b>改动本类时务必守住这条:</b>不要在这里出现 {@code XxxIota.TYPE} 或 {@code XxxIota.SOMETHING}。
 */
public final class SwitchCodecs {

    /** 代码块的 NBT 编解码器(就是一个 iota 序列)。 */
    public static final Codec<TreeList<Iota>> CODE_BLOCK = TreeList.codecOf(IotaType.TYPED_CODEC);

    /** 代码块的网络编解码器。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, TreeList<Iota>> CODE_BLOCK_STREAM =
            IotaType.TYPED_STREAM_CODEC.apply(TreeList.streamCodecOp());

    /**
     * 一条 case 的字段编解码器。
     *
     * <p>匹配值用 {@link IotaType#TYPED_CODEC} 而不限定成常量类型:
     * 「必须是常量」这条规则在<b>建造时</b>检查并报错,这样玩家拿到的是能看懂的提示,
     * 而不是"存档里有条数据解不开"。
     */
    private static final MapCodec<SwitchIota.Case> CASE_FIELDS = RecordCodecBuilder.mapCodec(inst -> inst.group(
            IotaType.TYPED_CODEC.fieldOf("value").forGetter(SwitchIota.Case::getValue),
            CODE_BLOCK.fieldOf("body").forGetter(SwitchIota.Case::getBody)
    ).apply(inst, SwitchIota.Case::new));

    /** 一条 case 的 NBT 编解码器(表里以列表形式内嵌它时用这个)。 */
    public static final Codec<SwitchIota.Case> CASE_CODEC = CASE_FIELDS.codec();

        /** 一条 case 的网络编解码器。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchIota.Case> CASE_STREAM_CODEC =
            StreamCodec.composite(
                    IotaType.TYPED_STREAM_CODEC, SwitchIota.Case::getValue,
                    CODE_BLOCK_STREAM, SwitchIota.Case::getBody,
                    SwitchIota.Case::new);

    /**
     * 匹配类型的 NBT 编解码器。
     *
     * <p>用 {@code comapFlatMap} 而不是 {@code xmap}:名字不认识时应当<b>明确报错</b>,
     * 而不是悄悄回退到某个默认类型 —— 后者会把存档损坏变成静默的行为改变。
     */
    public static final Codec<SwitchIota.MatchType> MATCH_TYPE = Codec.STRING.comapFlatMap(
            name -> {
                for (SwitchIota.MatchType type : SwitchIota.MatchType.values()) {
                    if (type.name().equalsIgnoreCase(name)) {
                        return DataResult.success(type);
                    }
                }
                return DataResult.error(() -> "hexextra: 未知的 switch 匹配类型 '" + name + "'");
            },
            SwitchIota.MatchType::name);

    /** 匹配类型的网络编解码器(按序号,并做越界保护)。 */
    public static final StreamCodec<ByteBuf, SwitchIota.MatchType> MATCH_TYPE_STREAM =
            ByteBufCodecs.VAR_INT.map(
                    i -> (i >= 0 && i < SwitchIota.MatchType.values().length)
                            ? SwitchIota.MatchType.values()[i]
                            : SwitchIota.MatchType.NUMBER,
                    SwitchIota.MatchType::ordinal);

    /** switch 表的 NBT 编解码器:匹配类型 + case 表 + default(可空)。 */
    public static final MapCodec<SwitchIota> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            MATCH_TYPE.fieldOf("match_type").forGetter(SwitchIota::getMatchType),
            CASE_CODEC.listOf().fieldOf("cases").forGetter(SwitchIota::getCases),
            CODE_BLOCK.optionalFieldOf("default").forGetter(SwitchIota::getDefaultBodyOptional)
    ).apply(inst, (type, cases, def) -> new SwitchIota(type, cases, def.orElse(null))));

    /**
     * switch 表的网络编解码器。
     *
     * <p>字段顺序必须与 {@link #CODEC} 完全一致(匹配类型 / case 表 / default),
     * 否则字节流会整体错位 —— 这类错误不会报异常,只会静默读出错的数据。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchIota> STREAM_CODEC =
            StreamCodec.composite(
                    MATCH_TYPE_STREAM, SwitchIota::getMatchType,
                    CASE_STREAM_CODEC.apply(ByteBufCodecs.list()), SwitchIota::getCases,
                    ByteBufCodecs.optional(CODE_BLOCK_STREAM), SwitchIota::getDefaultBodyOptional,
                    (type, cases, def) -> new SwitchIota(type, cases, def.orElse(null)));

    private SwitchCodecs() {
    }
}
