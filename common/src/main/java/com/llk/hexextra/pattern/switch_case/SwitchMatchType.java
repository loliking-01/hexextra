package com.llk.hexextra.pattern.switch_case;

import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 一条 switch 匹配什么类型 —— 在<b>建造期</b>用一个样例键的类型声明下来。
 *
 * <h2>为什么是密封接口而不是枚举</h2>
 * 枚举只有固定的几种,而 iota 类型是<b>开放</b>的:任何模组都能注册新的。
 * 写死成枚举,「用 MoreIotas 的字符串当键」就永远无法支持;
 * 改成「一种万能类型」,又破坏了"一张表只匹配一种类型"这条约束。
 *
 * <p>所以类型有两种:
 * <ul>
 *   <li>{@link Core} —— 咒法学自带的三种,键用精确的原生形式;</li>
 *   <li>{@link Foreign} —— 其它任何 iota 类型,按它的<b>注册 id</b> 区分,
 *       键用序列化后的 {@code Tag}(自带精确 equals/hashCode)。</li>
 * </ul>
 *
 * <h2>null 键不是一种类型</h2>
 * null 键可以进任何类型的表,所以 {@link #of(Iota)} 对 null 返回 {@code null}(表示未定)。
 * 只用 null 键造出来的表处于"未定类型"状态,之后可以接收任何类型的键;
 * 而在已确定类型的表里,null 分支永远命中不到,建索引时直接跳过。
 *
 * <h2>为什么 Foreign 的 id 是运行时读的</h2>
 * 从 {@code HexIotaTypes.REGISTRY.getKey(iota.getType())} 拿 —— 我们不写死任何外部 id,
 * 也就不需要编译期依赖对方。
 */
public sealed interface SwitchMatchType {

    /** 核心三型。 */
    enum Kind { NUMBER, BOOLEAN, PATTERN }

    /** 咒法学自带的三种可精确分派的类型。 */
    record Core(Kind kind) implements SwitchMatchType {

        @Override
        public String translationKey() {
            return "hexextra.switch.type." + this.kind.name().toLowerCase();
        }

        @Override
        public boolean accepts(Iota iota) {
            return switch (this.kind) {
                case NUMBER -> iota instanceof DoubleIota;
                case BOOLEAN -> iota instanceof BooleanIota;
                case PATTERN -> iota instanceof PatternIota;
            };
        }

        /**
         * 原生精确键。
         *
         * <p>数值用 64 位位模式而不是 {@code DoubleIota.hashCode()}:后者把 double 压成 int,
         * 存在撞键;同时把 {@code -0.0} 归一成 {@code 0.0}(Java 的 {@code ==} 认为两者相等,
         * 但位模式不同)。
         */
        @Override
        public Object keyOf(Iota iota) {
            return switch (this.kind) {
                case NUMBER -> {
                    double v = ((DoubleIota) iota).getDouble();
                    yield Double.doubleToLongBits(v == 0.0 ? 0.0 : v);
                }
                case BOOLEAN -> ((BooleanIota) iota).getBool();
                // 0.11.4 的 HexPattern 用 getAngles() 作签名(自带 equals/hashCode)
                case PATTERN -> ((PatternIota) iota).getPattern().getAngles();
            };
        }
    }

    /** 其它模组(或其它类型)注册的 iota,按注册 id 区分。 */
    record Foreign(ResourceLocation iotaTypeId) implements SwitchMatchType {

        /** 外部类型没有本地化名,显示 id。 */
        @Override
        public @Nullable String translationKey() {
            return null;
        }

        @Override
        public boolean accepts(Iota iota) {
            return this.iotaTypeId.equals(HexIotaTypes.REGISTRY.getKey(iota.getType()));
        }

        /**
         * 序列化后的 {@code Tag} 作键。
         *
         * <p>这是<b>唯一</b>不依赖对方实现的通用做法:任何 iota 都能序列化,
         * 而 {@code CompoundTag} 自带精确的 equals/hashCode(不含容差)。
         * 它同时天然是快照 —— 矩阵被原地改动也不会影响已经算好的键。
         */
        @Override
        public Object keyOf(Iota iota) {
            try {
                return IotaType.serialize(iota);
            } catch (RuntimeException e) {
                // 极少数 iota 可能序列化失败(值过大 / 类型实现有 bug)。
                // 那不该把服务端崩掉,而应当让玩家看到一条普通的事故提示。
                throw new com.llk.hexextra.core.HexextraMishaps.CaseNotConstant(iota);
            }
        }
    }

    // 常用的核心类型常量(省去到处 new Core(...))
    Core NUMBER = new Core(Kind.NUMBER);
    Core BOOLEAN = new Core(Kind.BOOLEAN);
    Core PATTERN = new Core(Kind.PATTERN);

    /** 本地化名所用的翻译键;外部类型返回 null。 */
    @Nullable String translationKey();

    /**
     * 报错文案与 tooltip 里用的类型名。
     *
     * <p>核心三型给翻译键(由语言文件本地化);外部类型直接给注册 id。
     */
    default String displayKey() {
        String key = this.translationKey();
        return key != null ? key : ((Foreign) this).iotaTypeId().toString();
    }

    /** 存档里用的类型标识:核心三型是 {@code NUMBER} 等,外部类型是注册 id。 */
    default String id() {
        return this instanceof Core core ? core.kind().name() : ((Foreign) this).iotaTypeId().toString();
    }

    /** 从存档里的标识解析类型;认不出来返回 null(由调用方决定怎么报错)。 */
    static @Nullable SwitchMatchType fromId(String s) {
        for (Kind kind : Kind.values()) {
            if (kind.name().equalsIgnoreCase(s)) {
                return new Core(kind);
            }
        }
        ResourceLocation rl = ResourceLocation.tryParse(s);
        return rl == null ? null : new Foreign(rl);
    }

    /** 这个 iota 是否属于本类型。 */
    boolean accepts(Iota iota);

    /** 在这个类型的表里,该 iota 的查表键。 */
    Object keyOf(Iota iota);

    /**
     * 从一个样例键推出类型。
     *
     * @return null 表示「未定」—— 样例是 null 键(可进任何表)
     */
    static @Nullable SwitchMatchType of(Iota sample) {
        // null 不是一种"类型":它作为键时可以进任何类型的表。
        if (sample instanceof NullIota) {
            return null;
        }
        if (sample instanceof DoubleIota) return NUMBER;
        if (sample instanceof BooleanIota) return BOOLEAN;
        if (sample instanceof PatternIota) return PATTERN;
        // 其余一律按"外部类型"处理,类型 id 运行时从它自己身上读
        ResourceLocation id = HexIotaTypes.REGISTRY.getKey(sample.getType());
        if (id == null) {
            // 理论上走不到(能出现在栈上的 iota 一定已注册);
            // 真遇到也报事故,不要抛 IllegalStateException 把服务端崩掉。
            throw new com.llk.hexextra.core.HexextraMishaps.CaseNotConstant(sample);
        }
        return new Foreign(id);
    }
}
