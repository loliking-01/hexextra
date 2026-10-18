package com.llk.hexextra.pattern.if_else;

import at.petrak.hexcasting.api.casting.SpellList;
import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.eval.vm.FrameEvaluate;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.CastingImages;
import com.llk.hexextra.core.HexextraMishaps;
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
 * if / elseif / else 的续延帧 —— 条件链的求值核心。
 *
 * <h2>两阶段设计</h2>
 * 一条分支要做两件事:先<b>求值条件代码块</b>(跨多个 VM 步),再<b>检查它留下了什么</b>。
 * 这两件事之间必须回到本帧,所以用 {@link #deciding} 区分阶段:
 * <ul>
 *   <li>{@code deciding == false}:记录当前栈大小,压入"下一阶段的自己"与条件的求值帧;</li>
 *   <li>{@code deciding == true}:检查栈上是否<b>恰好</b>多出一个布尔值,是则据此走向下一分支/else/代码块。</li>
 * </ul>
 *
 * <h2>条件块必须恰好留下一个布尔</h2>
 * 留 0 个(忘了产出)、留多个(多留了东西)、留了非布尔 —— 全都是错误,
 * 分别报 {@code IfConditionCount} / {@code IfConditionNotBool}。
 * 这也是本项目刻意不用"栈顶一弹了事"的原因:那样玩家写错条件时会被静默忽略。
 *
 * <h2>⚠ 报错必须折进 CastResult,不能抛</h2>
 * {@code CastingVM.executeInner} 只有一个 catch,会把 Mishap 包成内部错误并打印堆栈。
 * 所以本类用 {@link #mishap} 把报错折成 {@code DoMishap} 副作用。
 */
public record FrameIfElse(List<IfElseIota.Branch> branches,
                          @Nullable TreeList<Iota> elseBody,
                          int index,
                          boolean deciding,
                          int stackSizeBefore) implements ContinuationFrame {

    @Override
    public Pair<Boolean, List<Iota>> breakDownwards(List<? extends Iota> stack) {
        @SuppressWarnings("unchecked")
        List<Iota> mutable = (List<Iota>) stack;
        return new Pair<>(false, mutable);
    }

    @Override
    public CastResult evaluate(SpellContinuation continuation, ServerLevel level, CastingVM harness) {
        CastingImage image = harness.getImage();

        // ===== 阶段一:开始求值第 index 条分支的条件 =====
        if (!this.deciding) {
            if (this.index >= this.branches.size()) {
                // 没有分支可跑了 —— 有 else 就跑 else,没有就什么都不做
                return this.runElseOrNothing(harness, continuation, image);
            }

            TreeList<Iota> condition = this.branches.get(this.index).condition();
            int before = image.getStack().size();

            SpellContinuation next = continuation
                    .pushFrame(new FrameIfElse(this.branches, this.elseBody, this.index, true, before))
                    .pushFrame(new FrameEvaluate(new SpellList.LList(condition), true));

            return new CastResult(
                    new ListIota(condition),
                    next,
                    image.withResetEscape(),
                    List.<OperatorSideEffect>of(),
                    ResolvedPatternType.EVALUATED,
                    HexEvalSounds.HERMES);
        }

        // ===== 阶段二:检查条件留下了什么 =====
        List<Iota> stack = image.getStack();
        int produced = stack.size() - this.stackSizeBefore;

        if (produced != 1) {
            return this.mishap(new HexextraMishaps.IfConditionCount(produced), harness, continuation, stack);
        }

        Iota top = stack.get(stack.size() - 1);
        if (!(top instanceof BooleanIota bool)) {
            return this.mishap(new HexextraMishaps.IfConditionNotBool(top), harness, continuation, stack);
        }

        // 消费掉那个布尔值
        List<Iota> rest = new ArrayList<>(stack.subList(0, stack.size() - 1));
        CastingImage consumed = CastingImages.withStack(image.withUsedOp(), rest);

        // ---- 条件为真:跑这条分支的代码块 ----
        if (bool.getBool()) {
            TreeList<Iota> body = this.branches.get(this.index).body();
            SpellContinuation next = continuation
                    .pushFrame(new FrameEvaluate(new SpellList.LList(body), true));
            return new CastResult(
                    new ListIota(body), next, consumed,
                    List.<OperatorSideEffect>of(),
                    ResolvedPatternType.EVALUATED, HexEvalSounds.HERMES);
        }

        // ---- 条件为假:试下一条分支 ----
        int nextIndex = this.index + 1;
        if (nextIndex < this.branches.size()) {
            SpellContinuation next = continuation
                    .pushFrame(new FrameIfElse(this.branches, this.elseBody, nextIndex, false, 0));
            return new CastResult(
                    new ListIota(this.branches.get(nextIndex).condition()), next, consumed,
                    List.<OperatorSideEffect>of(),
                    ResolvedPatternType.EVALUATED, HexEvalSounds.HERMES);
        }

        // ---- 没有更多分支了:走 else 或什么都不做 ----
        return this.runElseOrNothing(harness, continuation, consumed);
    }

    /** 跑 else 块;没有 else 就原地结束。 */
    private CastResult runElseOrNothing(CastingVM harness, SpellContinuation continuation, CastingImage image) {
        TreeList<Iota> elseBody = this.elseBody;
        if (elseBody == null) {
            return new CastResult(
                    new ListIota(TreeList.empty()), continuation, image,
                    List.<OperatorSideEffect>of(),
                    ResolvedPatternType.EVALUATED, HexEvalSounds.NOTHING);
        }
        SpellContinuation next = continuation
                .pushFrame(new FrameEvaluate(new SpellList.LList(elseBody), true));
        return new CastResult(
                new ListIota(elseBody), next, image,
                List.<OperatorSideEffect>of(),
                ResolvedPatternType.EVALUATED, HexEvalSounds.HERMES);
    }

    /** 把报错折进 {@link CastResult}(不能抛)。 */
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


    // =======================================================================
    // 序列化
    // =======================================================================

    @Override
    public CompoundTag serializeToNBT() {
        var out = new CompoundTag();

        var branchesTag = new ListTag();
        for (IfElseIota.Branch branch : this.branches) {
            var one = new CompoundTag();
            one.put("condition", IfElseIota.serializeBlock(branch.condition()));
            one.put("body", IfElseIota.serializeBlock(branch.body()));
            branchesTag.add(one);
        }
        out.put("branches", branchesTag);

        if (this.elseBody != null) {
            out.put("else", IfElseIota.serializeBlock(this.elseBody));
        }

        out.putInt("index", this.index);
        out.putBoolean("deciding", this.deciding);
        out.putInt("stack_size_before", this.stackSizeBefore);
        return out;
    }

    @Override
    public int size() {
        int total = 1;
        for (IfElseIota.Branch branch : this.branches) {
            total += branch.condition().size() + branch.body().size();
        }
        if (this.elseBody != null) {
            total += this.elseBody.size();
        }
        return total;
    }

    @Override
    public ContinuationFrame.Type<?> getType() {
        return TYPE;
    }

    public static final ContinuationFrame.Type<FrameIfElse> TYPE = new ContinuationFrame.Type<>() {
        @Override
        public @NotNull FrameIfElse deserializeFromNBT(CompoundTag tag, ServerLevel world) {
            var branchesTag = tag.getList("branches", Tag.TAG_COMPOUND);
            var branches = new ArrayList<IfElseIota.Branch>(branchesTag.size());
            for (int i = 0; i < branchesTag.size(); i++) {
                var one = branchesTag.getCompound(i);
                branches.add(new IfElseIota.Branch(
                        IfElseIota.deserializeBlock(one.get("condition"), world),
                        IfElseIota.deserializeBlock(one.get("body"), world)));
            }

            TreeList<Iota> elseBody = null;
            if (tag.contains("else", Tag.TAG_LIST)) {
                elseBody = IfElseIota.deserializeBlock(tag.get("else"), world);
            }

            return new FrameIfElse(branches, elseBody,
                    tag.getInt("index"),
                    tag.getBoolean("deciding"),
                    tag.getInt("stack_size_before"));
        }
    };
}