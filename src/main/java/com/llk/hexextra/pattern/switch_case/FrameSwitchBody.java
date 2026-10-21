package com.llk.hexextra.pattern.switch_case;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import kotlin.Pair;

import java.util.List;

/**
 * switch 的执行帧 —— 在 case 体(含 default 体)运行期间携带<b>受控值</b>。
 *
 * <h2>为什么必须有这么一个帧</h2>
 * 「凯斯之精思」要在 case 体里把"分派的那个值"取回来。而 case 体现在是用原版的
 * {@code FrameEvaluate} 跑的 —— 它跑完就消失,没法回头查"刚才分派的是什么"。
 *
 * <p>{@link com.llk.hexextra.pattern.try_catch.FrameTryCatch} 当初也是同一个问题:
 * 它靠"自己留在续延上、body 压在上面"解决。这里照搬同一套:
 *
 * <pre>
 *   pushFrame(new FrameSwitchBody(受控值))     ← 留在下面
 *   pushFrame(new FrameEvaluate(case 体))      ← body 压在上面先跑
 * </pre>
 *
 * <p>于是 case 体运行期间,续延上一定挂着本帧 ✗ 图案就能顺着续延找到它、读出受控值 ✓
 *
 * <h2>嵌套</h2>
 * 找的是"最近的一个" ✗ 所以 switch 里套 switch 时取到的是<b>内层</b>的值 ✓
 * 与 {@code get_exception} 找最近 catch 的口径一致 ✓
 *
 * @param matched 被 switch 分派的那个值
 */
public record FrameSwitchBody(Iota matched) implements ContinuationFrame {

    /**
     * case 体跑完了:本帧只负责在运行期间携带受控值,现在可以退场。
     *
     * <p>返回一个"什么都不做"的结果 —— 压一个空列表当 iota 只是占位,
     * 它不会被消费;真正重要的是把续延原样交回去。
     */
    @Override
    public CastResult evaluate(SpellContinuation continuation, ServerLevel level, CastingVM vm) {
        return new CastResult(
                new ListIota(TreeList.empty()),
                continuation,
                vm.getImage().withUsedOp(),
                List.of(),
                ResolvedPatternType.EVALUATED,
                HexEvalSounds.NOTHING.get());
    }

    @Override
    public ContinuationFrame.Type<?> getType() {
        return TYPE;
    }

    /** 帧自身的开销 + 携带的那个 iota。 */
    @Override
    public int size() {
        return this.matched.size() + 1;
    }

    /** 本帧不参与栈的拆解,如实告诉 VM「我没动它」。 */
    @Override
    public Pair<Boolean, TreeList<Iota>> breakDownwards(TreeList<Iota> stack) {
        return new Pair<>(false, stack);
    }

    /**
     * 顺着续延找最近的、还挂着的本帧(供「凯斯之精思」用)。
     *
     * @return 不在任何 case 体里时为 null
     */
    public static @Nullable FrameSwitchBody findIn(SpellContinuation continuation) {
        SpellContinuation current = continuation;
        while (current instanceof SpellContinuation.NotDone notDone) {
            if (notDone.getFrame() instanceof FrameSwitchBody frame) {
                return frame;
            }
            current = notDone.getNext();
        }
        return null;
    }

    /**
     * 帧类型 —— 必须注册 ✗ 否则存档与网络同步都会失败(帧是会被写进法术书续延的)。
     */
    public static final ContinuationFrame.Type<FrameSwitchBody> TYPE = new ContinuationFrame.Type<>() {

        private final MapCodec<FrameSwitchBody> codec = RecordCodecBuilder.mapCodec(inst -> inst.group(
                IotaType.TYPED_CODEC.fieldOf("matched").forGetter(FrameSwitchBody::matched)
        ).apply(inst, FrameSwitchBody::new));

        private final StreamCodec<RegistryFriendlyByteBuf, FrameSwitchBody> streamCodec =
                StreamCodec.composite(
                        IotaType.TYPED_STREAM_CODEC, FrameSwitchBody::matched,
                        FrameSwitchBody::new);

        @Override
        public MapCodec<FrameSwitchBody> codec() {
            return this.codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FrameSwitchBody> streamCodec() {
            return this.streamCodec;
        }
    };
}
