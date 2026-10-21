package com.llk.hexextra.pattern.switch_case;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.FrameEvaluate;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.CastingImages;
import com.llk.hexextra.core.HexextraIotaTypes;
import com.llk.hexextra.core.HexextraMishaps;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 「switch」多路等值分派 —— 把 Java 里 switch 的定位搬进咒法学。
 *
 * <h2>执行方式:直接消费栈顶那个值</h2>
 * 本 iota <b>没有选择器代码块</b>。被执行时它直接取栈顶那个值(并把它消费掉)去派发:
 * <pre>
 *   [..., 值]  执行 switch →  查表 →  只执行命中的那个 case 的代码块
 * </pre>
 * 这样派发是<b>一步完成</b>的:不需要额外的元求值,也不需要自定义续延帧。
 *
 * <h2>它和 if-else 链的区别(也就是它存在的理由)</h2>
 * if-else 链的<b>每个条件都是一次独立的代码块求值</b>:n 条分支就要跑 n 次元求值
 * (每次都要压求值帧、跨若干 VM 步,而每一步都有施法结果分配与栈大小检查)。
 * switch 则是 <b>0 次代码块求值 + 1 次哈希查表</b> —— 分支越多,差距越大,
 * 这是数量级而非常数因子。
 *
 * <h2>语义(照 Java 的定位,不照 C 的)</h2>
 * <ul>
 *   <li><b>不贯穿</b>:命中哪个 case 就只执行那一个,不会往下滑。
 *       所以既不需要 break,也不可能因为忘写 break 而静默多执行分支
 *       —— 这是 Java 14+ 箭头形式的语义。</li>
 *   <li><b>case 只能是常量</b>:数值、布尔、null、图案。见 {@link CaseIndex} 的长注释
 *       —— 那既是 Java 传统 switch「只吃常量」的同构,也是 O(1) 派发的前提。</li>
 *   <li><b>重复的 case 报错</b>(照 Java 把重复 case 当编译错误)。</li>
 *   <li>没有 default 且没有 case 命中时,整条 switch 什么都不做(与 if 链一致)。</li>
 * </ul>
 *
 * <h2>结构</h2>
 * <ul>
 *   <li>{@link #cases} —— case 表,每个 case 是「常量 → 代码块」。</li>
 *   <li>{@link #defaultBody} —— 全都不命中时执行的代码块,可为 null。</li>
 * </ul>
 */
public class SwitchIota extends Iota {

    /**
     * 一条分支:匹配用常量 + 命中时执行的代码块。
     *
     * <h2>为什么是内部类而不是独立的 iota 类型</h2>
     * 它只是 switch 表的一部分,永远不会单独出现在栈上 —— 所以不该是一个 iota。
     * 早先它确实是独立类型({@code hexextra:case}),但那样"造一条分支"与"造一张表"
     * 会产出两种类型,玩家分不清手里的是半成品还是成品;现在统一成 switch 本身:
     * 「斯维奇之策略」造出的是<b>只有一条分支的 switch</b>,可以直接执行。
     *
     * <h2>为什么缓存键</h2>
     * 外部类型(其它模组的 iota)的键要序列化整棵 NBT 才能算出来,几十上百微秒。
     * 表本身不可变,但用「加法之馏化」构造新表时分支对象是<b>从旧表拷贝过来的同一个对象</b>
     * —— 缓存下来就能跨表复用,不必每建一次表就重新序列化一遍。
     */
    public static final class Case {

        private final Iota value;
        private final TreeList<Iota> body;

        /**
         * 惰性缓存的查表键。
         *
         * <p>{@code volatile} 只为避免看到半初始化引用;并发重复计算的结果等价。
         */
        private volatile @Nullable Object cachedKey;

        public Case(Iota value, TreeList<Iota> body) {
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

        /** 这条分支要求的匹配类型;null 键时为 null(不决定类型)。 */
        public @Nullable SwitchIota.MatchType matchType() {
            return SwitchIota.MatchType.of(this.value);
        }

        /**
         * 在给定类型的表里,这个分支的查表键。
         *
         * <p>结果缓存:同一个分支对象被重复建表时不再重复序列化。
         */
        public Object keyFor(MatchType type) {
            Object k = this.cachedKey;
            if (k == null) {
                k = type.keyOf(this.value);
                this.cachedKey = k;
            }
            return k;
        }

        /** 悬停显示:{@code case 值 -> {代码块}}。 */
        public net.minecraft.network.chat.Component display() {
            net.minecraft.network.chat.MutableComponent out = net.minecraft.network.chat.Component.empty();
            out.append(net.minecraft.network.chat.Component.literal("case ").withStyle(net.minecraft.ChatFormatting.GOLD));
            out.append(this.value.display());
            out.append(net.minecraft.network.chat.Component.literal(" -> {").withStyle(net.minecraft.ChatFormatting.GOLD));
            out.append(new ListIota(this.body).display());
            out.append(net.minecraft.network.chat.Component.literal("}").withStyle(net.minecraft.ChatFormatting.GOLD));
            return out;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Case other
                    && this.value.equals(other.value)
                    && this.body.equals(other.body);
        }

        @Override
        public int hashCode() {
            return 31 * this.value.hashCode() + this.body.hashCode();
        }
    }


    /**
     * 一条 switch 匹配什么类型 —— 在<b>建造期</b>用一个样例键的类型声明下来。
     *
     * <h2>为什么是密封接口而不是枚举</h2>
     * 枚举只有固定的几种,而 iota 类型是<b>开放</b>的:任何模组都能注册新的。
     * 若写死成枚举,「用 MoreIotas 的字符串当键」就永远无法支持;
     * 若改成「一种万能类型」,又破坏了"一张表只匹配一种类型"这条约束。
     *
     * <p>所以类型有两种:
     * <ul>
     *   <li>{@link Core} —— 咒法学自带的三种,键用精确的原生形式(位模式 / 布尔 / 图案签名);</li>
     *   <li>{@link Foreign} —— 其它任何 iota 类型,按它的<b>注册 id</b> 区分,
     *       键用序列化后的 {@code Tag}(自带精确 equals/hashCode)。</li>
     * </ul>
     *
     * <p>{@code Foreign} 的 id 是<b>运行时从 iota 自己身上读出来的</b>
     * ({@code HexIotaTypes.REGISTRY.getKey(iota.getType())})—— 我们不写死任何外部 id,
     * 也就不需要编译期依赖对方。
     */
    public sealed interface MatchType {

        /** 核心三型。 */
        enum Kind { NUMBER, BOOLEAN, PATTERN }

        /** 咒法学自带的三种可精确分派的类型。 */
        record Core(Kind kind) implements MatchType {

            @Override
            public String translationKey() {
                return "hexextra.switch.type." + this.kind.name().toLowerCase(java.util.Locale.ROOT);
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
                    case NUMBER -> Double.doubleToLongBits(
                            ((DoubleIota) iota).getDouble() == 0.0 ? 0.0 : ((DoubleIota) iota).getDouble());
                    case BOOLEAN -> ((BooleanIota) iota).getBool();
                    case PATTERN -> ((PatternIota) iota).getPattern().getSignature();
                };
            }
        }

        /** 其它模组(或其它类型)注册的 iota,按注册 id 区分。 */
        record Foreign(net.minecraft.resources.ResourceLocation iotaTypeId) implements MatchType {

            /** 外部类型没有本地化名,直接显示 id。 */
            @Override
            public String translationKey() {
                return null;
            }

            @Override
            public boolean accepts(Iota iota) {
                return this.iotaTypeId.equals(
                        at.petrak.hexcasting.common.lib.hex.HexIotaTypes.REGISTRY.getKey(iota.getType()));
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
                // 0.12.0 没有 IotaType.serialize(Tag 那套是 0.11.4 的),
                // 走类型化编解码器:任何 iota 都能编码成 Tag。
                try {
                    return at.petrak.hexcasting.api.casting.iota.IotaType.TYPED_CODEC
                            .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, iota)
                            .getOrThrow();
                } catch (RuntimeException e) {
                    // 极少数 iota 可能编码失败(值过大 / 类型实现有 bug)。
                    // 那不该把服务端崩掉,而应当让玩家看到一条普通的事故提示。
                    throw new HexextraMishaps.CaseNotConstant(iota);
                }
            }
        }

        // 常用的核心类型常量(省去到处 new Core(...))
        Core NUMBER = new Core(Kind.NUMBER);
        Core BOOLEAN = new Core(Kind.BOOLEAN);
        Core PATTERN = new Core(Kind.PATTERN);

        /** 本地化名所用的翻译键;外部类型返回 null(显示 id)。 */
        @Nullable String translationKey();

        /**
         * 报错文案与 tooltip 里用的类型名。
         *
         * <p>核心三型给翻译键(由语言文件本地化);外部类型直接给注册 id
         * ({@code moreiotas:string}),因为它本来也没有本地化名。
         */
        default String displayKey() {
            String key = this.translationKey();
            return key != null ? key : ((Foreign) this).iotaTypeId().toString();
        }

        /**
         * 存档里用的类型标识。
         *
         * <p>核心三型就是 {@code NUMBER} / {@code BOOLEAN} / {@code PATTERN};
         * 外部类型是它的注册 id,例如 {@code moreiotas:string}。
         */
        default String id() {
            return this instanceof Core core ? core.kind().name() : ((Foreign) this).iotaTypeId().toString();
        }

        /**
         * 从存档里的标识解析类型。
         *
         * @return null 表示认不出来(存档损坏),由调用方决定怎么报错
         */
        static @Nullable MatchType fromId(String s) {
            for (Kind kind : Kind.values()) {
                if (kind.name().equalsIgnoreCase(s)) {
                    return new Core(kind);
                }
            }
            // 不是核心三型 → 当作外部类型的注册 id
            var rl = net.minecraft.resources.ResourceLocation.tryParse(s);
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
        static @Nullable MatchType of(Iota sample) {
            // null 不是一种"类型":它作为键时可以进任何类型的表。
            if (sample instanceof NullIota) {
                return null;
            }
            if (sample instanceof DoubleIota) return NUMBER;
            if (sample instanceof BooleanIota) return BOOLEAN;
            if (sample instanceof PatternIota) return PATTERN;
            // 其余一律按"外部类型"处理,类型 id 运行时从它自己身上读
            var id = at.petrak.hexcasting.common.lib.hex.HexIotaTypes.REGISTRY.getKey(sample.getType());
            if (id == null) {
                // 理论上走不到(能出现在栈上的 iota 一定已注册);
                // 真遇到也报事故,不要抛 IllegalStateException 把服务端崩掉。
                throw new HexextraMishaps.CaseNotConstant(sample);
            }
            return new Foreign(id);
        }
    }

    @Nullable
    private final MatchType matchType;
    private final List<Case> cases;
    @Nullable
    private final TreeList<Iota> defaultBody;

    /**
     * 派发索引的惰性缓存。
     *
     * <p>为什么要缓存:建表是 O(case 数) 的,而 switch 的性能卖点正是 O(1) 派发 ——
     * 若每次执行都重建一次表,就把优势又还回去了。
     *
     * <p>为什么可以缓存:索引只由 {@link #cases} 决定,而本类型是不可变的。
     *
     * <p>为什么不必跟着序列化:存档与网络往返时,编解码器会<b>新建</b>一个对象
     * (构造器不会填这个字段),于是新对象的缓存为 null、首次使用时按需重建一次。
     * 这里用 {@code volatile} 只为避免多线程下看到半初始化的引用;即便真的并发重建,
     * 结果也等价,只是白做一次功。
     */
    private volatile transient @Nullable CaseIndex cachedIndex;

    /**
     * @param matchType   本 switch 匹配的类型
     * @param cases       case 表,可为空;每个 case 的类型必须与 {@code matchType} 一致
     * @param defaultBody default 代码块;null 表示没有
     */
    public SwitchIota(MatchType matchType, List<Case> cases, @Nullable TreeList<Iota> defaultBody) {
        super(HexextraIotaTypes.SWITCH::get);
        this.matchType = matchType;
        this.cases = List.copyOf(cases);
        this.defaultBody = defaultBody;
    }

    /** 本 switch 匹配的类型。 */
    public @Nullable MatchType getMatchType() {
        return this.matchType;
    }

    /**
     * 由若干条 case 建一张表 —— 类型从<b>第一条 case</b> 推出。
     *
     * <p>之所以能推:case 的匹配类型完全由它那个常量的类型决定(见 {@link SwitchIota.Case#matchType()})。
     * 后续 case 若类型不一致,会在建表时被 {@link CaseIndex#build} 拦下并报错。
     *
     * @throws IllegalArgumentException case 列表为空 —— 没有第一条就无法确定类型。
     *         正常玩法走不到这里:表要么由 case 拼出来,要么由 remove_from 从已有表删空
     *         (后者保留原类型,不走这个工厂)。
     */
    public static SwitchIota fromCases(List<Case> cases, @Nullable TreeList<Iota> defaultBody) {
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("hexextra: 空 case 列表无法推出 switch 的匹配类型");
        }
        // null 键不决定类型:跳过它往后找;全是 null 键时这张表就是「未定类型」的,
        // 之后可以接收任何类型的键。非 null 却不是常量的值仍然当场报错。
        SwitchIota.MatchType type = null;
        for (SwitchIota.Case c : cases) {
            Iota v = c.getValue();
            if (v instanceof NullIota) {
                continue;
            }
            SwitchIota.MatchType t = SwitchIota.MatchType.of(v);
            if (t == null) {
                throw new com.llk.hexextra.core.HexextraMishaps.CaseNotConstant(v);
            }
            type = t;
            break;
        }
        return new SwitchIota(type, cases, defaultBody);
    }

    /** case 表(不可变)。 */
    public List<Case> getCases() {
        return this.cases;
    }

    /** default 代码块,可为 null。 */
    public @Nullable TreeList<Iota> getDefaultBody() {
        return this.defaultBody;
    }

    /** 供编解码器使用的 default 包装形式。 */
    public Optional<TreeList<Iota>> getDefaultBodyOptional() {
        return Optional.ofNullable(this.defaultBody);
    }

    /**
     * 取得派发索引,必要时按需构建一次。
     *
     * <p>建表时顺带做两项校验(见 {@link CaseIndex#build}):case 必须是常量、
     * 常量不能重复 —— 两者都会抛报错。
     */
    public CaseIndex index() {
        CaseIndex index = this.cachedIndex;
        if (index == null) {
            index = CaseIndex.build(this.matchType, this.cases);
            this.cachedIndex = index;
        }
        return index;
    }

    /** 真假:只要"有东西可做"就算真 —— 存在 case 或存在 default。 */
    @Override
    public boolean isTruthy(@Nullable CastingEnvironment env) {
        return !this.cases.isEmpty() || this.defaultBody != null;
    }

    /**
     * 声明本 iota 可以被执行。
     *
     * <p>这个方法的唯一调用点是 {@code OperatorUtils.getEvaluatable} —— 也就是
     * 「赫尔墨斯之策略」等图案判断"能不能把栈上这个东西当代码跑"时用的地方。
     */
    @Override
    public boolean executable() {
        return true;
    }

    /**
     * 执行:消费栈顶那个值,查表,只把命中的那个代码块挂上去求值。
     *
     * <h2>⚠ 报错必须"折进" CastResult,不能抛</h2>
     * {@code CastingVM.executeInner} 里只有一个 catch,它把<b>一切</b>异常(包括 Mishap)
     * 都包成 {@code MishapInternalException} 并打印堆栈。真正的 Mishap 处理只存在于
     * {@code PatternIota.lookupAndOperate} —— 那条路径只覆盖<b>图案</b>。
     *
     * <p>所以约定是:{@code Action.operate} 可以抛 Mishap,但
     * <b>{@code Iota.execute} 与 {@code ContinuationFrame.evaluate} 不许抛</b>,
     * 必须自己折成 {@code DoMishap} 副作用 + ERRORED。{@link #mishap} 就是干这个的。
     */
    @Override
    public CastResult execute(CastingVM vm, ServerLevel world, SpellContinuation continuation) {
        CastingImage image = vm.getImage();
        TreeList<Iota> stack = image.getStack();

        // ---- 1) 栈上什么都没有,拿不到可判的值 ----
        if (stack.isEmpty()) {
            // 复用官方的"参数不足"报错,不必自造
            return this.mishap(new MishapNotEnoughArgs(1, 0), vm, continuation, stack);
        }

        // ---- 2) 消费掉栈顶那个值 ----
        Iota scrutinee = stack.last();
        CastingImage popped = CastingImages.withStack(image.withUsedOp(), stack.dropRight(1));

        // ---- 3) 类型必须与这条 switch 声明的相符 ----
        // 这里刻意报错而不是"当作不命中、走 default":类型不符的值注定匹配不上任何 case,
        // 默默滑到 default 只会把玩家的错误盖住。
        if (this.matchType != null && !this.matchType.accepts(scrutinee)) {
            return this.mishap(new HexextraMishaps.SwitchValueTypeMismatch(
                            this.matchType.displayKey(), scrutinee),
                    vm, continuation, popped.getStack());
        }

        // ---- 4) O(1) 查表决定走哪一支 ----
        // 注意:第一次执行会触发建表,而建表本身可能报错(重复的 case)。
        // 这个路径是**走得到的** —— 用「加法之馏化」把两张含相同常量的表合并起来就会这样。
        // 而 Iota.execute 不许抛 Mishap,所以这里必须接住并折进 CastResult。
        CaseIndex index;
        try {
            index = this.index();
        } catch (Mishap buildFailure) {
            return this.mishap(buildFailure, vm, continuation, popped.getStack());
        }

        Integer hit = index.lookup(scrutinee);
        TreeList<Iota> chosen;
        if (hit != null) {
            chosen = this.cases.get(hit).getBody();
        } else {
            // 没有 case 命中 → 有 default 就走 default,没有就什么都不做
            chosen = this.defaultBody;
        }

        SpellContinuation next = continuation;
        if (chosen != null) {
            // 只压入选中的那一个代码块的求值帧;跑完之后本 iota 无需再做任何事,
            // 控制权自然交回下层续延。
            next = next.pushFrame(new FrameEvaluate(chosen, true));
        }

        return new CastResult(
                this,
                next,
                popped,
                List.<OperatorSideEffect>of(),
                ResolvedPatternType.EVALUATED,
                chosen != null ? HexEvalSounds.HERMES.get() : HexEvalSounds.NOTHING.get());
    }

    /**
     * 把报错折进 {@link CastResult}。
     *
     * <p>写法照抄 {@code PatternIota} 与 if_else 的 {@code FrameIfElse}:
     * {@code DoMishap} 副作用 + {@code mishap.resolutionType(env)} + MISHAP 音效,
     * 并在需要时清掉未闭合的括号状态。
     */
    private CastResult mishap(Mishap mishap, CastingVM harness, SpellContinuation continuation,
                              TreeList<Iota> stack) {
        boolean wipeParens = continuation instanceof SpellContinuation.NotDone notDone
                && notDone.getFrame() instanceof FrameEvaluate frameEval
                && frameEval.isMetacasting();

        return new CastResult(
                new ListIota(stack),
                continuation,
                wipeParens ? harness.getImage().withResetEscape() : null,
                List.of(new OperatorSideEffect.DoMishap(mishap, new Mishap.Context(null, null))),
                mishap.resolutionType(harness.getEnv()),
                HexEvalSounds.MISHAP.get());
    }

    /** 容差相等:类型相同且 case 表与 default 都逐层相等。 */
    @Override
    protected boolean toleratesOther(Iota that) {
        return typesMatch(this, that)
                && that instanceof SwitchIota other
                && Objects.equals(this.matchType, other.matchType)
                && this.cases.equals(other.cases)
                && Objects.equals(this.defaultBody, other.defaultBody);
    }

    /** 与 {@link #toleratesOther} 保持一致的散列(与 ListIota / IfElseIota 同一口径)。 */
    @Override
    public int hashCode() {
        return Objects.hash(this.matchType, this.cases, this.defaultBody);
    }

    /**
     * 悬停/聊天栏里的多行显示,格式仿 Java 的箭头式 switch:
     * <pre>
     *   switch{
     *   case 值 -&gt; {代码块}
     *   default -&gt; {代码块}
     *   }
     * </pre>
     *
     * <p>换行用 {@code "\n"} 嵌入 Component —— 原版 tooltip 的
     * {@code Font.split → StringSplitter.splitLines} 会识别它。
     * 代码块照搬原版列表 iota 的渲染(包成 {@link ListIota} 调它的 {@code display()})。
     */
    @Override
    public Component display() {
        MutableComponent out = Component.empty();
        out.append(Component.literal("switch<").withStyle(ChatFormatting.GOLD));
        // 把声明的类型显示出来 —— 否则玩家光看这条链不知道它匹配什么
        // 外部类型没有翻译键,displayKey() 会退回成它的注册 id(如 moreiotas:string)
        out.append(Component.literal("")
                .append(Component.translatable(this.matchType != null
                        ? this.matchType.displayKey()
                        : "hexextra.switch.type.unknown"))
                .withStyle(ChatFormatting.AQUA));
        out.append(Component.literal(">{").withStyle(ChatFormatting.GOLD));

        for (SwitchIota.Case c : this.cases) {
            out.append(Component.literal("\n"));
            out.append(Component.literal("case ").withStyle(ChatFormatting.GOLD));
            // 匹配值用它自己的 display,于是数值/布尔/图案各自显示成本来的样子
            out.append(c.getValue().display());
            out.append(Component.literal(" -> {").withStyle(ChatFormatting.GOLD));
            out.append(renderBlock(c.getBody()));
            out.append(Component.literal("}").withStyle(ChatFormatting.GOLD));
        }

        if (this.defaultBody != null) {
            out.append(Component.literal("\n"));
            out.append(Component.literal("default -> {").withStyle(ChatFormatting.GOLD));
            out.append(renderBlock(this.defaultBody));
            out.append(Component.literal("}").withStyle(ChatFormatting.GOLD));
        }

        out.append(Component.literal("\n}").withStyle(ChatFormatting.GOLD));
        return out;
    }

    /** 照搬原版列表 iota 的渲染方式。 */
    private static Component renderBlock(TreeList<Iota> block) {
        return new ListIota(block).display();
    }

    /**
     * 内存占用:与 {@code ListIota} / {@code IfElseIota} 同一口径。
     * 咒法学用它判断栈是否还允许序列化(上限 {@code MAX_SERIALIZATION_TOTAL} = 1024)。
     */
    @Override
    public int size() {
        int total = 1;
        for (SwitchIota.Case c : this.cases) {
            total += c.getValue().size();
            total += sizeOf(c.getBody());
        }
        if (this.defaultBody != null) {
            total += sizeOf(this.defaultBody);
        }
        return total;
    }

    private static int sizeOf(TreeList<Iota> block) {
        int total = 1;
        for (Iota iota : block) {
            total += iota.size();
        }
        return total;
    }

    /** 嵌套深度,同样用于序列化保护(上限 {@code MAX_SERIALIZATION_DEPTH} = 256)。 */
    @Override
    public int depth() {
        int maxChild = 0;
        for (SwitchIota.Case c : this.cases) {
            maxChild = Math.max(maxChild, c.getValue().depth());
            maxChild = Math.max(maxChild, depthOf(c.getBody()));
        }
        if (this.defaultBody != null) {
            maxChild = Math.max(maxChild, depthOf(this.defaultBody));
        }
        return maxChild + 1;
    }

    private static int depthOf(TreeList<Iota> block) {
        int maxChild = 0;
        for (Iota iota : block) {
            maxChild = Math.max(maxChild, iota.depth());
        }
        return maxChild + 1;
    }

    /**
     * 直接子 iota —— 摊平列出各 case 的匹配值与代码块、以及 default。
     *
     * <p>用途只有一个:咒法学的 {@code MishapOthersName} 会递归扫描 iota 树查"别人的真名",
     * 我们要把内容暴露给它,否则藏在 switch 里的真名会漏检。
     */
    @Override
    public @Nullable Iterable<Iota> subIotas() {
        List<Iota> out = new ArrayList<>();
        for (SwitchIota.Case c : this.cases) {
            out.add(c.getValue());
            c.getBody().forEach(out::add);
        }
        if (this.defaultBody != null) {
            this.defaultBody.forEach(out::add);
        }
        return out;
    }

    /** 本 iota 的类型对象:必须实现 codec / streamCodec / color 三项。 */
    public static final IotaType<SwitchIota> TYPE = new IotaType<>() {

        @Override
        public MapCodec<SwitchIota> codec() {
            return SwitchCodecs.CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SwitchIota> streamCodec() {
            return SwitchCodecs.STREAM_CODEC;
        }

        /** 物品光效/图标颜色:取一个偏蓝的青,与 if_else 的青色区分开。 */
        @Override
        public int color() {
            return 0xff_3366cc;
        }
    };
}
