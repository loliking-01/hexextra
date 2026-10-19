package com.llk.hexextra.pattern.try_catch;

import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.CastingImages;
import com.llk.hexextra.core.HexextraMishaps;

import java.util.List;

/**
 * 「崔凯之策略」—— <b>直接执行</b>,不产出 iota。
 *
 * <h2>为什么不打包成 iota</h2>
 * try/catch 是控制流,它的语义是"现在就开始跑 try 块"。
 * 如果先打包成一个 iota,玩家还得再画一次「赫尔墨斯之策略」才真正执行 ——
 * 多一步、且中间那个值没地方放。所以这里直接在 {@link #operate} 里压帧,
 * 和 {@code for_loop} / {@code if_chain} 的执行方式一致。
 *
 * <h2>栈约定</h2>
 * 栈顶必须是一个列表,里面放 2 或 3 个代码块(依次 try、catch、可选 finally)。
 * 读完后把列表本身从栈上弹掉。
 */
public final class OpTryCatch implements Action {

    @Override
    public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
        TreeList<Iota> stack = image.getStack();
        if (stack.isEmpty()) {
            throw new HexextraMishaps.TryCatchBlocks(0);
        }

        Iota top = stack.last();
        if (!(top instanceof ListIota list)) {
            throw new HexextraMishaps.TryCatchBlocks(0);
        }

        TreeList<Iota> blocks = list.getList();
        if (blocks.size() < 2 || blocks.size() > 3) {
            throw new HexextraMishaps.TryCatchBlocks(blocks.size());
        }

        TreeList<Iota> tryBody = asBlock(blocks.get(0));
        TreeList<Iota> catchBody = asBlock(blocks.get(1));
        TreeList<Iota> finallyBody = blocks.size() == 3 ? asBlock(blocks.get(2)) : null;

        // 弹出那个列表
        CastingImage next = CastingImages.withStack(image, stack.dropRight(1));

        return new OperationResult(
                next,
                List.of(),
                continuation.pushFrame(new FrameTryCatch(tryBody, catchBody, finallyBody, 0, null)),
                HexEvalSounds.NORMAL_EXECUTE.get());
    }

    /** 把一个元素当成代码块取出来;不是列表就当空块(和其它控制流图案同一口径)。 */
    private static TreeList<Iota> asBlock(Iota iota) {
        return iota instanceof ListIota list ? list.getList() : TreeList.empty();
    }
}