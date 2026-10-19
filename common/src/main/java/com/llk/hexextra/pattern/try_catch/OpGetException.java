package com.llk.hexextra.pattern.try_catch;

import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.CastingImages;
import com.llk.hexextra.core.HexextraMishaps;

import java.util.ArrayList;
import java.util.List;

/**
 * 「艾若儿之精思」—— <b>只在 catch 块里有效</b>。
 *
 * <h2>为什么必须是 Action 而不是 ConstMediaAction</h2>
 * 判断"现在在不在 catch 里"要看<b>续延</b>:catch 块正在跑时,
 * 续延上挂着处于 stage 2/3 的 {@link FrameTryCatch}。
 * 只有 {@code Action.operate(env, image, continuation)} 能拿到续延。
 *
 * <h2>0.11.4 的差异</h2>
 * 栈是 {@code List<Iota>} ✗ 往末尾加一个值就是"压栈" ✗
 * 再交给 {@code CastingImages.withStack} 写回 image ✓
 */
public final class OpGetException implements Action {

    @Override
    public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation)
            throws Mishap {
        FrameTryCatch frame = FrameTryCatch.findInCatch(continuation);
        if (frame == null || frame.exception() == null) {
            throw new HexextraMishaps.NoActiveException();
        }

        List<Iota> next = new ArrayList<>(image.getStack());
        next.add(frame.exception());

        return new OperationResult(
                CastingImages.withStack(image, next),
                List.of(),
                continuation,
                HexEvalSounds.NORMAL_EXECUTE);
    }
}