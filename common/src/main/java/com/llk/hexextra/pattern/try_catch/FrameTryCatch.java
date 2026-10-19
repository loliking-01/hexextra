package com.llk.hexextra.pattern.try_catch;

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
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.pattern.if_else.IfElseIota;
import kotlin.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 「try / catch / finally」的续延帧(0.11.4 版)。
 *
 * <h2>阶段</h2>
 * <pre>
 * stage 0:刚进来        → 压出 try 块,自己进入 stage 1
 * stage 1:try 正常跑完  → 有 finally 就跑 finally(进入 stage 4),否则结束
 * stage 2:try 抛了      → 压出 catch 块,自己进入 stage 3   ← Mixin 改写进来
 * stage 3:catch 跑完    → 有 finally 就跑 finally,否则结束
 * stage 4:finally 跑完  → 结束
 * </pre>
 *
 * <p>stage 2 不是本帧自己走到的:本帧还挂在续延上时内层抛了 {@code Mishap},
 * 由 {@code MixinCastingVM} 在 {@code executeInner} 出口改写结果、把本帧换成 stage 2。
 * 所以本帧必须能从任意深度的续延里被找到(见 {@link #findNearest})。
 *
 * <h2>代价不回滚</h2>
 * try 块里已经花掉的媒质、已经改动的方块都不会被撤销 —— 本帧只负责"别让法术整个中断"。
 *
 * <h2>0.11.4 的序列化</h2>
 * 这个版本没有 Codec ✗ 帧自己实现 {@link #serializeToNBT()} ✗
 * 类型只实现 {@code deserializeFromNBT} ✓ 代码块借用 {@link IfElseIota} 的静态助手 ✓
 */
public record FrameTryCatch(
        TreeList<Iota> tryBody,
        TreeList<Iota> catchBody,
        @Nullable TreeList<Iota> finallyBody,
        int stage,
        @Nullable Iota exception
) implements ContinuationFrame {

    /** 在续延里找最近的、try 还开着(stage 0/1)的本帧。 */
    @Nullable
    public static FrameTryCatch findNearest(SpellContinuation continuation) {
        SpellContinuation current = continuation;
        while (current instanceof SpellContinuation.NotDone notDone) {
            if (notDone.getFrame() instanceof FrameTryCatch frame
                    && (frame.stage() == 0 || frame.stage() == 1 || frame.stage() == 3)) {
                return frame;
            }
            current = notDone.getNext();
        }
        return null;
    }

    /** 在续延里找最近的、正处于 catch(stage 2/3)且有异常值的本帧。 */
    @Nullable
    public static FrameTryCatch findInCatch(SpellContinuation continuation) {
        SpellContinuation current = continuation;
        while (current instanceof SpellContinuation.NotDone notDone) {
            if (notDone.getFrame() instanceof FrameTryCatch frame
                    && frame.stage() >= 2 && frame.stage() <= 3 && frame.exception() != null) {
                return frame;
            }
            current = notDone.getNext();
        }
        return null;
    }

    @Override
    public CastResult evaluate(SpellContinuation continuation, ServerLevel level, CastingVM vm) {
        return switch (this.stage) {
            case 0 -> this.run(continuation, vm, this.tryBody, 1, null);
            case 1 -> this.finish(continuation, vm, null);
            case 2 -> this.run(continuation, vm, this.catchBody, 3, this.exception);
            case 3 -> this.finish(continuation, vm, this.exception);
            case 4 -> this.exception != null
                    ? this.rethrow(continuation, vm, this.exception)
                    : this.cast(continuation, vm, TreeList.empty());
            default -> this.cast(continuation, vm, TreeList.empty());
        };
    }

    /** finally 跑完后,把 catch 里那次错抛出去。 */
    private CastResult rethrow(SpellContinuation continuation, CastingVM vm, Iota error) {
        net.minecraft.network.chat.Component message = error instanceof ExceptionIota ex
                ? ex.getMessage()
                : error.display();

        return new CastResult(
                new ListIota(TreeList.empty()),
                continuation,
                vm.getImage().withUsedOp(),
                List.of(new OperatorSideEffect.DoMishap(
                        new com.llk.hexextra.core.HexextraMishaps.Rethrown(message),
                        new at.petrak.hexcasting.api.casting.mishaps.Mishap.Context(null, null))),
                ResolvedPatternType.ERRORED,
                HexEvalSounds.MISHAP);
    }

    /** 压出一个代码块,跑完后回到本帧的指定阶段。 */
    private CastResult run(SpellContinuation continuation, CastingVM vm,
                           TreeList<Iota> body, int nextStage, @Nullable Iota ex) {
        SpellContinuation next = continuation
                .pushFrame(new FrameTryCatch(this.tryBody, this.catchBody, this.finallyBody, nextStage, ex))
                .pushFrame(new FrameEvaluate(new SpellList.LList(body), true));
        return this.cast(next, vm, body);
    }

    /** 一个块跑完了:还有 finally 就跑它,否则整条 try 结束。 */
    private CastResult finish(SpellContinuation continuation, CastingVM vm, @Nullable Iota ex) {
        if (this.finallyBody != null) {
            SpellContinuation next = continuation
                    .pushFrame(new FrameTryCatch(this.tryBody, this.catchBody, null, 4, ex))
                    .pushFrame(new FrameEvaluate(new SpellList.LList(this.finallyBody), true));
            return this.cast(next, vm, this.finallyBody);
        }
        return this.cast(continuation, vm, TreeList.empty());
    }

    /** try 块抛错时由 Mixin 调用:换成"进入 catch"的新结果。 */
    public CastResult enterCatch(SpellContinuation continuation, CastingVM vm, Iota errorIota) {
        // catch 块自己也抛了错(stage 3):finally 仍然要跑,跑完再抛出去
        if (this.stage == 3) {
            TryCatchExitGuard.request();
            return this.finish(continuation, vm, errorIota);
        }
        TryCatchExitGuard.request();

        SpellContinuation next = continuation.pushFrame(
                new FrameTryCatch(this.tryBody, this.catchBody, this.finallyBody, 2, errorIota));
        return this.cast(next, vm, TreeList.empty());
    }

    @Override
    public Pair<Boolean, List<Iota>> breakDownwards(List<? extends Iota> stack) {
        @SuppressWarnings("unchecked")
        List<Iota> mutable = (List<Iota>) stack;
        return new Pair<>(false, mutable);
    }

    @Override
    public int size() {
        int total = this.tryBody.size() + this.catchBody.size();
        if (this.finallyBody != null) {
            total += this.finallyBody.size();
        }
        return total + 1;
    }

    @Override
    public ContinuationFrame.Type<?> getType() {
        return TYPE;
    }

    private CastResult cast(SpellContinuation continuation, CastingVM vm, TreeList<Iota> block) {
        return new CastResult(
                new ListIota(block),
                continuation,
                vm.getImage().withUsedOp(),
                List.<OperatorSideEffect>of(),
                ResolvedPatternType.EVALUATED,
                HexEvalSounds.HERMES);
    }

    // =======================================================================
    // 序列化(0.11.4:手写 NBT)
    // =======================================================================

    @Override
    public CompoundTag serializeToNBT() {
        CompoundTag out = new CompoundTag();
        out.put("try", IfElseIota.serializeBlock(this.tryBody));
        out.put("catch", IfElseIota.serializeBlock(this.catchBody));
        if (this.finallyBody != null) {
            out.put("finally", IfElseIota.serializeBlock(this.finallyBody));
        }
        out.putInt("stage", this.stage);
        if (this.exception != null) {
            out.put("exception", IotaType.serialize(this.exception));
        }
        return out;
    }

    public static final ContinuationFrame.Type<FrameTryCatch> TYPE = new ContinuationFrame.Type<>() {

        @Override
        public @NotNull FrameTryCatch deserializeFromNBT(CompoundTag tag, ServerLevel world) {
            TreeList<Iota> tryBody = IfElseIota.deserializeBlock(tag.get("try"), world);
            TreeList<Iota> catchBody = IfElseIota.deserializeBlock(tag.get("catch"), world);

            TreeList<Iota> finallyBody = null;
            if (tag.contains("finally", Tag.TAG_LIST)) {
                finallyBody = IfElseIota.deserializeBlock(tag.get("finally"), world);
            }

            Iota exception = null;
            if (tag.contains("exception", Tag.TAG_COMPOUND)) {
                exception = IotaType.deserialize(tag.getCompound("exception"), world);
            }

            return new FrameTryCatch(tryBody, catchBody, finallyBody,
                    tag.getInt("stage"), exception);
        }
    };
}