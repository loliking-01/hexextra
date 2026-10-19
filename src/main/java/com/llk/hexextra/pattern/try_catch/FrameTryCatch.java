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
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
 * stage 0:刚进来        → 压出 try 块,自己进入 stage 1
 * stage 1:try 正常跑完  → 有 finally 就跑 finally(进入 4),否则结束
 * stage 2:try 抛了      → 压出 catch 块,自己进入 stage 3   ← Mixin 改写进来
 * stage 3:catch 跑完    → 有 finally 就跑 finally,否则结束
 * stage 4:finally 跑完  → 结束(如果带着 catch 的错就抛出去)
 * </pre>
 *
 * <h2>⚠ 进入 catch 前必须把旧帧拆掉</h2>
 * 报错发生时续延是 {@code [本帧@1, FrameEvaluate(try 块), ...外层]}。
 * 如果只在最上面压一个「进入 catch」的新帧,旧的本帧仍然留在下面 ——
 * catch 跑完后控制权又回到它 ✗ 它重跑 finally ✗ finally 再次抛错 ✗
 * 又被接住 ✗ **无限循环**,而且每轮往栈上留一个值,最终撞上「栈的大小上限」。
 * 所以 {@link #enterCatch} 里先用 {@link #dropAbove} 把这一层整个拆掉。
 *
 * <h2>代价不回滚</h2>
 * try 块里已经花掉的媒质、已经改动的方块都不会被撤销 —— 本帧只负责"别让法术整个中断"。
 *
 * <h2>finally 里抛错会怎样</h2>
 * <ul>
 *   <li><b>不会被自己接住</b> —— {@link #findNearest} 明确排除 stage 4,否则会无限重入;</li>
 *   <li><b>会被外层的 try 接住</b> —— 沿续延往上找,外层 stage 0/1/3 的帧照样命中;</li>
 *   <li>没有外层 try 时异常照常冒泡,法术以 finally 的那个错结束(覆盖 catch 的错,与 Java 一致)。</li>
 * </ul>
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

    /** 诊断开关:排查时临时打开。 */
    private static final boolean TRACE = false;

    // =======================================================================
    // 在续延里查找
    // =======================================================================

    /**
     * 找最近的、处于「可以接住报错」状态的本帧:
     * stage 0/1 = try 还开着(跳进 catch),stage 3 = catch 正在跑(跳进 finally)。
     *
     * <p>stage 4 被<b>故意排除</b>:finally 自己抛错时不能再回到自己的 catch,
     * 否则就是无限循环。
     */
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

    /** 找最近的、处于 catch 阶段(stage 2/3)且有异常值的本帧(供 get_exception 用)。 */
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

    /**
     * 把续延里「本帧及其上方」全部丢掉,只留它下面的部分。
     *
     * <p>try 块已被判定失败,它上面那些求值帧(以及本帧自己)都不该再恢复。
     */
    private SpellContinuation dropAbove(SpellContinuation continuation) {
        SpellContinuation current = continuation;
        while (current instanceof SpellContinuation.NotDone notDone) {
            if (notDone.getFrame() == this) {
                return notDone.getNext();
            }
            current = notDone.getNext();
        }
        // 理论上不会发生(调用方就是从续延里找到的本帧);保底原样返回
        return continuation;
    }

    // =======================================================================
    // 执行
    // =======================================================================

    @Override
    public CastResult evaluate(SpellContinuation continuation, ServerLevel level, CastingVM vm) {
        if (TRACE) {
            com.llk.hexextra.Hexextra.LOGGER.info("[try_catch] evaluate stage={} 栈深={}",
                    this.stage, vm.getImage().getStack().size());
        }
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

    /** 压出一个代码块,跑完后回到本帧的指定阶段。返回的帧不再保留已经开跑的块。 */
    private CastResult run(SpellContinuation continuation, CastingVM vm,
                           TreeList<Iota> body, int nextStage, @Nullable Iota ex) {
        SpellContinuation next = continuation
                .pushFrame(new FrameTryCatch(TreeList.empty(), this.catchBody, this.finallyBody, nextStage, ex))
                .pushFrame(new FrameEvaluate(body, true));
        return this.cast(next, vm, body);
    }

    /** 一个块跑完了:还有 finally 就跑它,否则整条 try 结束。 */
    private CastResult finish(SpellContinuation continuation, CastingVM vm, @Nullable Iota ex) {
        if (this.finallyBody != null) {
            SpellContinuation next = continuation
                    .pushFrame(new FrameTryCatch(TreeList.empty(), TreeList.empty(), null, 4, ex))
                    .pushFrame(new FrameEvaluate(this.finallyBody, true));
            return this.cast(next, vm, this.finallyBody);
        }
        return this.cast(continuation, vm, TreeList.empty());
    }

    /**
     * try 块(或 catch 块)抛错时由 Mixin 调用:换成"进入 catch / finally"的新结果。
     *
     * <p><b>关键</b>:先用 {@link #dropAbove} 把本帧与它上方的求值帧拆掉,
     * 否则 catch 跑完后会回到旧帧,形成无限循环。
     */
    public CastResult enterCatch(SpellContinuation continuation, CastingVM vm, Iota errorIota) {
        // 告诉 VM:这次报错不应让循环退出 —— 否则 catch 帧求值不到
        TryCatchExitGuard.request();

        SpellContinuation base = this.dropAbove(continuation);

        // catch 自己又抛了:finally 仍然要跑,跑完再把这次的错抛出去
        if (this.stage == 3) {
            return this.finish(base, vm, errorIota);
        }

        SpellContinuation next = base.pushFrame(
                new FrameTryCatch(TreeList.empty(), this.catchBody, this.finallyBody, 2, errorIota));
        return this.cast(next, vm, TreeList.empty());
    }

    /** finally 跑完后,把 catch 里那次错抛出去。 */
    private CastResult rethrow(SpellContinuation continuation, CastingVM vm, Iota error) {
        Component message = error instanceof ExceptionIota ex ? ex.getMessage() : error.display();

        return new CastResult(
                new ListIota(TreeList.empty()),
                continuation,
                vm.getImage().withUsedOp(),
                List.of(new OperatorSideEffect.DoMishap(
                        new com.llk.hexextra.core.HexextraMishaps.Rethrown(message),
                        new at.petrak.hexcasting.api.casting.mishaps.Mishap.Context(null, null))),
                ResolvedPatternType.ERRORED,
                HexEvalSounds.MISHAP.get());
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