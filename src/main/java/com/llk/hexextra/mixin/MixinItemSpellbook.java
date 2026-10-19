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
 *
 * <h2>做法</h2>
 * 原版 {@code writeDatum} 照常把书页写进物品组件(那一份保留,当缓存/镜像),
 * 在 {@code TAIL} 处再把它同步到 {@code hexextra_spellbooks.dat}。
 *
 * <h2>为什么保留组件那一份</h2>
 * <ul>
 *   <li>画面上的翻页、封印、染色全读组件,不镜像就得改一堆客户端逻辑;</li>
 *   <li><b>万一玩家卸载本模组</b>,原版书还能从组件读到内容 ——
 *       否则文件里的数据就成了孤儿,书全变空白。</li>
 * </ul>
 *
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