package com.llk.hexextra.pattern.switch_case;

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
 * 「凯斯之精思」—— 取回当前 switch <b>正在分派的那个值</b>。
 *
 * <h2>只在 case 体 / default 体里有效</h2>
 * 分派值在进入 switch 时就从栈上被消耗掉了 ✗ 本图案把它<b>再压回去一份</b> ✓
 * 所以在任何一节分支里都能拿到它去做别的事,比如:
 *
 * <pre>
 *   switch(x) {
 *     case 1 -> { 取出来 + 1 }     ← 拿到的是 x
 *     default -> { 取出来 }         ← 同样拿得到(default 也算分支)
 *   }
 * </pre>
 *
 * <h2>为什么必须是 SpellAction</h2>
 * 判断"现在在不在某条分支里"要看<b>续延</b>:分支运行期间,续延上挂着
 * {@link FrameSwitchBody}。而 {@code ConstMediaAction} 的签名里没有续延。
 *
 * <h2>取不到就报错</h2>
 * 在 switch 外面用它 ✗ 抛 {@code NoActiveSwitch} ✗ 而不是悄悄返回 null ✗
 * 后者会让"忘了包一层 switch"这种错误变成难查的数据问题 ✓
 */
public final class OpGetSwitchValue implements SpellAction {

    @Override
    public int getArgc() {
        return 0;
    }

    /**
     * 本图案<b>不走</b>这条路:它覆写了 {@link #operate}。
     * 保留实现只为满足接口要求。
     */
    @Override
    public SpellAction.Result execute(List<? extends Iota> args, CastingEnvironment env) {
        throw new UnsupportedOperationException("case_getvalue 只在 operate 里执行");
    }

    @Override
    public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
        FrameSwitchBody frame = FrameSwitchBody.findIn(continuation);
        if (frame == null) {
            throw new HexextraMishaps.NoActiveSwitch();
        }

        CastingImage next = CastingImages.withStack(image, image.getStack().appended(frame.matched()));

        return new OperationResult(
                next,
                List.of(),
                continuation,
                HexEvalSounds.NORMAL_EXECUTE.get());
    }
}
