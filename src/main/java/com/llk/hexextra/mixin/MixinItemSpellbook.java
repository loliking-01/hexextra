package com.llk.hexextra.mixin;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import com.llk.hexextra.item.betterspellbook.SpellbookPersistence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让<b>原版法术书</b>的书页也存进世界文件。
 * <h2>为什么 TAIL 而不是 HEAD</h2>
 * 必须等原版把组件写完,我们才有东西可抄。
 */
@Mixin(ItemSpellbook.class)
public class MixinItemSpellbook {

    @Inject(method = "writeDatum", at = @At("TAIL"), remap = false)
    private void hexextra$persist(ItemStack stack, Iota iota, CallbackInfo ci) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            SpellbookPersistence.flush(stack, server);
        }
    }
}