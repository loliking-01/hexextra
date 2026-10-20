package com.llk.hexextra.pattern.switch_case;

import at.petrak.hexcasting.api.casting.SpellList;
import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.FrameEvaluate;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.api.utils.HexUtils;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.CastingImages;
import com.llk.hexextra.core.HexextraMishaps;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * switch 的一张表 —— 多路等值分派的<b>成品</b>。
 *
 * <h2>语义(照 Java 的定位,不照 C 的)</h2>
 * <ul>
 *   <li><b>不贯穿</b>(箭头式):命中哪条 case 就只跑那一条,因此不需要 break;</li>
 *   <li>执行时<b>消费栈顶那个值</b>去查表 —— 没有"选择器代码块"这层;</li>
 *   <li>没有 case 命中时走 default;连 default 都没有就什么都不做;</li>
 *   <li>匹配是<b>精确</b>的(与 Java 的 {@code ==} 一致),不是咒法学的容差相等 ——
 *       理由见 {@link CaseIndex}。</li>
 * </ul>
 *
 * <h2>为什么不需要自定义续延帧</h2>
 * 派发是"弹栈顶 → 查表 → 压入选中的那一个代码块"一步完成的,
 * 没有"先求值选择器、再求值分支"的两阶段。所以本类是纯 {@link Iota} 实现,
 * 不需要向 {@code HexRegistries.CONTINUATION_TYPE} 注册任何帧类型。
 *
 * <h2>与 1.21.1 版的差异(0.11.4 的 API 特点)</h2>
 * <ul>
 *   <li>构造器把 {@code (type, payload)} 交给基类,payload 由基类持有;</li>
 *   <li>序列化是手写 NBT,没有 Codec;</li>
 *   <li>{@code CastingImage} 的字段是 6 个,{@code copy(...)} 也是 6 个位置参数;</li>
 *   <li>{@code CastResult} 的第一个参数是"被施放的 iota"而不是 image。</li>
 * </ul>
 */
public class SwitchIota extends Iota {

    /**
     * 一条分支:匹配用常量 + 命中时执行的代码块。
     *
     * <h2>为什么是内部 record 而不是独立的 iota 类型</h2>
     * 它只是表的一部分,永远不会单独出现在栈上 —— 所以不该是一个 iota。
     * 早先它是独立类型({@code hexextra:case}),但那样"造一条分支"与"造一张表"
     * 会产出两种类型,玩家分不清半成品与成品;现在统一成 switch 本身:
     * 「斯维奇之策略」造出的就是<b>只有一条分支的 switch</b>,可以直接执行。
     */
    public record Case(Iota value, TreeList<Iota> body) {

        public Iota getValue() {
            return this.value;
        }

        public TreeList<Iota> getBody() {
            return this.body;
        }

        /** 这条分支要求的匹配类型;null 表示 null 键(不决定类型)。 */
        public @Nullable SwitchMatchType matchType() {
            return SwitchMatchType.of(this.value);
        }

        /** 序列化成一个 {value, body} 的 compound。 */
        public @NotNull Tag serialize() {
            var out = new CompoundTag();
            out.put("value", IotaType.serialize(this.value));
            var bodyTag = new ListTag();
            for (Iota iota : this.body) {
                bodyTag.add(IotaType.serialize(iota));
            }
            out.put("body", bodyTag);
            return out;
        }

        /** 从 {value, body} 反序列化。 */
        public static @Nullable Case deserialize(Tag tag, ServerLevel world) {
            var ctag = HexUtils.downcast(tag, CompoundTag.TYPE);
            var value = IotaType.deserialize(HexUtils.downcast(ctag.get("value"), CompoundTag.TYPE), world);
            var bodyTag = HexUtils.downcast(ctag.get("body"), ListTag.TYPE);
            var body = new ArrayList<Iota>(bodyTag.size());
            for (Tag sub : bodyTag) {
                var iota = IotaType.deserialize(HexUtils.downcast(sub, CompoundTag.TYPE), world);
                if (iota == null) {
                    return null;
                }
                body.add(iota);
            }
            return new Case(value, TreeList.from(body));
        }

