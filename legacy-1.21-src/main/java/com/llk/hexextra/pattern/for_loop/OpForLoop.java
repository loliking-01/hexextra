package com.llk.hexextra.pattern.for_loop;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.CastingImages;
import com.mojang.datafixers.util.Either;

import java.util.List;

/**
 * 「for 循环」操作符:把一段代码块在同一个栈上连续执行 N 次。
 *
 * <h2>玩家怎么用</h2>
 * 按顺序往栈上压两个东西(后压的在栈顶):
 * <ol>
 *   <li>{@code n} —— 执行次数(整数)</li>
 *   <li>{@code x} —— 要重复执行的代码块(一个列表,或单个可执行图案)</li>
 * </ol>
 * 然后画出图案。效果等价于 Java 的:
 * <pre>
 *   for (int i = 0; i < n; i++) { ...x... }
 * </pre>
 * 且循环体各轮之间<b>共用同一个栈</b> —— 第 i 轮的产出就是第 i+1 轮的输入。
 *
 * <h2>为什么直接实现 Action 而不是 SpellAction</h2>
 * {@code SpellAction} 的默认 {@code operate} 只负责「弹参数 → 调 execute → 压结果」,
 * 它没有地方安插自定义续延帧,而循环恰恰<i>必须</i>靠续延帧才能跑在 VM 里。
 * 所以这里和咒法学自己的 {@code OpEval} 一样,直接实现最底层的 {@link Action}。
 */
public class OpForLoop implements Action {

    /**
     * 循环次数上限。
     *
     * <p>咒法学引擎每个 tick 的总算力预算 {@code HexConfig.server().maxOpCount()} 默认为 100000,
     * 超出会抛 {@code MishapEvalTooMuch}。这里取它的十分之一作为循环次数的上限,
     * 好处是:给循环体本身留足 90% 的预算,而且报错信息比「算力耗尽」更清楚地指向"次数太大"。
     */
    public static final int MAX_ITERATIONS = 10_000;

    /** 本操作符从栈上消费几个参数:n 和 x。 */
    private static final int ARGC = 2;

    @Override
    public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation)
            throws Mishap {

        TreeList<Iota> stack = image.getStack();

        // 栈里至少要有一个 n 和一个 x
        if (stack.size() < ARGC) {
            throw new MishapNotEnoughArgs(ARGC, stack.size());
        }

        // 栈序是用户定的「先压 n、再压 x」,也就是 x 在栈顶、n 在它下面。
        // 咒法学惯例:下标 stack.size()-1 是栈顶。第三个参数 argc 只用于生成报错文案。
        int counterIdx = stack.size() - ARGC;
        int blockIdx = stack.size() - 1;

        int iterations = OperatorUtils.getInt(stack, counterIdx, ARGC);
        Either<Iota, TreeList<Iota>> rawBlock = OperatorUtils.getEvaluatable(stack, blockIdx, ARGC);

        if (iterations > MAX_ITERATIONS) {
            // 复用咒法学自带的本地化文案 "a positive integer less than or equal to %d",
            // 这样七种语言的提示都是现成的,不用自己写语言文件。
            // 倒数下标算法与 OperatorUtils 内部保持一致:argc - (下标 + 1)。
            int reverseIdx = ARGC - (counterIdx + 1);
            throw MishapInvalidIota.of(stack.get(counterIdx), reverseIdx, "int.positive.less.equal", MAX_ITERATIONS);
        }

        // 先把两个参数弹掉。剩下的栈就是循环全程使用的那一个栈。
        CastingImage popped = CastingImages.withStack(image, stack.dropRight(ARGC));

        if (iterations <= 0) {
            // 次数 <= 0:一次都不跑(与 Java 的 for 循环一致),直接结束,不压任何帧。
            return new OperationResult(popped, List.of(), continuation, HexEvalSounds.THOTH.get());
        }

        // 把「可求值对象」统一成一段代码:
        //   单个可执行 iota(例如一个图案)→ 包成单元素列表
        //   列表                          → 它本身就是代码块
        // 这正是 getEvaluatable 返回 Either 的含义,与 OpEval / FrameForEach 的处理方式一致。
        TreeList<Iota> code = rawBlock.map(
                single -> TreeList.<Iota>from(List.of(single)),
                block -> block);

        // 把循环帧挂到续延上。之后 VM 每弹一次这个帧就跑一轮。
        SpellContinuation withLoop = continuation.pushFrame(new FrameForLoop(code, iterations));

        // 本操作符自身不扣媒质(用户定:循环本身不收费,媒质由循环体里的操作符各自结算),
        // 所以这里没有任何 OperatorSideEffect。
        return new OperationResult(popped, List.of(), withLoop, HexEvalSounds.THOTH.get());
    }
}