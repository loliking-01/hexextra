package com.llk.hexextra.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.llk.hexextra.core.WriterContext;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在「书写之策略」真正写入的那一刻,把施法者放进 {@link WriterContext}。
 *
 * <h2>为什么用 targets 字符串</h2>
 * {@code OpWrite$Spell} 是<b>包私有</b>的蛇形内部类 ✗ 我们的包无法直接引用它 ✗
 * 所以 {@code @Mixin} 只能写全名。
 *
 * <h2>为什么 HEAD + RETURN 成对</h2>
 * 写完立刻清掉 ✗ 避免污染同一线程上后续的其它物品写入 ✓
 */
@Mixin(targets = "at.petrak.hexcasting.common.casting.actions.rw.OpWrite$Spell")
public class MixinOpWriteSpell {

    /**
     * 设置"当前书写者"。
     *
     * <h2>为什么排除假玩家</h2>
     * 假玩家(Carpet 之类的 {@code player_123}、NeoForge 的 {@link FakePlayer})
     * 往往没有真实连接、UUID 也是随机生成的 ✗ 如果照单全收,
     * 访问记录里会堆满这些条目 ✗ 真正的玩家记录反而被挤掉
     * (记录表上限只有 2000 条)。
     *
     * <p>两个判据一起用:
     * <ul>
     *   <li>{@code instanceof FakePlayer} —— NeoForge 的标准假玩家类型;</li>
     *   <li>{@code connection == null} —— 没有网络连接的一定不是真人。</li>
     * </ul>
     */
    @Inject(method = "cast", at = @At("HEAD"), remap = false)
    private void hexextra$beforeWrite(CastingEnvironment env, CallbackInfo ci) {
        if (env.getCastingEntity() instanceof ServerPlayer player
                && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)
                && player.connection != null) {
            WriterContext.set(player);
        }
    }

    @Inject(method = "cast", at = @At("RETURN"), remap = false)
    private void hexextra$afterWrite(CastingEnvironment env, CallbackInfo ci) {
        WriterContext.clear();
    }
}