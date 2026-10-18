package com.llk.hexextra.pattern.for_loop;

import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.CastingImages;

import java.util.List;


/**
 * 「取当前次数」与「取剩余次数」两枚图案的行为。
 *
 * <h2>怎么找到"我在哪个循环里"</h2>
 * 续延 {@link SpellContinuation} 是一条公开可遍历的链表
 * ({@code NotDone(frame, next)})。图案执行时引擎把它交给 {@link Action#operate},
 * 于是从栈顶往下走,第一个遇到的 {@link FrameForLoop} 就是<b>最内层</b>的循环 ——
 * 嵌套循环因此天然正确,不需要额外的维护工作。
 *
 * <h2>计数口径</h2>
 * <ul>
 *   <li>{@code 取当前次数} 从 0 起:正序循环给出 {@code 0,1,…,n-1};
 *       倒序循环({@code for_down})给出 {@code n-1,…,1,0},即"计数反着记"。</li>
 *   <li>{@code 取剩余次数} 给的是<b>当前这一轮之后</b>还剩几轮:{@code n-1,…,0}。
 *       倒序循环里它自然变成 {@code 0,1,…,n-1}。</li>
 * </ul>
 *
 * <h2>不在循环里怎么办</h2>
 * 直接报 {@link com.llk.hexextra.core.HexextraMishaps.NoEnclosingLoop}。
 * 这两枚图案脱离循环没有任何合理语义,返回 0 或 null 只会掩盖玩家的结构错误。
 */
public final class LoopGetters {

    /** 沿续延链找最内层的 for 循环帧;找不到返回 null。 */
    private static FrameForLoop findLoop(SpellContinuation continuation) {
        SpellContinuation cursor = continuation;
        while (cursor instanceof SpellContinuation.NotDone notDone) {
            if (notDone.getFrame() instanceof FrameForLoop loop) {
                return loop;
            }
            cursor = notDone.getNext();
        }
        return null;
    }

    /**
     * 「取当前次数」—— 0 起;倒序循环里反着记。
     */
    public static final class GetIndex implements Action {

        @Override
        public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
            FrameForLoop loop = findLoop(continuation);
            if (loop == null) {
                throw new com.llk.hexextra.core.HexextraMishaps.NoEnclosingLoop();
            }
            int index = loop.reverse()
                    ? loop.remaining() - 1        // 倒着记:n-1 → 0
                    : loop.total() - loop.remaining();  // 正着记:0 → n-1
            return new OperationResult(
                    CastingImages.withStack(image.withUsedOp(), image.getStack().appended(new DoubleIota(index))),
                    List.<OperatorSideEffect>of(),
                    continuation,
                    HexEvalSounds.NORMAL_EXECUTE.get());
        }
    }

    /**
     * 「取剩余次数」—— 当前这一轮之后还剩几轮。
     */
    public static final class GetRemaining implements Action {

        @Override
        public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
            FrameForLoop loop = findLoop(continuation);
            if (loop == null) {
                throw new com.llk.hexextra.core.HexextraMishaps.NoEnclosingLoop();
            }
            int remaining = loop.remaining() - 1;
            return new OperationResult(
                    CastingImages.withStack(image.withUsedOp(), image.getStack().appended(new DoubleIota(remaining))),
                    List.<OperatorSideEffect>of(),
                    continuation,
                    HexEvalSounds.NORMAL_EXECUTE.get());
        }
    }

    private LoopGetters() {
    }
}
