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
 * <p>{@code OpWrite$Spell} 是包私有的内部类,所以 {@code @Mixin} 只能写全名。
 *
 * <p>排除假玩家:0.11.4 的 {@code common} 模块不能引用任何一方的假玩家类,
 * 所以这里只用「没有网络连接」这一个两端通用的判据。
 */
@Mixin(targets = "at.petrak.hexcasting.common.casting.actions.rw.OpWrite$Spell")
public class MixinOpWriteSpell {

    @Inject(method = "cast", at = @At("HEAD"), remap = false)
    private void hexextra$beforeWrite(CastingEnvironment env, CallbackInfo ci) {
        if (env.getCastingEntity() instanceof ServerPlayer player
                && player.connection != null) {
            WriterContext.set(player, player.level().getGameTime());
        }
    }

    @Inject(method = "cast", at = @At("RETURN"), remap = false)
    private void hexextra$afterWrite(CastingEnvironment env, CallbackInfo ci) {
        WriterContext.clear();
    }
}