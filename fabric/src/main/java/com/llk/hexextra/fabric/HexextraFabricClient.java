package com.llk.hexextra.fabric;

import at.petrak.hexcasting.common.items.storage.ItemFocus;
import at.petrak.hexcasting.xplat.IClientXplatAbstractions;
import com.llk.hexextra.item.betterspellbook.BetterSpellbookItems;
import com.llk.hexextra.item.betterspellbook.BetterSpellbookRendering;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.world.item.Item;

/**
 * Fabric 侧的强化法术书客户端设置:模型谓词。
 *
 * <p>谓词走咒法学的 xplat 接口(两端共用同一份注册逻辑)。
 */
public final class HexextraFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Item item = BetterSpellbookItems.get();

        IClientXplatAbstractions.INSTANCE.registerItemProperty(item, ItemFocus.OVERLAY_PRED,
                (stack, level, entity, seed) -> BetterSpellbookRendering.overlayLayer(stack));
        IClientXplatAbstractions.INSTANCE.registerItemProperty(item, ItemFocus.VARIANT_PRED,
                (stack, level, entity, seed) -> BetterSpellbookRendering.variant(stack));
    }
}