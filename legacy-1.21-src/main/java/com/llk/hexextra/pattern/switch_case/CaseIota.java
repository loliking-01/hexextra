package com.llk.hexextra.pattern.switch_case;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.utils.TreeList;
import com.llk.hexextra.core.HexextraIotaTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * switch 的一条 case —— 「常量 → 命中时执行的代码块」。
 *
 * <h2>为什么它得是独立类型,而不是"只含一条 case 的 switch 表"</h2>
 * 因为那样一来「造一条 case」与「造一张表」会产出<b>同一种</b> iota,
 * 只有 case 条数不同 —— 玩家从类型上分不清手里拿的是"一条分支"还是"一张完整的表"。
 * 分开以后:
 * <ul>
 *   <li>{@link CaseIota} 是<b>原料</b>:用「加法之馏化」把若干条 case 拼成表,或往表里追加;</li>
 *   <li>{@link SwitchIota} 是<b>成品</b>:能被执行的表。</li>
 * </ul>
 *
 * <h2>它不可执行</h2>
 * 本类刻意<b>不覆写</b> {@code executable()} 与 {@code execute()} —— 于是对一条单独的 case
 * 执行(比如在它上面画「赫尔墨斯之策略」)会照咒法学惯例得到
 * {@code MishapUnescapedValue}("未逃逸的值"),玩家一看就知道用错了。
 * 这正好把"原料"与"成品"的界限划死。
 *
 * <h2>类型不在这里存</h2>
 * 匹配类型由 {@link #value} 的类型决定(见 {@link #matchType()}),不必单独存一份 ——
 * 免得出现"存着的类型"与"常量的实际类型"不一致的非法状态。
 */
public class CaseIota extends Iota {

    private final Iota value;
    private final TreeList<Iota> body;

    /**
     * @param value 匹配用的常量(数值 / 布尔 / null / 图案)
     * @param body  命中时执行的代码块
     */
    public CaseIota(Iota value, TreeList<Iota> body) {
        super(HexextraIotaTypes.CASE::get);
        this.value = value;
        this.body = body;
    }

    /** 匹配用的常量。 */
    public Iota getValue() {
        return this.value;
    }

    /** 命中时执行的代码块。 */
    public TreeList<Iota> getBody() {
        return this.body;
    }

    /**
     * 这条 case 要求的匹配类型。
     *
     * <p>正常途径(用「凯斯之策略」造)一定非 null;
     * 只有存档被手改之类的情况下才可能为 null —— 那时建表会报
     * {@code CaseNotConstant},而不是悄悄放过。
     */
    public @Nullable SwitchIota.MatchType matchType() {
        return SwitchIota.MatchType.of(this.value);
    }

    /** 真假:一条 case 总是"有东西可做"的,所以恒真(对照 {@code ListIota} 非空为真)。 */
    @Override
    public boolean isTruthy(@Nullable CastingEnvironment env) {
        return true;
    }

    /** 容差相等:类型相同且常量与代码块都逐层相等。 */
    @Override
    protected boolean toleratesOther(Iota that) {
        return typesMatch(this, that)
                && that instanceof CaseIota other
                && this.value.equals(other.value)
                && this.body.equals(other.body);
    }

    /** 与 {@link #toleratesOther} 保持一致的散列。 */
    @Override
    public int hashCode() {
        return Objects.hash(this.value, this.body);
    }

    /** 悬停显示:{@code case 值 -> {代码块}}。 */
    @Override
    public Component display() {
        MutableComponent out = Component.empty();
        out.append(Component.literal("case ").withStyle(ChatFormatting.GOLD));
        out.append(this.value.display());
        out.append(Component.literal(" -> {").withStyle(ChatFormatting.GOLD));
        // 代码块照搬原版列表 iota 的渲染
        out.append(new ListIota(this.body).display());
        out.append(Component.literal("}").withStyle(ChatFormatting.GOLD));
        return out;
    }

    /** 内存占用(与 ListIota / IfElseIota / SwitchIota 同一口径)。 */
    @Override
    public int size() {
        int total = 2;   // 自身 + 匹配值
        for (Iota iota : this.body) {
            total += iota.size();
        }
        return total;
    }

    /** 嵌套深度。 */
    @Override
    public int depth() {
        int maxChild = this.value.depth();
        for (Iota iota : this.body) {
            maxChild = Math.max(maxChild, iota.depth());
        }
        return maxChild + 1;
    }

    /**
     * 直接子 iota —— 匹配值 + 代码块里的元素。
     *
     * <p>用途只有一个:咒法学的 {@code MishapOthersName} 会递归扫描 iota 树查"别人的真名"。
     */
    @Override
    public @Nullable Iterable<Iota> subIotas() {
        List<Iota> out = new ArrayList<>();
        out.add(this.value);
        this.body.forEach(out::add);
        return out;
    }

    /** 本 iota 的类型对象。 */
    public static final IotaType<CaseIota> TYPE = new IotaType<>() {

        @Override
        public MapCodec<CaseIota> codec() {
            // IotaType.codec() 要的是 MapCodec,所以用 CASE_MAP_CODEC 而不是 CASE_CODEC
            return SwitchCodecs.CASE_MAP_CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CaseIota> streamCodec() {
            return SwitchCodecs.CASE_STREAM_CODEC;
        }

        /** 与 switch 表的蓝色区分开的青色。 */
        @Override
        public int color() {
            return 0xff_55aacc;
        }
    };
}
