package com.llk.hexextra.fabric;

import at.petrak.hexcasting.client.RegisterClientStuff;
import at.petrak.hexcasting.common.items.storage.ItemFocus;
import at.petrak.hexcasting.xplat.IClientXplatAbstractions;
import com.llk.hexextra.item.betterspellbook.BetterSpellbookItems;
import com.llk.hexextra.item.betterspellbook.BetterSpellbookRendering;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 侧的强化法术书客户端设置:模型谓词 + 物品染色。
 *
 * <p>谓词走咒法学的 xplat 接口(与 Forge 侧同一份逻辑);
 * 染色必须用 Fabric 自己的 {@code ColorProviderRegistry}(Forge 用事件)。
 */
public final class HexextraFabricClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("hexextra");

    @Override
    public void onInitializeClient() {
        Item item = BetterSpellbookItems.get();

        IClientXplatAbstractions.INSTANCE.registerItemProperty(item, ItemFocus.OVERLAY_PRED,
                (stack, level, entity, seed) -> BetterSpellbookRendering.overlayLayer(stack));
        IClientXplatAbstractions.INSTANCE.registerItemProperty(item, ItemFocus.VARIANT_PRED,
                (stack, level, entity, seed) -> BetterSpellbookRendering.variant(stack));

        ColorProviderRegistry.ITEM.register(
                RegisterClientStuff.makeIotaStorageColorizer(BetterSpellbookRendering::tintColor), item);

        // 这行日志用来确认客户端入口真的跑了 —— 如果日志里没有它,
        // 说明 fabric.mod.json 的 client entrypoint 没生效。
        LOGGER.info("hexextra: 强化法术书客户端已注册(模型谓词 2 个 + 染色 1 个)");
    }
}