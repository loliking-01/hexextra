package com.llk.hexextra.pattern.for_each;

import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.CastingImages;

import java.util.List;

/**
 * 「群体遍历之策略」—— 本项目自建的 foreach,与托特是两套东西。
 *
 * <h2>栈布局</h2>
 * 与托特一致:底部是<b>要遍历的列表</b>,顶部是<b>循环体代码块</b>。执行后两者都被消费。
 *
 * <h2>与托特的区别</h2>
 * 托特每轮把元素压进栈;本图案<b>不压</b> —— 循环体里要取值必须画
 * {@code hexextra:get_current}。这样循环体不会凭空多出一个栈值。
 *
 * <h2>为什么不用 {@code SpellAction}</h2>
 * 循环必须靠续延帧才能跑在 VM 里,而 {@code SpellAction} 的默认 {@code operate}
 * 没有地方安插自定义帧。所以和 {@code OpForLoop} 一样直接实现最底层的 {@link Action}。
 */
public class OpForEach implements Action {

    /** 从栈上消费几个参数:列表 + 代码块。 */
    private static final int ARGC = 2;

    @Override
    public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation)
            throws Mishap {

        TreeList<Iota> stack = image.getStack();
        if (stack.size() < ARGC) {
            throw new MishapNotEnoughArgs(ARGC, stack.size());
        }

        int n = stack.size();
        Iota blockIota = stack.get(n - 1);
        Iota listIota = stack.get(n - 2);

        if (!(blockIota instanceof ListIota block)) {
            throw MishapInvalidIota.of(blockIota, 0, "list");
        }
        if (!(listIota instanceof ListIota list)) {
            throw MishapInvalidIota.of(listIota, 1, "list");
        }

        TreeList<Iota> pending = TreeList.from(list.getList());
        TreeList<Iota> newStack = stack.dropRight(ARGC);

        // 空列表:什么都不跑。仍然算消耗一次操作,并把两个参数消费掉。
        if (pending.isEmpty()) {
            return new OperationResult(
                    CastingImages.withStack(image.withUsedOp(), newStack),
                    List.<OperatorSideEffect>of(),
                    continuation,
                    HexEvalSounds.NOTHING.get());
        }

        // 压入循环帧,并把当前元素记为第一个 —— 循环体的第一轮就能用「取当前元素」读到它。
        SpellContinuation withLoop = continuation.pushFrame(
                new FrameForEach(block.getList(), pending.tail(), pending.head()));

        return new OperationResult(
                CastingImages.withStack(image.withUsedOp(), newStack),
                List.<OperatorSideEffect>of(),
                withLoop,
                HexEvalSounds.THOTH.get());
    }
}
