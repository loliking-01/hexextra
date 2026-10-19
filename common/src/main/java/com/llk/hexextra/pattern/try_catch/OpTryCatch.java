package com.llk.hexextra.pattern.try_catch;

import at.petrak.hexcasting.api.casting.SpellList;
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

import java.util.ArrayList;
import java.util.List;

/**
 * 「崔凯之策略」—— <b>直接执行</b>,不产出 iota。
 *
 * <h2>0.11.4 的差异</h2>
 * <ul>
 *   <li>{@code CastingImage.getStack()} 返回 {@code List<Iota>}(不是 TreeList)</li>
 *   <li>{@code ListIota.getList()} 返回 {@link SpellList} —— 那是 car/cdr 链表 ✗
 *       不是 {@code List} ✗ 所以按 {@code getAt(i)} 取元素、按迭代器数个数</li>
 *   <li>音效常量是普通值,没有 {@code .get()}</li>
 * </ul>
 *
 * <h2>栈约定</h2>
 * 栈顶必须是一个列表,里面放 2 或 3 个代码块(依次 try、catch、可选 finally),
 * 读完后把那个列表从栈上弹掉。
 */
public final class OpTryCatch implements Action {

    @Override
    public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation)
            throws Mishap {
        List<Iota> stack = image.getStack();
        if (stack.isEmpty()) {
            throw new HexextraMishaps.TryCatchBlocks(0);
        }

        Iota top = stack.get(stack.size() - 1);
        if (!(top instanceof ListIota list)) {
            throw new HexextraMishaps.TryCatchBlocks(0);
        }

        SpellList blocks = list.getList();
        int count = countOf(blocks);
        if (count < 2 || count > 3) {
            throw new HexextraMishaps.TryCatchBlocks(count);
        }

        TreeList<Iota> tryBody = asBlock(blocks.getAt(0));
        TreeList<Iota> catchBody = asBlock(blocks.getAt(1));
        TreeList<Iota> finallyBody = count == 3 ? asBlock(blocks.getAt(2)) : null;

        // 弹出那个列表
        List<Iota> rest = new ArrayList<>(stack.subList(0, stack.size() - 1));
        CastingImage next = CastingImages.withStack(image, rest);

        return new OperationResult(
                next,
                List.of(),
                continuation.pushFrame(new FrameTryCatch(tryBody, catchBody, finallyBody, 0, null)),
                HexEvalSounds.NORMAL_EXECUTE);
    }

    /** SpellList 是链表,没有 size() —— 数一遍。 */
    private static int countOf(SpellList list) {
        int n = 0;
        for (Iota ignored : list) {
            n++;
        }
        return n;
    }

    /** 把一个元素当成代码块取出来;不是列表就当空块。 */
    private static TreeList<Iota> asBlock(Iota iota) {
        return iota instanceof ListIota list ? TreeList.from(list.getList()) : TreeList.empty();
    }
}