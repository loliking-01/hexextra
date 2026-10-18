package com.llk.hexextra.core;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.api.utils.TreeList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * hexextra 的自定义施法报错(Mishap)。
 *
 * <h2>为什么不用官方的 MishapInvalidIota</h2>
 * 那个报错的文案是「期望 X,实际是 Y」,但它要求身上有一个"肇事 iota"。
 * 而"同一个常量在 switch 里出现了两次"这类错误<b>没有单一肇事者</b>(是两个都在),
 * 硬塞一个进去提示会误导玩家。所以自建,把话说清楚。
 *
 * <h2>与 1.21.1 版的差异</h2>
 * 0.11.4 的 {@link Mishap#execute} 第三个参数是 <b>{@code MutableList<Iota>}</b>,
 * 不是 0.12.0 的 {@code TreeList<Iota>}。Java 侧类型写 {@code List<Iota>} 即可
 * (Kotlin 的 MutableList 编译后就是 java.util.List)。
 *
 * <p>类型信息以<b>翻译键字符串</b>传入(如 {@code hexextra.switch.type.number}),
 * 而不是传功能层的枚举 —— 这样 core 不会反向依赖具体功能,又仍然可本地化。
 */
public final class HexextraMishaps {

    private HexextraMishaps() {
    }

    /**
     * 同一个常量在 switch 里对应了不止一个 case。
     *
     * <p>照 Java 的规矩:重复的 case 是编译错误。放任不管的话玩家根本没法从界面
     * 判断"到底会走哪一个",而建索引时也会互相覆盖。
     */
    public static final class DuplicateCase extends Mishap {

        private final Iota value;

        public DuplicateCase(Iota value) {
            this.value = value;
        }

        public Iota getValue() {
            return this.value;
        }

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return this.dyeColor(DyeColor.LIGHT_BLUE);
        }

        /** 本报错不改动栈。 */
        @Override
        public void execute(CastingEnvironment env, Mishap.Context errorCtx, List<Iota> stack) {
        }

        @Override
        protected @Nullable Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.duplicate_case", this.value.display());
        }
    }

    /**
     * 拿来当 case 的值根本不是常量。
     *
     * <p>与 {@link CaseTypeMismatch} 的区别:这里是"压根不是可匹配的类型"
     * (列表 / 实体 / 向量),那里是"是常量、但类型与这张表声明的不符"。
     */
    public static final class CaseNotConstant extends Mishap {

        private final Iota value;

        public CaseNotConstant(Iota value) {
            this.value = value;
        }

        public Iota getValue() {
            return this.value;
        }

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return this.dyeColor(DyeColor.LIGHT_BLUE);
        }

        @Override
        public void execute(CastingEnvironment env, Mishap.Context errorCtx, List<Iota> stack) {
        }

        @Override
        protected @Nullable Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.case_not_constant", this.value.display());
        }
    }

    /**
     * 往 switch 里塞了一个类型不符的 case。
     *
     * <p>照 Java 的定位,这类错误应当在<b>建造期</b>就被抓住,
     * 而不是等到执行时默默不命中。
     *
     * @param expectedKey 期望类型的翻译键
     * @param actual      实际塞进来的那个值
     */
    public static final class CaseTypeMismatch extends Mishap {

        private final String expectedKey;
        private final Iota actual;

        public CaseTypeMismatch(String expectedKey, Iota actual) {
            this.expectedKey = expectedKey;
            this.actual = actual;
        }

        public String getExpectedKey() {
            return this.expectedKey;
        }

        public Iota getActual() {
            return this.actual;
        }

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return this.dyeColor(DyeColor.LIGHT_BLUE);
        }

        @Override
        public void execute(CastingEnvironment env, Mishap.Context errorCtx, List<Iota> stack) {
        }

        @Override
        protected @Nullable Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.case_type_mismatch",
                    Component.translatable(this.expectedKey), this.actual.display());
        }
    }

    /**
     * switch 拿去匹配的那个值,类型与这张表声明的不符。
     *
     * <p>照 Java「switch 不支持这种类型」的做法当场说清楚,而不是当作不命中滑到 default
     * —— 后者会把玩家的错误盖住。
     */
    public static final class SwitchValueTypeMismatch extends Mishap {

        private final String expectedKey;
        private final Iota actual;

        public SwitchValueTypeMismatch(String expectedKey, Iota actual) {
            this.expectedKey = expectedKey;
            this.actual = actual;
        }

        public String getExpectedKey() {
            return this.expectedKey;
        }

        public Iota getActual() {
            return this.actual;
        }

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return this.dyeColor(DyeColor.LIGHT_BLUE);
        }

        @Override
        public void execute(CastingEnvironment env, Mishap.Context errorCtx, List<Iota> stack) {
        }

        @Override
        protected @Nullable Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.switch_value_type_mismatch",
                    Component.translatable(this.expectedKey), this.actual.display());
        }
    }

    /**
     * 试图把两张<b>声明了不同类型</b>的表合并起来。
     *
     * <p>一张表只匹配一种类型,类型不同的两张表合并没有意义 ——
     * 默默丢掉其中一张的 case 更是会让人完全看不出问题。
     */
    public static final class SwitchTypeMismatch extends Mishap {

        private final String firstKey;
        private final String secondKey;

        public SwitchTypeMismatch(String firstKey, String secondKey) {
            this.firstKey = firstKey;
            this.secondKey = secondKey;
        }

        public String getFirstKey() {
            return this.firstKey;
        }

        public String getSecondKey() {
            return this.secondKey;
        }

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return this.dyeColor(DyeColor.LIGHT_BLUE);
        }

        @Override
        public void execute(CastingEnvironment env, Mishap.Context errorCtx, List<Iota> stack) {
        }

        @Override
        protected @Nullable Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.switch_type_mismatch",
                    Component.translatable(this.firstKey), Component.translatable(this.secondKey));
        }
    }

    /**
     * 试图把两张<b>各自都带 default</b> 的表合并,或给一张已有 default 的表再接一个。
     *
     * <p>一张表只能有一个 default —— 默默丢掉其中一个会让人完全看不出问题。
     */
    public static final class SwitchAlreadyHasDefault extends Mishap {

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return this.dyeColor(DyeColor.LIGHT_BLUE);
        }

        @Override
        public void execute(CastingEnvironment env, Mishap.Context errorCtx, List<Iota> stack) {
        }

        @Override
        protected @Nullable Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.switch_already_has_default");
        }
    }

    // =======================================================================
    // if / elseif / else(条件链)相关的报错
    // =======================================================================

    /**
     * if 的条件代码块没有「恰好」在栈上留下一个值。
     *
     * <p>玩家看到的提示会说明实际留下了几个 —— 0 个通常意味着条件块忘了产出结果,
     * 负数意味着它把原有的栈内容吃掉了,大于 1 意味着多留了东西。
     */
    public static final class IfConditionCount extends Mishap {

        private final int produced;

        public IfConditionCount(int produced) {
            this.produced = produced;
        }

        public int getProduced() {
            return this.produced;
        }

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return this.dyeColor(DyeColor.CYAN);
        }

        @Override
        public void execute(CastingEnvironment env, Mishap.Context errorCtx, List<Iota> stack) {
        }

        @Override
        protected @Nullable Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.if_condition_count", this.produced);
        }
    }

    /**
     * if 的条件代码块留下了东西,但那个东西不是布尔值。
     */
    public static final class IfConditionNotBool extends Mishap {

        private final Iota perpetrator;

        public IfConditionNotBool(Iota perpetrator) {
            this.perpetrator = perpetrator;
        }

        public Iota getPerpetrator() {
            return this.perpetrator;
        }

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return this.dyeColor(DyeColor.CYAN);
        }

        @Override
        public void execute(CastingEnvironment env, Mishap.Context errorCtx, List<Iota> stack) {
        }

        @Override
        protected @Nullable Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.if_condition_not_bool", this.perpetrator.display());
        }
    }

    /**
     * 往一条<b>已经有 else 块</b>的链上再接东西。
     *
     * <p>刻意报错而不是默默覆盖:链只能有一个最终 else,默默丢掉玩家之前接的那个
     * 会让人完全看不出问题。
     */
    public static final class ChainAlreadyHasElse extends Mishap {

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return this.dyeColor(DyeColor.ORANGE);
        }

        @Override
        public void execute(CastingEnvironment env, Mishap.Context errorCtx, List<Iota> stack) {
        }

        @Override
        protected @Nullable Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.chain_already_has_else");
        }
    }

    /**
     * 拿一条<b>自带 else 的链</b>去当分支替换项。
     *
     * <p>分支只由「条件 + 代码块」组成,不含 else;替换时若悄悄丢掉替换项里的 else,
     * 玩家会以为接上了却没接上。
     */
    public static final class ReplacementHasElse extends Mishap {

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return this.dyeColor(DyeColor.ORANGE);
        }

        @Override
        public void execute(CastingEnvironment env, Mishap.Context errorCtx, List<Iota> stack) {
        }

        @Override
        protected @Nullable Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.replacement_has_else");
        }
    }

    // =======================================================================
    // 循环取值图案的报错
    // =======================================================================

    /**
     * 取值图案(取当前次数 / 取剩余次数 / 取当前元素)脱离了循环使用。
     *
     * <p>这些图案的值来自<b>外层循环帧</b>,没有循环帧就没有值可言。
     * 刻意报错而不是给 0 或 null —— 那只会掩盖玩家的结构错误。
     */
    public static final class NoEnclosingLoop extends Mishap {

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return dyeColor(DyeColor.ORANGE);
        }

        @Override
        public void execute(CastingEnvironment env, Mishap.Context errorCtx, List<Iota> stack) {
        }

        @Override
        protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.no_enclosing_loop");
        }
    }
}