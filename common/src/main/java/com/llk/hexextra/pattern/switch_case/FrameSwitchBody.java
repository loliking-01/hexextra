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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import kotlin.Pair;

/**
 * switch 的执行帧 —— 在 case 体(含 default 体)运行期间携带<b>受控值</b>。
 *
 * <h2>为什么必须有这么一个帧</h2>
 * 「凯斯之精思」要在分支体里把"分派的那个值"取回来。而分支体现在是用原版的
 * {@code FrameEvaluate} 跑的 —— 它跑完就消失,没法回头查"刚才分派的是什么"。
 *
 * <p>{@link com.llk.hexextra.pattern.try_catch.FrameTryCatch} 当初也是同一个问题:
 * 它靠"自己留在续延上、body 压在上面"解决。这里照搬同一套:
 *
 * <pre>
 *   pushFrame(new FrameSwitchBody(受控值))     ← 留在下面
 *   pushFrame(new FrameEvaluate(分支体))       ← body 压在上面先跑
 * </pre>
 *
 * <h2>嵌套</h2>
 * 找的是"最近的一个",所以 switch 里套 switch 时取到的是<b>内层</b>的值,
 * 与 {@code get_exception} 找最近 catch 的口径一致。
 *
 * <h2>0.11.4 的序列化</h2>
 * 这个版本没有 Codec:帧自己实现 {@link #serializeToNBT()},
 * 类型只实现 {@code deserializeFromNBT}。
 *
 * @param matched 被 switch 分派的那个值
 */
public record FrameSwitchBody(Iota matched) implements ContinuationFrame {

    /**
     * 分支体跑完了:本帧只负责在运行期间携带受控值,现在可以退场。
     */
    @Override
    public CastResult evaluate(SpellContinuation continuation, ServerLevel level, CastingVM vm) {
        return new CastResult(
                new ListIota(TreeList.empty()),
                continuation,
                vm.getImage().withUsedOp(),
                List.of(),
                ResolvedPatternType.EVALUATED,
                HexEvalSounds.NOTHING);
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

    /** 本帧不参与栈的拆解。 */
    @Override
    @SuppressWarnings("unchecked")
    public Pair<Boolean, List<Iota>> breakDownwards(List<? extends Iota> stack) {
        return new Pair<>(false, (List<Iota>) stack);
    }

    @Override
    public @NotNull CompoundTag serializeToNBT() {
        var out = new CompoundTag();
        out.put("matched", IotaType.serialize(this.matched));
        return out;
    }

    /**
     * 顺着续延找最近的、还挂着的本帧(供「凯斯之精思」用)。
     *
     * @return 不在任何分支体里时为 null
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
     * 帧类型 —— 必须注册,否则存档与网络同步都会失败(帧会被写进法术书续延的)。
     */
    public static final ContinuationFrame.Type<FrameSwitchBody> TYPE = new ContinuationFrame.Type<>() {

        @Override
        public @NotNull FrameSwitchBody deserializeFromNBT(CompoundTag tag, ServerLevel world) {
            Iota matched = null;
            if (tag.contains("matched", Tag.TAG_COMPOUND)) {
                matched = IotaType.deserialize(tag.getCompound("matched"), world);
            }
            return new FrameSwitchBody(matched);
        }
    };
}
