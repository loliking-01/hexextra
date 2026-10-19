package com.llk.hexextra.mixin;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import com.llk.hexextra.core.ServerHolder;
import com.llk.hexextra.item.betterspellbook.SpellbookPersistence;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让原版法术书的所有<b>内容变更</b>都同步到文件。
 *
 * <p>0.11.4 的书页存在物品 NBT 里,公开常量见 {@code ItemSpellbook.TAG_*}。
 * 三条会改内容的路径都要挂:
 * <ul>
 *   <li>{@code writeDatum} —— 书写图案写入书页</li>
 *   <li>{@code setSealed} —— 封印(静态方法)</li>
 *   <li>{@code setVariant} —— 换书皮</li>
 * </ul>
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
        var server = ServerHolder.get();
        if (server != null) {
            SpellbookPersistence.flush(stack, server);
        }
    }
}