        /** 内存占用(与 ListIota 同口径)。 */
        public int size() {
            int total = 2;
            for (Iota iota : this.body) {
                total += iota.size();
            }
            return total;
        }

        /** 嵌套深度。 */
        public int depth() {
            int maxChild = this.value.depth();
            for (Iota iota : this.body) {
                maxChild = Math.max(maxChild, iota.depth());
            }
            return maxChild + 1;
        }

        /** 悬停显示:{@code case 值 -> {代码块}}。 */
        public Component display() {
            MutableComponent out = Component.empty();
            out.append(Component.literal("case ").withStyle(ChatFormatting.GOLD));
            out.append(this.value.display());
            out.append(Component.literal(" -> {").withStyle(ChatFormatting.GOLD));
            out.append(new ListIota(this.body).display());
            out.append(Component.literal("}").withStyle(ChatFormatting.GOLD));
            return out;
        }

        /** 从 tag 渲染(0.11.4 的显示入口是 Tag)。 */
        public static Component displayFromTag(Tag tag) {
            var ctag = HexUtils.downcast(tag, CompoundTag.TYPE);
            MutableComponent out = Component.empty();
            out.append(Component.literal("case ").withStyle(ChatFormatting.GOLD));
            out.append(IotaType.getDisplay(HexUtils.downcast(ctag.get("value"), CompoundTag.TYPE)));
            out.append(Component.literal(" -> {").withStyle(ChatFormatting.GOLD));
            MutableComponent bodyOut = Component.empty();
            var bodyTag = HexUtils.downcast(ctag.get("body"), ListTag.TYPE);
            for (int i = 0; i < bodyTag.size(); i++) {
                bodyOut.append(IotaType.getDisplay(HexUtils.downcast(bodyTag.get(i), CompoundTag.TYPE)));
                if (i < bodyTag.size() - 1) {
                    bodyOut.append(", ");
                }
            }
            out.append(Component.translatable("hexcasting.tooltip.list_contents", bodyOut)
                    .withStyle(ChatFormatting.DARK_PURPLE));
            return out;
        }
    }


    /**
     * payload:匹配类型 + case 表 + 可空的 default。
     *
     * <p>类型必须显式存 —— 空表(比如被「切除器之馏化」删光了)没有第一条 case 可以推类型。
     */
    public record SwitchData(SwitchMatchType matchType,
                             List<SwitchIota.Case> cases,
                             @Nullable TreeList<Iota> defaultBody) {
    }

    /**
     * 惰性构建的派发表。
     *
     * <p>{@code transient} 不影响正确性(0.11.4 的存档走 {@link #serialize()});
     * 加 {@code volatile} 是因为它可能在多线程被读到(不同玩家的施法)。
     */
    private volatile transient CaseIndex cachedIndex;

    public SwitchIota(SwitchMatchType matchType,
                      List<SwitchIota.Case> cases,
                      @Nullable TreeList<Iota> defaultBody) {
        super(TYPE, new SwitchData(matchType, cases, defaultBody));
    }

    private SwitchData data() {
        return (SwitchData) this.payload;
    }

    public @Nullable SwitchMatchType getMatchType() {
        return this.data().matchType();
    }

    public List<SwitchIota.Case> getCases() {
        return this.data().cases();
    }

    public @Nullable TreeList<Iota> getDefaultBody() {
        return this.data().defaultBody();
    }

