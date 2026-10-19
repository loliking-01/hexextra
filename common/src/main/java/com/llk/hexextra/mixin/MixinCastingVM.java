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

/**
 * 让 {@code try_catch} 真正接住 {@code Mishap}。
 *
 * <h2>为什么挂在这里</h2>
 * 咒法学 VM 没有异常处理:每一步的报错都被 {@code executeInner} 折进
 * {@code CastResult} 的 {@link OperatorSideEffect.DoMishap} 副作用里。
 * 所以在 {@code executeInner} 的<b>出口</b>看一眼:结果里有没有 {@code DoMishap}?
 * 续延上有没有我们还开着的 {@link FrameTryCatch}?两者都有就把结果改写成"进入 catch",
 * 否则原样返回。
 *
 * <h2>为什么还需要 MixinTempControllerInfo</h2>
 * 光改写返回值不够 —— VM 循环末尾还会用
 * {@code earlyExit = earlyExit || !resolutionType.success} 决定要不要退出。
 * 那个闸由 {@code MixinTempControllerInfo} 拦住。
 *
 * <h2>兼容性</h2>
 * {@code require = 0} + 只在<b>我们自己的帧</b>还在续延上时才改写 → 与其它附属零冲突。
 */
@Mixin(CastingVM.class)
public class MixinCastingVM {

    /** 本项目的日志器(common 模块里没有共用日志器,就地建一个)。 */
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("hexextra");

    @Inject(method = "executeInner", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void hexextra$catchMishap(Iota iota, ServerLevel world, SpellContinuation continuation,
                                      CallbackInfoReturnable<CastResult> cir) {
        try {
            hexextra$tryRewrite(cir);
        } catch (Throwable thrown) {
            // 兜底:try_catch 的功能性失败绝不能把整个游戏带崩。
            // 最坏情况是这次报错没被接住(和没装本模组一样),而不是客户端退出。
            // 但仍然写日志 —— 否则这类问题无从排查。
            LOGGER.error("hexextra: try_catch 捕获失败,已跳过", thrown);
        }
    }

    private void hexextra$tryRewrite(CallbackInfoReturnable<CastResult> cir) {
        CastResult result = cir.getReturnValue();
        if (result == null) {
            return;
        }

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

        FrameTryCatch frame = FrameTryCatch.findNearest(result.getContinuation());
        if (frame == null) {
            return;
        }

        Mishap mishap = doMishap.getMishap();
        CastingVM self = (CastingVM) (Object) this;

        // 取"玩家会看到的文字"。0.11.4 里 errorMessageWithName 会用到 Mishap.Context
        // 的名字,而有些 DoMishap 的上下文是 (null, null) —— 那种情况可能抛异常。
        // 这里兜住:取不到就退回报错的类名,绝不能因为包装文字而崩掉整个游戏。
        net.minecraft.network.chat.Component message;
        try {
            message = mishap.errorMessageWithName(self.getEnv(), doMishap.getErrorCtx());
        } catch (RuntimeException ex) {
            message = net.minecraft.network.chat.Component.literal(mishap.getClass().getSimpleName());
        }
        if (message == null) {
            message = net.minecraft.network.chat.Component.literal(mishap.getClass().getSimpleName());
        }
        Iota errorIota = new ExceptionIota(message);

        cir.setReturnValue(frame.enterCatch(result.getContinuation(), self, errorIota));
    }
}