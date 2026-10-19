package com.llk.hexextra.mixin;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import com.llk.hexextra.pattern.try_catch.ExceptionIota;
import com.llk.hexextra.pattern.try_catch.FrameTryCatch;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 让 {@code try_catch} 真正接住 {@code Mishap}。
 *
 * <h2>为什么挂在这里</h2>
 * 咒法学 VM <b>没有</b>异常处理:每一步的报错都被 {@code executeInner} 折进
 * {@code CastResult} 的 {@link OperatorSideEffect.DoMishap} 副作用里,
 * 然后由外层决定是否中断整个法术。
 *
 * <p>所以只要在 {@code executeInner} 的<b>出口</b>看一眼:结果里有没有 {@code DoMishap}?
 * 续延上有没有我们还开着的 {@link FrameTryCatch}?两者都有,就把结果改写成
 * "进入 catch";否则<b>原样返回</b>。
 *
 * <h2>兼容性</h2>
 * <ul>
 *   <li>{@code require = 0} —— 咒法学改签名时本功能静默失效,<b>不会崩</b>;</li>
 *   <li>只在<b>我们自己的帧</b>还在续延上时才改写 —— 与其它附属零冲突。</li>
 * </ul>
 */
@Mixin(CastingVM.class)
public class MixinCastingVM {

    @Inject(method = "executeInner", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void hexextra$catchMishap(Iota iota, ServerLevel world, SpellContinuation continuation,
                                      CallbackInfoReturnable<CastResult> cir) {
        CastResult result = cir.getReturnValue();
        if (result == null) {
            return;
        }

        // 这一步抛错了吗?
        OperatorSideEffect.DoMishap doMishap = null;
        for (OperatorSideEffect effect : result.getSideEffects()) {
            if (effect instanceof OperatorSideEffect.DoMishap dm) {
                doMishap = dm;
                break;
            }
        }
        if (doMishap == null) {
            return;
        }

        // 续延上还有我们开着的 try 帧吗?
        FrameTryCatch frame = FrameTryCatch.findNearest(result.getContinuation());
        if (frame == null) {
            return;
        }

        // 把报错文字包成异常 iota,再改写结果
        Mishap mishap = doMishap.getMishap();
        var env = ((CastingVM) (Object) this).getEnv();
        Iota errorIota = new ExceptionIota(mishap.errorMessageWithName(env, doMishap.getErrorCtx()));

        cir.setReturnValue(frame.enterCatch(result.getContinuation(),
                (CastingVM) (Object) this, errorIota));
    }
}