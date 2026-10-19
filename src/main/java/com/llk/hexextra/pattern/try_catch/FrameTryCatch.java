package com.llk.hexextra.pattern.try_catch;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.eval.vm.FrameEvaluate;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.pattern.if_else.IfElseCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import kotlin.Pair;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 「try / catch / finally」的续延帧。
 *
 * <h2>阶段</h2>
 * <pre>
 * stage 0:刚进来  → 压出 try 块,自己进入 stage 1
 * stage 1:try 正常跑完 → 有 finally 就跑 finally,否则结束
 * stage 2:try 抛了 → 压出 catch 块,自己进入 stage 3
 * stage 3:catch 跑完 → 有 finally 就跑 finally,否则结束
 * stage 4:finally 跑完 → 结束
 * </pre>
 *
 * <p>注意 stage 2 不是本帧自己走到的 —— 是本帧还挂在续延上时,内层某一步抛了
 * {@code Mishap},由 {@code MixinCastingVM} 在 {@code executeInner} 的出口
 * 改写结果、把本帧换成 stage 2 并带上异常值。所以本帧必须能<b>从任意深度的续延里被找到</b>
 * (见 {@link #findNearest})。
 *
 * <h2>代价不回滚</h2>
 * try 块里已经花掉的媒质、已经改掉的方块都不会被撤销 —— 本帧只负责"别让法术整个中断"。
 *
 * @param stage     阶段,见上
 * @param exception 捕获到的异常 iota;只有 stage >= 2 时非 null
 */
public record FrameTryCatch(
        TreeList<Iota> tryBody,
        TreeList<Iota> catchBody,
        @Nullable TreeList<Iota> finallyBody,
        int stage,
        @Nullable Iota exception
) implements ContinuationFrame {

    /** 在续延里找最近的、处于 stage 0/1(也就是"try 还开着")的本帧。 */
    @Nullable
    public static FrameTryCatch findNearest(SpellContinuation continuation) {
        SpellContinuation current = continuation;
        while (current instanceof SpellContinuation.NotDone notDone) {
            if (notDone.getFrame() instanceof FrameTryCatch frame
                    && (frame.stage() == 0 || frame.stage() == 1)) {
                return frame;
            }
            current = notDone.getNext();
        }
        return null;
    }

    /** 在续延里找最近的、处于 catch 阶段的本帧(供 get_exception 用)。 */
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
            default -> this.cast(continuation, vm, TreeList.empty());
        };
    }

    /** 压出一个代码块,跑完后回到本帧的指定阶段。 */
    private CastResult run(SpellContinuation continuation, CastingVM vm,
                           TreeList<Iota> body, int nextStage, @Nullable Iota ex) {
        SpellContinuation next = continuation
                .pushFrame(new FrameTryCatch(this.tryBody, this.catchBody, this.finallyBody, nextStage, ex))
                .pushFrame(new FrameEvaluate(body, true));
        return this.cast(next, vm, body);
    }

    /** 一个块跑完了:还有 finally 就跑它,否则整条 try 结束。 */
    private CastResult finish(SpellContinuation continuation, CastingVM vm, @Nullable Iota ex) {
        if (this.finallyBody != null) {
            SpellContinuation next = continuation
                    .pushFrame(new FrameTryCatch(this.tryBody, this.catchBody, null, 4, ex))
                    .pushFrame(new FrameEvaluate(this.finallyBody, true));
            return this.cast(next, vm, this.finallyBody);
        }
        return this.cast(continuation, vm, TreeList.empty());
    }

    /** try 块抛错时由 Mixin 调用:换成"进入 catch"的新结果。 */
    public CastResult enterCatch(SpellContinuation continuation, CastingVM vm, Iota errorIota) {
        SpellContinuation next = continuation.pushFrame(
                new FrameTryCatch(this.tryBody, this.catchBody, this.finallyBody, 2, errorIota));
        return this.cast(next, vm, TreeList.empty());
    }

    /** halt 打到本帧:照常往外传(try 不是循环,没有"跳出自己"的语义)。 */
    @Override
    public Pair<Boolean, TreeList<Iota>> breakDownwards(TreeList<Iota> stack) {
        return new Pair<>(false, stack);
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
                HexEvalSounds.HERMES.get());
    }

    public static final ContinuationFrame.Type<FrameTryCatch> TYPE = new ContinuationFrame.Type<>() {

        private final MapCodec<FrameTryCatch> codec = RecordCodecBuilder.mapCodec(inst -> inst.group(
                IfElseCodecs.CODE_BLOCK.fieldOf("try").forGetter(FrameTryCatch::tryBody),
                IfElseCodecs.CODE_BLOCK.fieldOf("catch").forGetter(FrameTryCatch::catchBody),
                IfElseCodecs.CODE_BLOCK.optionalFieldOf("finally")
                        .forGetter(f -> Optional.ofNullable(f.finallyBody())),
                Codec.INT.fieldOf("stage").forGetter(FrameTryCatch::stage),
                IotaType.TYPED_CODEC.optionalFieldOf("exception")
                        .forGetter(f -> Optional.ofNullable(f.exception()))
        ).apply(inst, (tryBody, catchBody, fin, stage, ex) ->
                new FrameTryCatch(tryBody, catchBody, fin.orElse(null), stage, ex.orElse(null))));

        private final StreamCodec<RegistryFriendlyByteBuf, FrameTryCatch> streamCodec = StreamCodec.composite(
                IfElseCodecs.CODE_BLOCK_STREAM, FrameTryCatch::tryBody,
                IfElseCodecs.CODE_BLOCK_STREAM, FrameTryCatch::catchBody,
                ByteBufCodecs.optional(IfElseCodecs.CODE_BLOCK_STREAM),
                f -> Optional.ofNullable(f.finallyBody()),
                ByteBufCodecs.VAR_INT, FrameTryCatch::stage,
                ByteBufCodecs.optional(IotaType.TYPED_STREAM_CODEC),
                f -> Optional.ofNullable(f.exception()),
                (tryBody, catchBody, fin, stage, ex) ->
                        new FrameTryCatch(tryBody, catchBody, fin.orElse(null), stage, ex.orElse(null)));

        @Override
        public MapCodec<FrameTryCatch> codec() {
            return this.codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FrameTryCatch> streamCodec() {
            return this.streamCodec;
        }
    };
}