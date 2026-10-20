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

import java.util.Optional;

/**
 * switch 的全部编解码器。
 *
 * <h2>为什么集中在这个中立类里</h2>
 * {@link SwitchIota} 与 {@link SwitchIota.Case} 互相引用对方的编解码器 ——
 * 把编解码器放进任一方都会形成静态初始化回环(见 if_else 的 {@code IfElseCodecs} 里那段踩坑记录)。
 *
 * <h2>match_type 是可空的</h2>
 * 一条 null 键不决定表的类型(它兼容任何类型),所以整张表可能处于「未定类型」状态。
 * 存档里用<b>缺省该字段</b>表示未定,网络侧用 {@code -1} 表示。
 */
public final class SwitchCodecs {

    /** 代码块的 NBT 编解码器。 */
    public static final Codec<TreeList<Iota>> CODE_BLOCK = TreeList.codecOf(IotaType.TYPED_CODEC);

    /** 代码块的网络编解码器。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, TreeList<Iota>> CODE_BLOCK_STREAM =
            IotaType.TYPED_STREAM_CODEC.apply(TreeList.streamCodecOp());

    /** 一条分支的字段编解码器。 */
    private static final MapCodec<SwitchIota.Case> CASE_FIELDS = RecordCodecBuilder.mapCodec(inst -> inst.group(
            IotaType.TYPED_CODEC.fieldOf("value").forGetter(SwitchIota.Case::getValue),
            CODE_BLOCK.fieldOf("body").forGetter(SwitchIota.Case::getBody)
    ).apply(inst, SwitchIota.Case::new));

    /** 一条分支的 NBT 编解码器(表里以列表形式内嵌它时用这个)。 */
    public static final Codec<SwitchIota.Case> CASE_CODEC = CASE_FIELDS.codec();

    /** 一条分支的网络编解码器。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchIota.Case> CASE_STREAM_CODEC =
            StreamCodec.composite(
                    IotaType.TYPED_STREAM_CODEC, SwitchIota.Case::getValue,
                    CODE_BLOCK_STREAM, SwitchIota.Case::getBody,
                    SwitchIota.Case::new);

    /**
     * 匹配类型的 NBT 编解码器。
     *
     * <p>用 {@code comapFlatMap} 而不是 {@code xmap}:名字不认识时应当明确报错,
     * 而不是悄悄回退到某个默认类型。
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

    /** 可空的匹配类型:用 {@code -1} 表示「未定」。 */
    public static final StreamCodec<ByteBuf, SwitchIota.MatchType> NULLABLE_MATCH_TYPE_STREAM =
            StreamCodec.of(
                    (ByteBuf buf, SwitchIota.MatchType type) ->
                            buf.writeByte(type == null ? -1 : type.ordinal()),
                    (ByteBuf buf) -> {
                        int id = buf.readByte();
                        return (id < 0 || id >= SwitchIota.MatchType.values().length)
                                ? null
                                : SwitchIota.MatchType.values()[id];
                    });

    /** 整张表的 NBT 编解码器:匹配类型(可缺省)+ 分支表 + default(可空)。 */
    public static final MapCodec<SwitchIota> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            MATCH_TYPE.optionalFieldOf("match_type")
                    .forGetter(s -> Optional.ofNullable(s.getMatchType())),
            CASE_CODEC.listOf().fieldOf("cases").forGetter(SwitchIota::getCases),
            CODE_BLOCK.optionalFieldOf("default").forGetter(SwitchIota::getDefaultBodyOptional)
    ).apply(inst, (type, cases, def) -> new SwitchIota(type.orElse(null), cases, def.orElse(null))));

    /** 整张表的网络编解码器。字段顺序必须与 {@link #CODEC} 一致。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchIota> STREAM_CODEC =
            StreamCodec.composite(
                    NULLABLE_MATCH_TYPE_STREAM, SwitchIota::getMatchType,
                    CASE_STREAM_CODEC.apply(ByteBufCodecs.list()), SwitchIota::getCases,
                    ByteBufCodecs.optional(CODE_BLOCK_STREAM), SwitchIota::getDefaultBodyOptional,
                    (type, cases, def) -> new SwitchIota(type, cases, def.orElse(null)));

    private SwitchCodecs() {
    }
}