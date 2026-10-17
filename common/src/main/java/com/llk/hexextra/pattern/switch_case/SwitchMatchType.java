package com.llk.hexextra.pattern.switch_case;

import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import org.jetbrains.annotations.Nullable;

/**
 * switch 表「匹配的是什么类型」。
 *
 * <h2>为什么需要它</h2>
 * 一条表只能匹配一种类型,否则查表时无法为不同的值建立统一的键。
 * 类型由<b>第一条 case 的常量</b>推出(见 {@link CaseIota#matchType()}),
 * 之后塞入类型不符的 case 会当场报错。
 *
 * <h2>为什么只有这四种</h2>
 * 因为这四种才有「精确、可哈希的规范键」。列表 / 实体 / 向量拿不到稳定的键,
 * 也谈不上"等值分派"。
 *
 * <h2>与 1.21.1 版的差异</h2>
 * 逻辑完全一致;只是 0.12.0 上这个枚举嵌在 {@code SwitchIota} 里,
 * 这里拆成独立文件,好让 {@link CaseIota} 不必依赖整张表的实现。
 */
public enum SwitchMatchType {

    /** 数值。键用规范化后的 64 位位模式。 */
    NUMBER,

    /** 布尔。键直接用 Boolean。 */
    BOOLEAN,

    /** null。键用一个哨兵对象(唯一的 null 值)。 */
    NULL,

    /** 图案。键用 HexPattern.getAngles()(它自带 equals / hashCode)。 */
    PATTERN;

    /** 给玩家看的类型名所用的翻译键。 */
    public String translationKey() {
        return "hexextra.switch.type." + this.name().toLowerCase();
    }

    /**
     * 这个类型能否接受该值。
     *
     * <p>只按 iota 的实际类型判断 —— 这也是 {@code accepts} 能当常量的原因。
     */
    public boolean accepts(Iota iota) {
        return switch (this) {
            case NUMBER -> iota instanceof DoubleIota;
            case BOOLEAN -> iota instanceof BooleanIota;
            case NULL -> iota instanceof NullIota;
            case PATTERN -> iota instanceof PatternIota;
        };
    }

    /**
     * 由样例常量反推类型。
     *
     * @return 不是可匹配的常量类型时返回 null(调用方负责报错)
     */
    public static @Nullable SwitchMatchType of(Iota iota) {
        if (iota instanceof DoubleIota) return NUMBER;
        if (iota instanceof BooleanIota) return BOOLEAN;
        if (iota instanceof NullIota) return NULL;
        if (iota instanceof PatternIota) return PATTERN;
        return null;
    }

    /**
     * 把一个值变成查表用的键。
     *
     * <p>数值走 {@link #numberKey(double)}:要<b>精确</b>而不是容差 ——
     * 咒法学默认的 iota 相等是带 1e-4 容差的,那种相等既不满足传递性、
     * 又与精确 hashCode 冲突,根本建不出自洽的哈希表(详见备忘 05)。
     */
    public @Nullable Object keyFor(Iota iota) {
        if (!this.accepts(iota)) {
            return null;
        }
        return switch (this) {
            case NUMBER -> numberKey(((DoubleIota) iota).getDouble());
            case BOOLEAN -> ((BooleanIota) iota).getBool();
            case NULL -> NULL_KEY;
            case PATTERN -> ((PatternIota) iota).getPattern().getAngles();
        };
    }

    /**
     * 数值的规范键。
     *
     * <p>用 {@link Double#doubleToLongBits} 而不是 {@code DoubleIota.hashCode()}:
     * 后者会把 double 压缩成 int,存在撞键可能;64 位位模式是双射。
     *
     * <p>同时把 {@code -0.0} 归一成 {@code 0.0} —— Java 的 {@code ==} 认为两者相等,
     * 但它们的位模式不同,不归一就会出现"相等却查不到"。
     */
    private static long numberKey(double value) {
        return Double.doubleToLongBits(value == 0.0 ? 0.0 : value);
    }

    /** NULL 类型的键:全局唯一的哨兵,避免用 null 当 HashMap 的键。 */
    public static final Object NULL_KEY = new Object();
}
