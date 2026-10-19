package com.llk.hexextra.mixin;

import com.llk.hexextra.pattern.try_catch.TryCatchExitGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 VM 循环在"我们刚把一次报错改成进入 catch"之后<b>不要退出</b>。
 *
 * <p>目标是 {@code CastingVM} 的内部类 {@code TempControllerInfo}(每轮循环新建一个)。
 *
 * <p>取消整个 {@code setEarlyExit} 等于保留原来的值(false),循环自然会继续到
 * 我们压入的 catch 帧。{@code require = 0}:上游改签名时静默失效,不会崩。
 */
@Mixin(at.petrak.hexcasting.api.casting.eval.vm.CastingVM.TempControllerInfo.class)
public class MixinTempControllerInfo {

    @Inject(method = "setEarlyExit", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void hexextra$swallowEarlyExit(boolean earlyExit, CallbackInfo ci) {
        try {
            if (TryCatchExitGuard.consume()) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // 兜底:这里出错也不该崩,最多是这次循环提前退出。
        }
    }
}