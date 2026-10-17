package com.llk.hexextra.pattern.for_loop;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;

import java.util.ArrayList;
import java.util.List;

/**
 * 「佛尔之策略」—— for 循环。
 *
 * <h2>语义</h2>
 * 从栈上取「次数」和「代码块」,把代码块重复跑该次数。循环本身不耗媒质;
 * 次数 ≤ 0 时什么都不做;上限 {@value #MAX_ITERATIONS} 次(防止一次施法跑太久把服务器拖死)。
 *
 * <h2>为什么是 {@link Action} 而不是 {@code ConstMediaAction}</h2>
 * 因为它<b>要压续延帧</b>。{@code ConstMediaAction} 的 {@code operate} 是固定的
 * "取参数 → 执行 → 把结果压回栈",没法动续延栈;所以这里直接实现 {@link Action}。
 *
 * <h2>为什么不用 {@code ConstMediaAction} 的媒质消耗</h2>
 * 循环次数在运行时才知道,不是固定费用 —— 而且我们刻意让它免费(循环本身不耗媒质,
 * 循环体里画的图案才耗)。
 */
public final class OpForLoop implements Action {

    /** 次数上限。防止误用把服务器单次施法卡死。 */
    public static final int MAX_ITERATIONS = 10_000;

    @Override
    public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
        List<Iota> stack = image.getStack();
        if (stack.size() < 2) {
            throw new MishapNotEnoughArgs(2, stack.size());
        }

        // 栈顶是代码块,它下面那个是次数
        Iota bodyIota = stack.get(stack.size() - 1);
        Iota countIota = stack.get(stack.size() - 2);

        // 用 OperatorUtils 的取值助手:非数值会抛 MishapInvalidIota,
        // 且报错会定位到"第几个参数",比手写判断更清楚
        @SuppressWarnings("unchecked")
        List<Iota> args = (List<Iota>) stack;
        int requested = OperatorUtils.getInt(args, args.size() - 2, 2);
        TreeList<Iota> body = TreeList.from(OperatorUtils.getList(args, args.size() - 1, 2));

        // 负数按 0 处理(与 1.21.1 版一致);超过上限则截断
        int iterations = Math.max(0, Math.min(requested, MAX_ITERATIONS));

        // 两个参数都消费掉
        List<Iota> newStack = new ArrayList<>(stack.subList(0, stack.size() - 2));

        CastingImage newImage = image.withUsedOp().copy(newStack,
                image.getParenCount(),
                image.getParenthesized(),
                image.getEscapeNext(),
                image.getOpsConsumed(),
                image.getUserData());

        // 次数 > 0 才压帧;否则循环不执行(但仍然消费掉了参数)
        SpellContinuation next = continuation;
        if (iterations > 0) {
            next = next.pushFrame(new FrameForLoop(body, iterations));
        }

        return new OperationResult(
                newImage,
                List.<OperatorSideEffect>of(),
                next,
                iterations > 0 ? HexEvalSounds.HERMES : HexEvalSounds.NORMAL_EXECUTE);
    }
}
