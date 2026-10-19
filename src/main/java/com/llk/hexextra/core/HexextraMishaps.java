package com.llk.hexextra.core;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.api.utils.TreeList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

/**
 * hexextra 的自定义施法报错(Mishap)。
 *
 * <p>为什么不用咒法学自带的 {@code MishapInvalidIota}:那个报错的文案是
 * 「期望 X,实际是 Y」,但它要求身上有一个"肇事 iota"。而"条件代码块留下的值个数不对"
 * 这类错误<b>根本没有肇事 iota</b>(栈上也许空着),用它只能瞎填一个,提示会误导玩家。
 * 所以这里自建,把话说清楚。
 *
 * <p>涉及具体类型时,类型以<b>翻译键字符串</b>传入(如 {@code hexextra.switch.type.number}),
 * 而不是传功能层的枚举 —— 这样 core 不会反向依赖具体功能,又仍然可本地化。
 */
public final class HexextraMishaps {

    private HexextraMishaps() {
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

        /** 实际净留下的值个数(可能为负:条件吃掉了原有栈内容)。 */
        private final int produced;

        public IfConditionCount(int produced) {
            this.produced = produced;
        }

        public int getProduced() {
            return this.produced;
        }

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return dyeColor(DyeColor.CYAN);
        }

        /** 本报错不改动栈 —— 原样返回。 */
        @Override
        public TreeList<Iota> execute(CastingEnvironment env, Mishap.Context errorCtx, TreeList<Iota> stack) {
            return stack;
        }

