package com.llk.hexextra.core;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * 当前服务端实例的持有者。
 *
 * <h2>为什么需要它</h2>
 * {@code common} 模块同时被 Forge 与 Fabric 使用,<b>不能</b>直接引用任何一方的
 * 服务端生命周期 API(Forge 的 {@code ServerLifecycleHooks} / Fabric 的
 * {@code ServerLifecycleEvents})。所以由两个 loader 在启停时把实例放进这里,
 * 公共代码只读这个字段。
 *
 * <p>客户端永远读不到它(为 null),调用方据此跳过"只在服务端有意义"的落盘动作。
 */
public final class ServerHolder {

    private static MinecraftServer current;

    public static void set(@Nullable MinecraftServer server) {
        current = server;
    }

    @Nullable
    public static MinecraftServer get() {
        return current;
    }

    private ServerHolder() {
    }
}