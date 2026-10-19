package com.llk.hexextra.pattern.try_catch;

import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.CastingImages;
import com.llk.hexextra.core.HexextraMishaps;

import java.util.List;

/**
 * 「取出当前异常」的图案 —— <b>只在 catch 块里有效</b>。
 *
 * <h2>为什么必须是 SpellAction 而不是 ConstMediaAction</h2>
 * 判断"现在在不在 catch 里"要看<b>续延</b>:catch 块正在跑时,
 * 续延上挂着那个处于 stage 2/3 的 {@link FrameTryCatch}。
 * 而 {@code ConstMediaAction.execute(args, env)} 的签名里<b>没有续延</b>,
 * 只有 {@code SpellAction.operate(env, image, continuation)} 能拿到。
 *
 * <h2>压栈怎么做</h2>
 * {@code TreeList} 是不可变结构,但提供了 {@code appended(A)} ——
 * 返回"追加一个元素后的新列表",正是这里要的。
 */
public final class OpGetException implements SpellAction {

    @Override
    public int getArgc() {
        return 0;
    }

    /**
     * 本图案<b>不走</b>这条路:它覆写了 {@link #operate}。
     *
     * <p>保留实现只是为了满足接口要求,正常运行中不会被调用。
     */
    @Override
    public SpellAction.Result execute(List<? extends Iota> args, CastingEnvironment env) {
        throw new UnsupportedOperationException("get_exception 只在 operate 里执行");
    }

    @Override
    public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
        FrameTryCatch frame = FrameTryCatch.findInCatch(continuation);
        if (frame == null || frame.exception() == null) {
            // 不在任何 catch 块里:按设计报错,而不是悄悄返回空值
            throw new HexextraMishaps.NoActiveException();
        }

        CastingImage next = CastingImages.withStack(image, image.getStack().appended(frame.exception()));

        return new OperationResult(
                next,
                List.of(),
                continuation,
                HexEvalSounds.NORMAL_EXECUTE.get());
    }
}