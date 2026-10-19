package com.llk.hexextra.pattern.try_catch;

/**
 * 「下一次 VM 循环的 earlyExit 请忽略」的一次性标记。
 *
 * <p>{@code CastingVM.queueExecuteAndWrapIotas} 每个迭代末尾都会执行
 * {@code earlyExit = earlyExit || !resolutionType.success} ✗
 * 我们在 {@code executeInner} 出口把一次 Mishap 改写成"进入 catch"之后,
 * 循环不应该因为这次报错退出 —— 否则 catch 帧永远求值不到。
 *
 * <p>标记由 {@link FrameTryCatch#enterCatch} 置上,由 {@code MixinTempControllerInfo}
 * 在 {@code setEarlyExit} 处取走并取消。用 ThreadLocal 保证不会跨施法残留。
 */
public final class TryCatchExitGuard {

    private static final ThreadLocal<Boolean> PENDING = new ThreadLocal<>();

    public static void request() {
        PENDING.set(Boolean.TRUE);
    }

    /** 取走标记:true 表示"这次要忽略"。取走后自动清除。 */
    public static boolean consume() {
        boolean pending = Boolean.TRUE.equals(PENDING.get());
        if (pending) {
            PENDING.remove();
        }
        return pending;
    }

    private TryCatchExitGuard() {
    }
}