        @Override
        protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.if_condition_count", this.produced);
        }
    }

    /**
     * if 的条件代码块留下了东西,但那个东西不是布尔值。
     *
     * @param perpetrator 实际留下的那个 iota,用在提示里展示它是什么
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
            return dyeColor(DyeColor.CYAN);
        }

        @Override
        public TreeList<Iota> execute(CastingEnvironment env, Mishap.Context errorCtx, TreeList<Iota> stack) {
            return stack;
        }

        @Override
        protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            // 把肇事 iota 的显示文本嵌进去,玩家一眼能看出自己留了个什么
            return Component.translatable("hexextra.mishap.if_condition_not_bool", this.perpetrator.display());
        }
    }

    /**
     * 往一条<b>已经有 else 块</b>的链上再接东西。
     *
     * <p>这是刻意报错而不是默默覆盖:链只能有一个最终 else,默默丢掉玩家之前接的那个
     * 会让人完全看不出问题出在哪。
     */
    public static final class ChainAlreadyHasElse extends Mishap {

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return dyeColor(DyeColor.ORANGE);
        }

        @Override
        public TreeList<Iota> execute(CastingEnvironment env, Mishap.Context errorCtx, TreeList<Iota> stack) {
            return stack;
        }

        @Override
        protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.chain_already_has_else");
        }
    }

    /**
     * 拿一条<b>自带 else 的链</b>去当分支替换项。
     *
     * <p>分支只由「条件 + 代码块」组成,不含 else;替换时若悄悄丢掉替换项里的 else,
     * 玩家会以为接上了却没接上。所以直接报错说清楚。
     */
    public static final class ReplacementHasElse extends Mishap {

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return dyeColor(DyeColor.ORANGE);
        }

        @Override
        public TreeList<Iota> execute(CastingEnvironment env, Mishap.Context errorCtx, TreeList<Iota> stack) {
            return stack;
        }

        @Override
        protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.replacement_has_else");
        }
    }

    // =======================================================================
    // switch(多路等值分派)相关的报错
    // =======================================================================

    /**
     * 同一个常量在 switch 里对应了不止一个 case。
     *
     * <p>照 Java 的规矩:重复的 case 是编译错误。如果放任不管,
     * 玩家根本没法从界面判断"到底会走哪一个",而建索引时也会互相覆盖。
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
            return dyeColor(DyeColor.LIGHT_BLUE);
        }

        @Override
        public TreeList<Iota> execute(CastingEnvironment env, Mishap.Context errorCtx, TreeList<Iota> stack) {
            return stack;
        }

        @Override
        protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.duplicate_case", this.value.display());
        }
    }

    /**
     * 拿来当 case 的值根本不是常量。
     *
     * <p>与 {@link CaseTypeMismatch} 的区别:这里是"压根不是可匹配的类型"
     * (列表 / 实体 / 向量),那里是"是常量、但类型与这条表声明的不符"。
     * 分开报错才能让玩家一眼知道该改哪里。
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
            return dyeColor(DyeColor.LIGHT_BLUE);
        }

        @Override
        public TreeList<Iota> execute(CastingEnvironment env, Mishap.Context errorCtx, TreeList<Iota> stack) {
            return stack;
        }

        @Override
        protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.case_not_constant", this.value.display());
        }
    }

    /**
     * 往 switch 里塞了一个类型不符的 case。
     *
     * <p>例如:一条「数值型」的表里塞了图案 case。照 Java 的定位,这类错误
     * 应当在<b>建造期</b>就被抓住,而不是等到执行时默默不命中。
     *
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
            return dyeColor(DyeColor.LIGHT_BLUE);
        }

        @Override
        public TreeList<Iota> execute(CastingEnvironment env, Mishap.Context errorCtx, TreeList<Iota> stack) {
            return stack;
        }

        @Override
        protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.case_type_mismatch",
                    Component.translatable(this.expectedKey), this.actual.display());
        }
    }

    /**
     * switch 拿去匹配的那个值,类型与这条表声明的类型不符。
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
            return dyeColor(DyeColor.LIGHT_BLUE);
        }

        @Override
        public TreeList<Iota> execute(CastingEnvironment env, Mishap.Context errorCtx, TreeList<Iota> stack) {
            return stack;
        }

        @Override
        protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.switch_value_type_mismatch",
                    Component.translatable(this.expectedKey), this.actual.display());
        }
    }

    /**
     * 试图把两条<b>声明了不同类型</b>的 switch 合并起来。
     *
     * <p>一条表只匹配一种类型,所以类型不同的两张表合并起来没有意义 ——
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
            return dyeColor(DyeColor.LIGHT_BLUE);
        }

        @Override
        public TreeList<Iota> execute(CastingEnvironment env, Mishap.Context errorCtx, TreeList<Iota> stack) {
            return stack;
        }

        @Override
        protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.switch_type_mismatch",
                    Component.translatable(this.firstKey), Component.translatable(this.secondKey));
        }
    }

    /**
     * 试图把两条<b>各自都带 default</b> 的表合并,或给一条已有 default 的表再接一个。
     *
     * <p>一条表只能有一个 default —— 默默丢掉其中一个会让人完全看不出问题。
     */
    public static final class SwitchAlreadyHasDefault extends Mishap {

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return dyeColor(DyeColor.LIGHT_BLUE);
        }

        @Override
        public TreeList<Iota> execute(CastingEnvironment env, Mishap.Context errorCtx, TreeList<Iota> stack) {
            return stack;
        }

        @Override
        protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.switch_already_has_default");
        }
    }

    // =======================================================================
    // 循环取值图案的报错
    // =======================================================================

    /**
     * 取值图案(取当前次数 / 取剩余次数 / 取当前元素)脱离了循环使用。
     *
     * <p>这三枚图案的值来自<b>外层循环帧</b>(沿续延链找到的那个),
     * 没有循环帧就没有值可言。这里刻意报错而不是给 0 或 null ——
     * 那只会让玩家的结构错误被静默掩盖,更难排查。
     */
    public static final class NoEnclosingLoop extends Mishap {

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return dyeColor(DyeColor.ORANGE);
        }

        @Override
        public TreeList<Iota> execute(CastingEnvironment env, Mishap.Context errorCtx, TreeList<Iota> stack) {
            return stack;
        }

        @Override
        protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.no_enclosing_loop");
        }
    }

    // =======================================================================
    // try_catch 相关的报错
    // =======================================================================

    /**
     * 「try_catch」拿到的列表里代码块个数不是 2 或 3。
     */
    public static final class TryCatchBlocks extends Mishap {

        private final int count;

        public TryCatchBlocks(int count) {
            this.count = count;
        }

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return dyeColor(DyeColor.ORANGE);
        }

        @Override
        public TreeList<Iota> execute(CastingEnvironment env, Mishap.Context errorCtx, TreeList<Iota> stack) {
            return stack;
        }

        @Override
        protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.try_catch_blocks", this.count);
        }
    }

    /**
     * 「get_exception」不在任何 catch 块里。
     */
    public static final class NoActiveException extends Mishap {

        @Override
        public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
            return dyeColor(DyeColor.ORANGE);
        }

        @Override
        public TreeList<Iota> execute(CastingEnvironment env, Mishap.Context errorCtx, TreeList<Iota> stack) {
            return stack;
        }

        @Override
        protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
            return Component.translatable("hexextra.mishap.no_active_exception");
        }
    }
}
