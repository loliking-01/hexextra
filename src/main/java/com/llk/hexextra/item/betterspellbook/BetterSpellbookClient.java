package com.llk.hexextra.item.betterspellbook;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.common.items.storage.ItemFocus;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import at.petrak.hexcasting.common.lib.HexDataComponents;
import at.petrak.hexcasting.xplat.IClientXplatAbstractions;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.world.item.Item;

/**
 * 强化法术书的客户端侧设置:给它注册与咒法学法术书相同的两个模型谓词。
 *
 * <h2>为什么必须单独注册</h2>
 * 咒法学是这样注册的:
 * <pre>
 * IClientXplatAbstractions.INSTANCE.registerItemProperty(
 *         (Item) HexItems.SPELLBOOK.get(),   // ← 绑定到那个具体物品实例
 *         ItemFocus.OVERLAY_PRED,            // hexcasting:overlay_layer
 *         ...);
 * </pre>
 * 而 {@code ItemProperties.register(Item, ...)} 的绑定关系是<b>按物品实例</b>的 ——
 * 我们的强化法术书是另一个 Item 对象,所以它<b>没有任何模型谓词</b>,
 * 模型的 {@code overrides} 全部匹配不上,外观永远停在空书那一档。
 *
 * <h2>谓词取值</h2>
 * <ul>
 *   <li>{@code hexcasting:overlay_layer}:0 = 空页,1 = 本页有内容,2 = 本页已封印</li>
 *   <li>{@code hexcasting:variant}:书皮变体(读 ITEM_VARIANT 组件)</li>
 * </ul>
 * 取值必须与 {@code assets/hexextra/models/item/betterspellbook.json} 里
 * {@code overrides} 的 predicate 完全对应。
 */
public final class BetterSpellbookClient {

    private BetterSpellbookClient() {
    }

    /**
     * 物品染色:标签颜色取自<b>当前页 iota 类型定义的颜色</b>。
     *
     * <p>咒法学原版是 {@code makeIotaStorageColorizer(stack -> iota 的颜色)},
     * 然后按<b>物品实例</b>绑进 {@code BiConsumer<Item, ItemColor>} ✗
     * 所以我们的物品同样是"没有颜色处理器"状态。
     *
     * <p>{@code ItemColor} 返回的是 ARGB,没有 iota 时返回白色(不改变贴图)。
     */
    public static net.minecraft.client.color.item.ItemColor makeColorizer() {
        return at.petrak.hexcasting.client.RegisterClientStuff.makeIotaStorageColorizer(stack -> {
            if (stack.getItem() instanceof ItemSpellbook book) {
                Iota current = book.readIota(stack);
                if (current != null) {
                    return current.getType().color();
                }
            }
            return 0xFFFFFFFF;
        });
    }

    /** 在客户端初始化阶段调用一次。 */
    public static void registerModelProperties() {
        Item ourItem = BetterSpellbookFeature.ITEM.get();

        // hexcasting:overlay_layer —— 空 / 有内容 / 封印
        ItemPropertyFunction overlay = (stack, level, entity, seed) -> {
            if (ItemSpellbook.isSealed(stack)) {
                return 2.0F;
            }
            if (stack.getItem() instanceof ItemSpellbook book) {
                Iota current = book.readIota(stack);
                if (current != null) {
                    return 1.0F;
                }
            }
            return 0.0F;
        };
        IClientXplatAbstractions.INSTANCE.registerItemProperty(
                ourItem, ItemFocus.OVERLAY_PRED, overlay);

        // hexcasting:variant —— 书皮变体,直接读组件
        ItemPropertyFunction variant = (stack, level, entity, seed) -> {
            Integer v = stack.get(HexDataComponents.ITEM_VARIANT.get());
            return v == null ? 0.0F : v.floatValue();
        };
        IClientXplatAbstractions.INSTANCE.registerItemProperty(
                ourItem, ItemFocus.VARIANT_PRED, variant);
    }
}
