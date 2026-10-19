package com.llk.hexextra.core;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 「当前是谁在写」的线程本地上下文。
 *
 * <h2>为什么需要它</h2>
 * {@code IotaHolderItem.writeDatum(ItemStack, Iota)} 的签名里<b>没有玩家</b>,
 * 而真正知道施法者的是上层图案 {@code OpWrite} 的 {@code Spell.cast(CastingEnvironment)}。
 * 所以要记录"谁写的",只能在那个方法里把玩家塞进这个上下文 ✗
 * 再由 {@code writeDatum} 读出来。
 *
 * <h2>为什么用 ThreadLocal</h2>
 * 施法与物品写入都在服务端线程上同步完成 ✗ 没有跨线程/异步风险 ✗
 * ThreadLocal 是最轻的做法,也不会被别的玩家干扰。
 */
public final class WriterContext {

    private static final ThreadLocal<ServerPlayer> CURRENT = new ThreadLocal<>();

    public static void set(@Nullable ServerPlayer player) {
        if (player == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(player);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    @Nullable
    public static ServerPlayer get() {
        return CURRENT.get();
    }

    private WriterContext() {
    }
}