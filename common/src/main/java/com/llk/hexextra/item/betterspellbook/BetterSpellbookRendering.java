package com.llk.hexextra.item.betterspellbook;

import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import net.minecraft.world.item.ItemStack;

/**
 * 强化法术书的两个模型谓词取值。
 *
 * <h2>为什么用 readIotaTag 而不是 readIota</h2>
 * 0.11.4 的 {@code IotaHolderItem.readIota(ItemStack, ServerLevel)} 需要<b>服务端世界</b>,
 * 而模型谓词是<b>客户端</b>算的,拿不到 {@code ServerLevel}。所以这里判断"本页有没有内容"
 * 用的是 {@code readIotaTag}(返回 {@code CompoundTag},客户端可用,内容为空时返回 null)。
 *
 * <h2>为什么没有染色</h2>
 * 0.11.4 的 {@code IotaHolderItem.getColor} 默认实现同样依赖 {@code readIota},
 * 客户端拿不到世界,所以 0.11.4 的法术书本身就没有按 iota 类型染色 ——
 * 我们保持一致,不额外做(1.21.1 版才有真正的染色)。
 */
public final class BetterSpellbookRendering {

    /** {@code hexcasting:overlay_layer}:0 = 空页,1 = 本页有内容,2 = 已封印。 */
    public static int overlayLayer(ItemStack stack) {
        if (ItemSpellbook.isSealed(stack)) {
            return 2;
        }
        if (stack.getItem() instanceof ItemSpellbook book) {
            if (book.readIotaTag(stack) != null) {
                return 1;
            }
        }
        return 0;
    }

    /** {@code hexcasting:variant}:书皮变体,直接读物品 NBT。 */
    public static int variant(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(ItemSpellbook.TAG_VARIANT)) {
            return 0;
        }
        return tag.getInt(ItemSpellbook.TAG_VARIANT);
    }

    private BetterSpellbookRendering() {
    }
}