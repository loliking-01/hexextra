package com.llk.hexextra.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 「当前是谁在写」的线程本地上下文。
 *
 * <p>{@code writeDatum} 的签名里没有玩家,所以由 {@code MixinOpWriteSpell} 在
 * {@code OpWrite$Spell.cast} 时把施法者放进这里,再由写入路径读出来。
 *
 * <p>带 {@code gameTime} 时效:注入的 RETURN 在异常时不执行,
 * 残留值最多活一个 tick 就自动失效,避免误记到无辜玩家头上。
 *
 * <p>异步线程没有上下文 → 不记录(宁可漏记,不可错记)。
 */
public final class WriterContext {

    private static final ThreadLocal<Pending> CURRENT = new ThreadLocal<>();

    private record Pending(ServerPlayer player, long gameTime) {
    }

    public static void set(@Nullable ServerPlayer player, long gameTime) {
        if (player == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(new Pending(player, gameTime));
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    @Nullable
    public static ServerPlayer get(MinecraftServer server) {
        Pending pending = CURRENT.get();
        if (pending == null) {
            return null;
        }
        if (pending.gameTime() != server.overworld().getGameTime()) {
            CURRENT.remove();
            return null;
        }
        return pending.player();
    }

    private WriterContext() {
    }
}