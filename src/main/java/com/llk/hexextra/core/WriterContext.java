package com.llk.hexextra.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 「当前是谁在写」的线程本地上下文。
 *
 * <h2>为什么需要它</h2>
 * {@code IotaHolderItem.writeDatum(ItemStack, Iota)} 的签名里<b>没有玩家</b>,
 * 而真正知道施法者的是上层图案 {@code OpWrite} 的 {@code Spell.cast(CastingEnvironment)}。
 * 所以在那里把玩家塞进这个上下文,再由写入路径读出来。
 *
 * <h2>为什么带 tick 时效</h2>
 * {@code @Inject(at = RETURN)} <b>只有正常返回才会执行</b> ✗ 一旦 {@code cast}
 * 抛异常(比如 Mishap)✗ 这个 ThreadLocal 就会<b>残留</b> ✗
 * 之后同一线程上的任何写入都会被错误地归到那个玩家头上。
 *
 * <p>所以这里同时记下设置时的 {@code gameTime} ✗ 读取时必须与当前 tick 相同才认 ✗
 * 残留值最多活过一个 tick 就自动失效。
 *
 * <h2>异步线程</h2>
 * 别的线程没有上下文 ✗ 于是<b>不会被记录</b> ✗ 这是刻意选择的偏安全行为:
 * 宁可漏记,也不能把写入算到无辜玩家头上。
 */
public final class WriterContext {

    /** 上下文的有效期:设置时的 gameTime。 */
    private static final ThreadLocal<Pending> CURRENT = new ThreadLocal<>();

    private record Pending(ServerPlayer player, long gameTime) {
    }

    /** 在书写图案开始时设置。 */
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

    /**
     * 取当前书写者;上下文与当前 tick 不一致(过期或残留)时返回 null。
     */
    @Nullable
    public static ServerPlayer get(MinecraftServer server) {
        Pending pending = CURRENT.get();
        if (pending == null) {
            return null;
        }
        long now = server.overworld().getGameTime();
        if (pending.gameTime() != now) {
            // 过期的残留值:顺手清掉
            CURRENT.remove();
            return null;
        }
        return pending.player();
    }

    private WriterContext() {
    }
}