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
 * 让原版法术书的所有<b>内容变更</b>都同步到文件。
 *
 * <h2>为什么不止 writeDatum</h2>
 * 书页内容通过 {@code writeDatum} 改 ✗ 但还有两条路径不经过它:
 * <ul>
 *   <li>{@code setSealed} —— 封印(蜂蜜块配方用)</li>
 *   <li>{@code setVariant} —— 换书皮(若将来接上)</li>
 * </ul>
 * 只挂 {@code writeDatum} 的话,封印状态一退出游戏就丢了。
 *
 * <h2>为什么 TAIL</h2>
 * 必须等原版把组件写完,我们才有东西可抄。
 */
@Mixin(ItemSpellbook.class)
public class MixinItemSpellbook {

    @Inject(method = "writeDatum", at = @At("TAIL"), remap = false)
    private void hexextra$persistWrite(ItemStack stack, Iota iota, CallbackInfo ci) {
        persist(stack);
    }

    @Inject(method = "setSealed", at = @At("TAIL"), remap = false)
    private static void hexextra$persistSeal(ItemStack stack, boolean sealed, CallbackInfo ci) {
        persist(stack);
    }

    @Inject(method = "setVariant", at = @At("TAIL"), remap = false)
    private void hexextra$persistVariant(ItemStack stack, int variant, CallbackInfo ci) {
        persist(stack);
    }

    private static void persist(ItemStack stack) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            SpellbookPersistence.flush(stack, server);
        }
    }
}