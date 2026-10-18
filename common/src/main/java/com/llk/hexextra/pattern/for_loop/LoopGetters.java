package com.llk.hexextra.pattern.for_loop;

import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.CastingImages;

import java.util.ArrayList;
import java.util.List;

/**
 * 三枚循环取值图案:取当前次数 / 取剩余次数 / 取当前元素。
 *
 * <h2>层数参数(0.11.4 版)</h2>
 * 三枚图案都从栈上<b>消费一个数字</b>,表示"往外数第几层循环":
 * <pre>
 * 0  = 最内层(当前所在的那一层)
 * 1  = 外面一层
 * ...
 * </pre>
 * 越界报 {@link com.llk.hexextra.core.HexextraMishaps.NoEnclosingLoop}。
 *
 * <p><b>计数口径</b>:只数<b>同类</b>的循环帧 —— {@code 取当前元素} 只数 foreach,
 * {@code 取当前次数} 只数 for,{@code 取剩余次数} 两者都数。
 *
 * <h2>0.11.4 与 1.21.1 的差异</h2>
 * <ul>
 *   <li>{@code CastingImage.getStack()} 返回 <b>{@code List<Iota>}</b>,不是 {@code TreeList}
 *       —— 没有 {@code last()} / {@code dropRight()} / {@code appended()} 可用,只能手抄集合;</li>
 *   <li>{@code HexEvalSounds} 的常量是普通静态字段(<b>没有</b> {@code .get()})。</li>
 * </ul>
 */
public final class LoopGetters {

    /** 从栈顶取层数,并返回"消费掉它之后"的栈。 */
    private record DepthArg(int depth, List<Iota> rest) {
    }

    private static DepthArg popDepth(CastingImage image) {
        List<Iota> stack = image.getStack();
        if (stack.isEmpty()) {
            throw new MishapNotEnoughArgs(1, 0);
        }
        Iota top = stack.get(stack.size() - 1);
        if (!(top instanceof DoubleIota number)) {
            throw MishapInvalidIota.of(top, 0, "double");
        }
        // 负数当 0 处理(等价于"当前层")
        int depth = Math.max(0, (int) Math.floor(number.getDouble()));
        List<Iota> rest = new ArrayList<>(stack.subList(0, stack.size() - 1));
        return new DepthArg(depth, rest);
    }

    /** 沿续延链找第 {@code depth} 层的 for 循环帧;找不到返回 null。 */
    private static FrameForLoop findLoop(SpellContinuation continuation, int depth) {
        int seen = 0;
        SpellContinuation cursor = continuation;
        while (cursor instanceof SpellContinuation.NotDone notDone) {
            if (notDone.getFrame() instanceof FrameForLoop loop) {
                if (seen == depth) {
                    return loop;
                }
                seen++;
            }
            cursor = notDone.getNext();
        }
        return null;
    }

    /** 沿续延链找第 {@code depth} 层的 foreach 帧;找不到返回 null。 */
    private static com.llk.hexextra.pattern.for_each.FrameForEach findEach(SpellContinuation continuation, int depth) {
        int seen = 0;
        SpellContinuation cursor = continuation;
        while (cursor instanceof SpellContinuation.NotDone notDone) {
            if (notDone.getFrame() instanceof com.llk.hexextra.pattern.for_each.FrameForEach each) {
                if (seen == depth) {
                    return each;
                }
                seen++;
            }
            cursor = notDone.getNext();
        }
        return null;
    }

    /** 打包结果:栈上压一个新数字。 */
    private static OperationResult pushNumber(CastingImage image, List<Iota> rest,
                                              int value, SpellContinuation continuation) {
        List<Iota> newStack = new ArrayList<>(rest);
        newStack.add(new DoubleIota(value));
        return new OperationResult(
                CastingImages.withStack(image.withUsedOp(), newStack),
                List.<OperatorSideEffect>of(),
                continuation,
                HexEvalSounds.NORMAL_EXECUTE);
    }

    /** 「取当前次数」—— 0 起;倒序循环里反着记。 */
    public static final class GetIndex implements Action {

        @Override
        public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
            DepthArg arg = popDepth(image);
            FrameForLoop loop = findLoop(continuation, arg.depth());
            if (loop == null) {
                throw new com.llk.hexextra.core.HexextraMishaps.NoEnclosingLoop();
            }
            int index = loop.reverse()
                    ? loop.remaining() - 1
                    : loop.total() - loop.remaining();
            return pushNumber(image, arg.rest(), index, continuation);
        }
    }

    /** 「取剩余次数」—— 当前这一轮之后还剩几轮。 */
    public static final class GetRemaining implements Action {

        @Override
        public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
            DepthArg arg = popDepth(image);

            FrameForLoop loop = findLoop(continuation, arg.depth());
            int remaining;
            if (loop != null) {
                remaining = loop.remaining();
            } else {
                com.llk.hexextra.pattern.for_each.FrameForEach each = findEach(continuation, arg.depth());
                if (each == null) {
                    throw new com.llk.hexextra.core.HexextraMishaps.NoEnclosingLoop();
                }
                remaining = each.pending().size();
            }
            return pushNumber(image, arg.rest(), remaining, continuation);
        }
    }

    /** 「取当前元素」—— 只能在 foreach 里用。 */
    public static final class GetCurrent implements Action {

        @Override
        public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
            DepthArg arg = popDepth(image);
            com.llk.hexextra.pattern.for_each.FrameForEach each = findEach(continuation, arg.depth());
            if (each == null) {
                throw new com.llk.hexextra.core.HexextraMishaps.NoEnclosingLoop();
            }
            List<Iota> newStack = new ArrayList<>(arg.rest());
            newStack.add(each.current());
            return new OperationResult(
                    CastingImages.withStack(image.withUsedOp(), newStack),
                    List.<OperatorSideEffect>of(),
                    continuation,
                    HexEvalSounds.NORMAL_EXECUTE);
        }
    }

    private LoopGetters() {
    }
}