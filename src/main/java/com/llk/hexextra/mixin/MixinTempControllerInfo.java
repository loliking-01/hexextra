package com.llk.hexextra.mixin;

import com.llk.hexextra.pattern.try_catch.TryCatchExitGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 VM 循环在"我们刚把一次报错改成进入 catch"之后<b>不要退出</b>。
 *
 * <h2>目标为什么用 targets 字符串</h2>
 * {code CastingVM$TempControllerInfo} 是包私有的内部类,我们的包引用不到,
 * 只能写全名。
 *
 * <h2>为什么干脆取消整个 setter</h2>
 * {code setEarlyExit} 只在循环末尾被调用一次,参数是
 * {code earlyExit || !resolutionType.success}。取消它等于保留原来的值(false),
 * 循环自然会继续到我们压入的 catch 帧。
 *
 * {code require = 0}:上游改签名时静默失效,不会崩。
 */
@Mixin(targets = "at.petrak.hexcasting.api.casting.eval.vm.CastingVM$TempControllerInfo")
public class MixinTempControllerInfo {

    @Inject(method = "setEarlyExit", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void hexextra$swallowEarlyExit(boolean earlyExit, CallbackInfo ci) {
        if (TryCatchExitGuard.consume()) {
            ci.cancel();
        }
    }
}