    /**
     * 由若干条 case 建表 —— 类型从<b>第一条 case</b> 推出。
     *
     * @throws IllegalArgumentException case 列表为空(没有第一条就无法确定类型)。
     *         正常玩法走不到这里:表要么由 case 拼出来,要么由 remove_from 从已有表删空
     *         (后者保留原类型,不走这个工厂)。
     */
    public static SwitchIota fromCases(List<SwitchIota.Case> cases, @Nullable TreeList<Iota> defaultBody) {
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("hexextra: 空 case 列表无法推出 switch 的匹配类型");
        }
        // null 键不决定类型:跳过它往后找;全是 null 键时这张表就是「未定类型」的,
        // 之后可以接收任何类型的键。非 null 却不是常量的值仍然当场报错。
        SwitchMatchType type = null;
        for (SwitchIota.Case c : cases) {
            Iota v = c.getValue();
            if (v instanceof at.petrak.hexcasting.api.casting.iota.NullIota) {
                continue;
            }
            SwitchMatchType t = SwitchMatchType.of(v);
            if (t == null) {
                throw new HexextraMishaps.CaseNotConstant(v);
            }
            type = t;
            break;
        }
        return new SwitchIota(type, cases, defaultBody);
    }

    /**
     * 建(或取缓存)派发表。
     *
     * @throws Mishap case 类型不符或常量重复时
     */
    public CaseIndex index() {
        CaseIndex idx = this.cachedIndex;
        if (idx == null) {
            idx = CaseIndex.build(this.getMatchType(), this.getCases());
            this.cachedIndex = idx;
        }
        return idx;
    }

    /** 真假:一张表总有东西可做,恒真。 */
    @Override
    public boolean isTruthy() {
        return true;
    }

    @Override
    protected boolean toleratesOther(Iota that) {
        return typesMatch(this, that)
                && that instanceof SwitchIota other
                && this.getMatchType() == other.getMatchType()
                && this.getCases().size() == other.getCases().size()
                && this.getCases().equals(other.getCases())
                && java.util.Objects.equals(this.getDefaultBody(), other.getDefaultBody());
    }

    /**
     * 手写 NBT 序列化。
     *
     * <p>格式:{@code { match_type: String, cases: ListTag, default?: CompoundTag }}。
     * 认不出来的 {@code match_type} 会让反序列化返回 null → 本体把它变成 NullIota,
     * 而不是悄悄回退到某个默认类型。
     */
    @Override
    public @NotNull Tag serialize() {
        var out = new CompoundTag();
        if (this.getMatchType() != null) {
            out.putString("match_type", this.getMatchType().name());
        }

        var casesTag = new ListTag();
        for (SwitchIota.Case entry : this.getCases()) {
            casesTag.add(entry.serialize());
        }
        out.put("cases", casesTag);

        TreeList<Iota> def = this.getDefaultBody();
        if (def != null) {
            var defTag = new ListTag();
            for (Iota iota : def) {
                defTag.add(IotaType.serialize(iota));
            }
            out.put("default", defTag);
        }
        return out;
    }

    /** 直接子 iota:每条 case + default 里的元素。 */
    @Override
    public @Nullable Iterable<Iota> subIotas() {
        List<Iota> out = new ArrayList<>();
        for (SwitchIota.Case entry : this.getCases()) {
            out.add(entry.getValue());
            entry.getBody().forEach(out::add);
        }
        TreeList<Iota> def = this.getDefaultBody();
        if (def != null) {
            def.forEach(out::add);
        }
        return out;
    }

    /** 内存占用。 */
    @Override
    public int size() {
        int total = 2;   // 自身 + 类型
        for (SwitchIota.Case entry : this.getCases()) {
            total += entry.size();
        }
        TreeList<Iota> def = this.getDefaultBody();
        if (def != null) {
            for (Iota iota : def) {
                total += iota.size();
            }
        }
        return total;
    }

    /** 嵌套深度。 */
    @Override
    public int depth() {
        int maxChild = 1;
        for (SwitchIota.Case entry : this.getCases()) {
            maxChild = Math.max(maxChild, entry.depth());
        }
        TreeList<Iota> def = this.getDefaultBody();
        if (def != null) {
            for (Iota iota : def) {
                maxChild = Math.max(maxChild, iota.depth());
            }
        }
        return maxChild + 1;
    }

    /**
     * 表是可执行的 —— 这是"成品"与"原料"的分界。
     *
     * <p>本方法的调用点之一是「赫尔墨斯之策略」判断"栈上这个东西能不能当代码跑"。
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
     * 都包成内部错误并打印堆栈。真正的 Mishap 处理只存在于 {@code PatternIota.lookupAndOperate}
     * —— 那条路径只覆盖<b>图案</b>。所以约定是:{@code Action.operate} 可以抛 Mishap,
     * 但 <b>{@code Iota.execute} 与 {@code ContinuationFrame.evaluate} 不许抛</b>。
     */
    @Override
    public @NotNull CastResult execute(CastingVM vm, ServerLevel world, SpellContinuation continuation) {
        CastingImage image = vm.getImage();
        List<Iota> stack = image.getStack();

        // ---- 1) 栈上什么都没有,拿不到可判的值 ----
        if (stack.isEmpty()) {
            return this.mishap(new MishapNotEnoughArgs(1, 0), vm, continuation, stack);
        }

        // ---- 2) 消费掉栈顶那个值 ----
        Iota scrutinee = stack.get(stack.size() - 1);
        List<Iota> rest = new ArrayList<>(stack.subList(0, stack.size() - 1));
        CastingImage popped = CastingImages.withStack(image.withUsedOp(), rest);

        // ---- 3) 类型必须与这张表声明的相符 ----
        // 刻意报错而不是"当作不命中、走 default":类型不符的值注定匹配不上任何 case,
        // 默默滑到 default 只会把玩家的错误盖住。
        if (this.getMatchType() != null && !this.getMatchType().accepts(scrutinee)) {
            return this.mishap(new HexextraMishaps.SwitchValueTypeMismatch(
                    this.getMatchType().translationKey(), scrutinee), vm, continuation, rest);
        }

        // ---- 4) O(1) 查表 ----
        // 第一次执行会触发建表,而建表本身可能报错(重复的 case)。这条路走得到 ——
        // 用「加法之馏化」把两张含相同常量的表合并起来就是这样。而 execute 不许抛,所以接住。
        CaseIndex idx;
        try {
            idx = this.index();
        } catch (Mishap buildFailure) {
            return this.mishap(buildFailure, vm, continuation, rest);
        }

        Integer hit = idx.lookup(scrutinee);
        TreeList<Iota> chosen = (hit != null)
                ? this.getCases().get(hit).getBody()
                : this.getDefaultBody();

        // ---- 5) 只压入选中的那一个代码块的求值帧 ----
        SpellContinuation next = continuation;
        if (chosen != null) {
            next = next.pushFrame(new FrameEvaluate(new SpellList.LList(chosen), true));
        }

        return new CastResult(
                this,
                next,
                popped,
                List.<OperatorSideEffect>of(),
                ResolvedPatternType.EVALUATED,
                chosen != null ? HexEvalSounds.HERMES : HexEvalSounds.NOTHING);
    }

    /**
     * 把报错折进 {@link CastResult}(不能抛)。
     *
     * <p>写法照抄 {@code PatternIota}:{@code DoMishap} 副作用 + {@code resolutionType} +
     * MISHAP 音效,并在元求值上下文里清掉未闭合的括号状态。
     */
    private CastResult mishap(Mishap mishap, CastingVM harness, SpellContinuation continuation, List<Iota> stack) {
        boolean wipeParens = continuation instanceof SpellContinuation.NotDone notDone
                && notDone.getFrame() instanceof FrameEvaluate frameEval
                && frameEval.isMetacasting();

        return new CastResult(
                new ListIota(stack),
                continuation,
                wipeParens ? harness.getImage().withResetEscape() : null,
                List.of(new OperatorSideEffect.DoMishap(mishap, new Mishap.Context(null, null))),
                mishap.resolutionType(harness.getEnv()),
                HexEvalSounds.MISHAP);
    }

    /**
     * 换掉 image 里的栈。
     *
     * <p>0.11.4 的 {@link CastingImage} 是 Kotlin data class,{@code copy} 的参数全是位置参数,
     * 一旦写错顺序就会把 {@code escapeNext} 与别的布尔量对调 —— 那种 bug 极难查。
     * 所以集中到这里写一次。
     */

    /** 本 iota 的类型对象。 */
    public static final IotaType<SwitchIota> TYPE = new IotaType<>() {

        @Override
        public @Nullable SwitchIota deserialize(Tag tag, ServerLevel world) throws IllegalArgumentException {
            var ctag = HexUtils.downcast(tag, CompoundTag.TYPE);

            // 匹配类型:缺省 = 未定(null 键组成的表);写了名字却认不出来才算存档损坏
            SwitchMatchType matchType = null;
            if (ctag.contains("match_type", Tag.TAG_STRING)) {
                String typeName = ctag.getString("match_type");
                for (SwitchMatchType candidate : SwitchMatchType.values()) {
                    if (candidate.name().equalsIgnoreCase(typeName)) {
                        matchType = candidate;
                        break;
                    }
                }
                if (matchType == null) {
                    return null;
                }
            }

            var casesTag = HexUtils.downcast(ctag.get("cases"), ListTag.TYPE);
            var cases = new ArrayList<SwitchIota.Case>(casesTag.size());
            for (Tag sub : casesTag) {
                var csub = HexUtils.downcast(sub, CompoundTag.TYPE);
                // 参数显式声明为 Tag,否则会被解析成静态的 IotaType.deserialize(CompoundTag, ...)(返回 Iota)
                SwitchIota.Case entry = SwitchIota.Case.deserialize((Tag) csub, world);
                if (entry == null) {
                    return null;
                }
                cases.add(entry);
            }

            TreeList<Iota> defaultBody = null;
            if (ctag.contains("default", Tag.TAG_LIST)) {
                var defTag = HexUtils.downcast(ctag.get("default"), ListTag.TYPE);
                var def = new ArrayList<Iota>(defTag.size());
                for (Tag sub : defTag) {
                    var csub = HexUtils.downcast(sub, CompoundTag.TYPE);
                    var iota = IotaType.deserialize(csub, world);
                    if (iota == null) {
                        return null;
                    }
                    def.add(iota);
                }
                defaultBody = TreeList.from(def);
            }

            return new SwitchIota(matchType, cases, defaultBody);
        }

        @Override
        public Component display(Tag tag) {
            var ctag = HexUtils.downcast(tag, CompoundTag.TYPE);
            MutableComponent out = Component.empty();

            out.append(Component.literal("switch<").withStyle(ChatFormatting.GOLD));
            out.append(Component.translatable(
                    "hexextra.switch.type." + ctag.getString("match_type").toLowerCase()).withStyle(ChatFormatting.AQUA));
            out.append(Component.literal(">{").withStyle(ChatFormatting.GOLD));

            var casesTag = HexUtils.downcast(ctag.get("cases"), ListTag.TYPE);
            for (int i = 0; i < casesTag.size(); i++) {
                var csub = HexUtils.downcast(casesTag.get(i), CompoundTag.TYPE);
                out.append(Component.literal("\n"));
                out.append(SwitchIota.Case.displayFromTag(csub));
            }

            if (ctag.contains("default", Tag.TAG_LIST)) {
                out.append(Component.literal("\ndefault -> {").withStyle(ChatFormatting.GOLD));
                MutableComponent bodyOut = Component.empty();
                var defTag = HexUtils.downcast(ctag.get("default"), ListTag.TYPE);
                for (int i = 0; i < defTag.size(); i++) {
                    var csub = HexUtils.downcast(defTag.get(i), CompoundTag.TYPE);
                    bodyOut.append(IotaType.getDisplay(csub));
                    if (i < defTag.size() - 1) {
                        bodyOut.append(", ");
                    }
                }
                out.append(Component.translatable("hexcasting.tooltip.list_contents", bodyOut)
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }

            out.append(Component.literal("\n}").withStyle(ChatFormatting.GOLD));
            return out;
        }

        @Override
        public int color() {
            return 0xff_3d6fbf;
        }
    };
}
