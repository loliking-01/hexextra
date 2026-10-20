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
 *
 * <h2>为什么只有这三种</h2>
 * 只有数值 / 布尔 / 图案才有「精确、可哈希的规范键」。列表 / 实体 / 向量拿不到稳定的键,
 * 也谈不上等值分派。
 *
 * <h2>null 不是一种类型</h2>
 * null 键可以进**任何**类型的表 —— 所以 {@link #of(Iota)} 对 null 返回 {@code null}
 * (表示「未定」),而不是某个 NULL 类型。只用 null 键造出来的表处于未定类型状态,
 * 之后可以接收任何类型的键;而在已确定类型的表里,null 分支永远命中不到
 * (受控值不可能是 null),建索引时直接跳过。
 */
public enum SwitchMatchType {

    /** 数值。键用规范化后的 64 位位模式。 */
    NUMBER,

    /** 布尔。键直接用 Boolean。 */
    BOOLEAN,

    /** 图案。键用 HexPattern 的签名(自带 equals / hashCode)。 */
    PATTERN;

    /** 给玩家看的类型名所用的翻译键。 */
    public String translationKey() {
        return "hexextra.switch.type." + this.name().toLowerCase();
    }

    /** 这个类型能否接受该值。 */
    public boolean accepts(Iota iota) {
        return switch (this) {
            case NUMBER -> iota instanceof DoubleIota;
            case BOOLEAN -> iota instanceof BooleanIota;
            case PATTERN -> iota instanceof PatternIota;
        };
    }

    /**
     * 由样例常量反推类型。
     *
     * @return null 表示「未定」:一是样例是 null 键(可以进任何表),
     *         二是样例根本不是可匹配的常量(调用方负责对后者报错)
     */
    public static @Nullable SwitchMatchType of(Iota iota) {
        if (iota instanceof DoubleIota) return NUMBER;
        if (iota instanceof BooleanIota) return BOOLEAN;
        if (iota instanceof PatternIota) return PATTERN;
        // NullIota 也走这里:null 不决定类型
        return null;
    }

    /**
     * 把一个值变成查表用的键;类型不符(含 null 值)返回 null。
     *
     * <p>数值走 {@link #numberKey(double)}:要<b>精确</b>而不是容差 ——
     * 咒法学默认的 iota 相等带 1e-4 容差,那种相等不满足传递性,
     * 又与精确 hashCode 冲突,建不出自洽的哈希表。
     */
    public @Nullable Object keyFor(Iota iota) {
        if (!this.accepts(iota)) {
            return null;
        }
        return switch (this) {
            case NUMBER -> numberKey(((DoubleIota) iota).getDouble());
            case BOOLEAN -> ((BooleanIota) iota).getBool();
            case PATTERN -> ((PatternIota) iota).getPattern().getAngles();
        };
    }

    /**
     * 数值的规范键。
     *
     * <p>用 {@link Double#doubleToLongBits} 而不是 {@code DoubleIota.hashCode()}:
     * 后者把 double 压缩成 int,存在撞键可能;64 位位模式是双射。
     *
     * <p>同时把 {@code -0.0} 归一成 {@code 0.0} —— Java 的 {@code ==} 认为两者相等,
     * 但位模式不同,不归一就会出现"相等却查不到"。
     */
    private static long numberKey(double value) {
        return Double.doubleToLongBits(value == 0.0 ? 0.0 : value);
    }
}