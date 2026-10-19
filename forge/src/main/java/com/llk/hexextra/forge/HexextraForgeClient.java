package com.llk.hexextra.forge;

import at.petrak.hexcasting.common.items.storage.ItemFocus;
import at.petrak.hexcasting.xplat.IClientXplatAbstractions;
import com.llk.hexextra.HexextraCommon;
import com.llk.hexextra.item.betterspellbook.BetterSpellbookItems;
import com.llk.hexextra.item.betterspellbook.BetterSpellbookRendering;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Forge 侧的强化法术书客户端设置:模型谓词。
 *
 * <p>咒法学按<b>物品实例</b>注册这些谓词,所以我们的物品要自己注册一份。
 * 注册动作必须延迟到 {@code enqueueWork} 里 —— 物品此时才确定已经就绪。
 */
@Mod.EventBusSubscriber(modid = HexextraCommon.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HexextraForgeClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            Item item = BetterSpellbookItems.get();
            IClientXplatAbstractions.INSTANCE.registerItemProperty(item, ItemFocus.OVERLAY_PRED,
                    (stack, level, entity, seed) -> BetterSpellbookRendering.overlayLayer(stack));
            IClientXplatAbstractions.INSTANCE.registerItemProperty(item, ItemFocus.VARIANT_PRED,
                    (stack, level, entity, seed) -> BetterSpellbookRendering.variant(stack));
        });
    }

    private HexextraForgeClient() {
    }
}