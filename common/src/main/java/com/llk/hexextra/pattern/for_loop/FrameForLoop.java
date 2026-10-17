package com.llk.hexextra.pattern.for_loop;

import at.petrak.hexcasting.api.casting.SpellList;
import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.eval.vm.FrameEvaluate;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.utils.HexUtils;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import kotlin.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * for 循环的续延帧 —— 「佛尔之策略」把代码块重复跑 N 次的核心。
 *
 * <h2>为什么必须用续延帧</h2>
 * 循环体本身是<b>一串图案</b>,要交给 VM 去求值。求值会跨多个 VM 步,
 * 所以不能在动作里"就地循环" —— 必须把"还剩几轮"这个状态挂到续延栈上,
 * 每轮求值完回到本帧时再决定要不要继续。这就是 {@link ContinuationFrame} 的用途。
 *
 * <h2>循环体与外界共用同一个栈</h2>
 * 每轮结束时不重置栈,于是上一轮压的东西下一轮能接着用 ——
 * 这样循环体才能把结果累积起来(与 1.21.1 版语义完全一致)。
 *
 * <h2>0.11.4 的实现要点</h2>
 * <ul>
 *   <li>{@code breakDownwards} 返回 {@code kotlin.Pair}(Java 里是 {@link Pair});</li>
 *   <li>序列化是手写 {@link CompoundTag},没有 Codec;</li>
 *   <li>类型注册走 {@code HexRegistries.CONTINUATION_TYPE},类型对象是
 *       {@link ContinuationFrame.Type},它只要求实现 {@code deserializeFromNBT}。</li>
 * </ul>
 */
public record FrameForLoop(TreeList<Iota> body, int remaining) implements ContinuationFrame {

    /**
     * 继续下发:返回 false 表示"不打断下层续延"。
     *
     * <p>这个方法在「卡戎之策略」之类的中断路径上被调用。循环帧本身不拦截中断 ——
     * 也就是说<b>在循环体里画卡戎能提前跳出循环</b>,这是我们想要的语义。
     */
    @Override
    public Pair<Boolean, List<Iota>> breakDownwards(List<? extends Iota> stack) {
        // Kotlin 的 List<Iota> 编译到 Java 是 List<? extends Iota>,而 Pair 的第二个参数要求 List<Iota>
        @SuppressWarnings("unchecked")
        List<Iota> mutable = (List<Iota>) stack;
        return new Pair<>(false, mutable);
    }

    /**
     * 走一步。
     *
     * <p>还有剩余次数就把"次数减一的自己"和"代码块的求值帧"一起压上去;
     * 次数用完了就把控制权交回下层续延(什么都不做)。
     */
    @Override
    public CastResult evaluate(SpellContinuation continuation, ServerLevel level, CastingVM harness) {
        CastingImage image = harness.getImage();

        // ---- 次数用完了:这一轮什么都不做,让下层续延继续 ----
        if (this.remaining <= 0) {
            return new CastResult(
                    new ListIota(this.body),
                    continuation,
                    image,
                    List.<OperatorSideEffect>of(),
                    ResolvedPatternType.EVALUATED,
                    HexEvalSounds.NOTHING);
        }

        // ---- 还有次数:先压"下一轮",再压"本轮要跑的代码块" ----
        // 压栈顺序决定了执行顺序:后压的先跑。所以先 push 下一轮的帧、再 push 代码块,
        // 这样代码块跑完后会回到下一轮的帧。
        SpellContinuation next = continuation
                .pushFrame(new FrameForLoop(this.body, this.remaining - 1))
                .pushFrame(new FrameEvaluate(new SpellList.LList(this.body), true));

        return new CastResult(
                new ListIota(this.body),
                next,
                // 每轮开始前清掉未闭合的括号状态,免得上一轮的括号泄漏到下一轮
                image.withResetEscape(),
                List.<OperatorSideEffect>of(),
                ResolvedPatternType.EVALUATED,
                HexEvalSounds.HERMES);
    }

    /**
     * 手写 NBT 序列化。
     *
     * <p>把代码块存成一个 ListTag(每个元素都是 {@code {type, data}}),
     * 反序列化时用 {@code HexIotaTypes.LIST} 解回来 —— 这样嵌套结构与存档格式
     * 完全交给本体维护。
     */
    @Override
    public CompoundTag serializeToNBT() {
        var out = new CompoundTag();
        out.put("body", serializeBlock(this.body));
        out.putInt("remaining", this.remaining);
        return out;
    }

    @Override
    public int size() {
        return this.body.size() + 1;
    }

    @Override
    public ContinuationFrame.Type<?> getType() {
        return TYPE;
    }

    // =======================================================================
    // 工具
    // =======================================================================

    /** 把一个代码块序列化成 ListTag。 */
    static ListTag serializeBlock(TreeList<Iota> block) {
        var out = new ListTag();
        for (Iota iota : block) {
            out.add(IotaType.serialize(iota));
        }
        return out;
    }

    /** 从 ListTag 解回一个代码块。解不开就返回空(反正存档已经坏了,不如不崩)。 */
    static TreeList<Iota> deserializeBlock(@Nullable Tag tag, ServerLevel world) {
        if (tag == null) {
            return TreeList.empty();
        }
        var listTag = HexUtils.downcast(tag, ListTag.TYPE);
        List<Iota> out = new ArrayList<>(listTag.size());
        for (Tag sub : listTag) {
            var csub = HexUtils.downcast(sub, CompoundTag.TYPE);
            var iota = IotaType.deserialize(csub, world);
            out.add(iota == null ? new at.petrak.hexcasting.api.casting.iota.GarbageIota() : iota);
        }
        return TreeList.from(out);
    }

    /** 本帧的类型对象 —— 只要求实现"从 NBT 还原"。 */
    public static final ContinuationFrame.Type<FrameForLoop> TYPE = new ContinuationFrame.Type<>() {
        @Override
        public @NotNull FrameForLoop deserializeFromNBT(CompoundTag tag, ServerLevel world) {
            return new FrameForLoop(
                    deserializeBlock(tag.get("body"), world),
                    tag.getInt("remaining"));
        }
    };
}
