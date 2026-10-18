package com.llk.hexextra.pattern.for_loop;

import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.CastingImages;
import com.llk.hexextra.pattern.for_each.FrameForEach;

import java.util.List;

/**
 * 三枚循环取值图案:取当前次数 / 取剩余次数 / 取当前元素。
 *
 * <h2>层数参数</h2>
 * 三枚图案都从栈上<b>消费一个数字</b>,表示"往外数第几层循环":
 * <pre>
 * 0  = 最内层(当前所在的那一层)
 * 1  = 外面一层
 * 2  = 再外面一层
 * ...
 * </pre>
 * 越界(层数大于实际嵌套层数)报 {@link com.llk.hexextra.core.HexextraMishaps.NoEnclosingLoop}。
 *
 * <p><b>计数口径</b>:只数<b>同类</b>的循环帧 —— {@code 取当前元素} 只数 foreach,
 * {@code 取当前次数} 只数 for,{@code 取剩余次数} 两者都数。
 * 这样"第 0 层"在每枚图案里都指"最近的那个我能用的循环",嵌套混用时不会错位。
 *
 * <h2>怎么找"第 n 层"</h2>
 * 续延 {@link SpellContinuation} 是一条公开可遍历的链表({@code NotDone(frame, next)})。
 * 从栈顶往下走,每遇到一个匹配的帧就把计数器加一,数到 {@code depth} 就返回 ——
 * 所以嵌套再深也只是多走几步,不需要额外的维护工作。
 *
 * <h2>计数口径(get_index / get_remaining)</h2>
 * <ul>
 *   <li>{@code 取当前次数} 从 0 起:正序循环给出 {@code 0,1,…,n-1};
 *       倒序循环({@code for_down})给出 {@code n-1,…,0}。</li>
 *   <li>{@code 取剩余次数} 给的是<b>当前这一轮之后</b>还剩几轮;
 *       在 foreach 里则是"还没开始遍历的元素个数"。</li>
 * </ul>
 */
public final class LoopGetters {

    // =======================================================================
    // 工具:取层数、找第 n 层循环帧
    // =======================================================================

    /**
     * 从栈顶取层数,并返回"消费掉它之后"的栈。
     *
     * <p>用一个小内部类返回两个值,避免为了一个临时容器多写一层。
     */
    private record DepthArg(int depth, TreeList<Iota> rest) {
    }

    /**
     * 取层数:栈顶是数字就"当成层数消费掉",否则<b>默认 0(当前层)且不动栈</b>。
     *
     * <p>这是一个可省略参数 —— 不压层数时三枚图案的行为与旧版完全一致,
     * 想取外层时才在栈顶压一个数字。
     *
     * <p><b>代价</b>:栈顶恰好有个数字(本来想留给别的图案)时会被吃掉。
     * 这是"可选参数"无法避免的歧义,按需求接受。
     */
    private static DepthArg popDepth(CastingImage image) {
        TreeList<Iota> stack = image.getStack();
        if (!stack.isEmpty() && stack.last() instanceof DoubleIota number) {
            // 负数当 0 处理(等价于"当前层")
            int depth = Math.max(0, (int) Math.floor(number.getDouble()));
            return new DepthArg(depth, stack.dropRight(1));
        }
        return new DepthArg(0, stack);
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
    private static FrameForEach findEach(SpellContinuation continuation, int depth) {
        int seen = 0;
        SpellContinuation cursor = continuation;
        while (cursor instanceof SpellContinuation.NotDone notDone) {
            if (notDone.getFrame() instanceof FrameForEach each) {
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
    private static OperationResult pushNumber(CastingImage image, TreeList<Iota> rest,
                                              int value, SpellContinuation continuation) {
        return new OperationResult(
                CastingImages.withStack(image.withUsedOp(), rest.appended(new DoubleIota(value))),
                List.<OperatorSideEffect>of(),
                continuation,
                HexEvalSounds.NORMAL_EXECUTE.get());
    }

    // =======================================================================
    // 取当前次数
    // =======================================================================

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
                    ? loop.remaining() - 1                      // 倒着记:n-1 → 0
                    : loop.total() - loop.remaining();          // 正着记:0 → n-1
            return pushNumber(image, arg.rest(), index, continuation);
        }
    }

    // =======================================================================
    // 取剩余次数
    // =======================================================================

    /** 「取剩余次数」—— 当前这一轮之后还剩几轮。 */
    public static final class GetRemaining implements Action {

        @Override
        public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
            DepthArg arg = popDepth(image);

            // 一个图案兼容两种循环:先找 for,再找 foreach
            FrameForLoop loop = findLoop(continuation, arg.depth());
            int remaining;
            if (loop != null) {
                remaining = loop.remaining();                   // 帧里存的就是「本轮之后还剩几轮」
            } else {
                FrameForEach each = findEach(continuation, arg.depth());
                if (each == null) {
                    throw new com.llk.hexextra.core.HexextraMishaps.NoEnclosingLoop();
                }
                remaining = each.pending().size();              // 还没开始遍历的元素个数
            }
            return pushNumber(image, arg.rest(), remaining, continuation);
        }
    }

    // =======================================================================
    // 取当前元素
    // =======================================================================

    /** 「取当前元素」—— 只能在 foreach 里用。 */
    public static final class GetCurrent implements Action {

        @Override
        public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
            DepthArg arg = popDepth(image);
            FrameForEach each = findEach(continuation, arg.depth());
            if (each == null) {
                throw new com.llk.hexextra.core.HexextraMishaps.NoEnclosingLoop();
            }
            return new OperationResult(
                    CastingImages.withStack(image.withUsedOp(), arg.rest().appended(each.current())),
                    List.<OperatorSideEffect>of(),
                    continuation,
                    HexEvalSounds.NORMAL_EXECUTE.get());
        }
    }

    private LoopGetters() {
    }
}
