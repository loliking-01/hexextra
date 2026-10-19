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
 * <h2>染色</h2>
 * 用 {@code IotaType.getColor(CompoundTag)} 从 <b>NBT</b> 直接取类型颜色,
 * 不需要 {@code ServerLevel},所以客户端也算得出来。
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

    /**
     * 物品染色:当前页 iota 类型定义的颜色。
     *
     * <p>用 {@code IotaType.getColor(CompoundTag)} —— 它是 0.11.4 提供的<b>公开静态方法</b>,
     * 直接吃 {@code readIotaTag} 返回的 NBT,<b>不需要 ServerLevel</b>,所以客户端也能算。
     *
     * <p>空页返回白色(0xFFFFFF),即不改变贴图。
     */
    public static int tintColor(ItemStack stack) {
        if (stack.getItem() instanceof ItemSpellbook book) {
            var tag = book.readIotaTag(stack);
            if (tag != null) {
                int color = at.petrak.hexcasting.api.casting.iota.IotaType.getColor(tag);
                if (color != 0) {
                    return color;
                }
            }
        }
        return 0xFFFFFF;
    }

    private BetterSpellbookRendering() {
    }
}