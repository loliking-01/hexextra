package com.llk.hexextra.pattern.try_catch;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.utils.TreeList;
import com.llk.hexextra.core.HexextraMishaps;

import java.util.List;

/**
 * 「try_catch」图案:把「2 或 3 个代码块组成的列表」变成可执行的 {@link TryCatchIota}。
 *
 * <h2>为什么是"一个列表"而不是"两三个参数"</h2>
 * 咒法学图案的参数个数是<b>固定</b>的({@code getArgc()} 返回常量),
 * 做不到"2 个也行、3 个也行"。所以和 {@code if_else} 的造链图案一样,
 * 这里吃<b>一个</b> {@code ListIota}:前两个元素是 try / catch,第三个(可选)是 finally。
 */
public final class OpTryCatch implements ConstMediaAction {

    private static final int ARGC = 1;

    @Override
    public int getArgc() {
        return ARGC;
    }

    @Override
    public long getMediaCost() {
        return 0L;
    }

    @Override
    public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) throws Mishap {
        Iota arg = args.get(0);
        if (!(arg instanceof ListIota list)) {
            throw new HexextraMishaps.TryCatchBlocks(0);
        }

        TreeList<Iota> blocks = list.getList();
        if (blocks.size() < 2 || blocks.size() > 3) {
            throw new HexextraMishaps.TryCatchBlocks(blocks.size());
        }

        // OperatorUtils.getList(列表, 下标, argc) 会取出该下标上的 ListIota 的代码块
        int argc = blocks.size();
        TreeList<Iota> tryBody = OperatorUtils.getList(blocks, 0, argc);
        TreeList<Iota> catchBody = OperatorUtils.getList(blocks, 1, argc);
        TreeList<Iota> finallyBody = argc == 3 ? OperatorUtils.getList(blocks, 2, argc) : null;

        return List.of(new TryCatchIota(tryBody, catchBody, finallyBody));
    }
}