package com.llk.hexextra.item.betterspellbook;

import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import at.petrak.hexcasting.common.lib.HexItems;
import com.llk.hexextra.core.ServerHolder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 「普通法术书 + 一圈充能紫水晶 → 强化法术书」的合成(0.11.4 / 1.20.1 版)。
 *
 * <h2>为什么必须自定义配方</h2>
 * 普通工作台 JSON 配方没法把<b>输入物品上的数据</b>搬给产物。这里要把原版法术书的
 * 全部书页 tag 原样复制到强化书里,只能在代码里完成。
 *
 * <h2>为什么中心格用精确匹配</h2>
 * 不能用 {@code instanceof ItemSpellbook} —— 强化法术书本身就是它的子类,
 * 那样会让强化书也能再合成一次(还会复制出第二份书页)。
 */
public class BetterSpellbookRecipe extends CustomRecipe {

    /** 中间那一格在 3×3 输入里的下标。 */
    private static final int CENTER = 4;

    public BetterSpellbookRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        if (input.getWidth() != 3 || input.getHeight() != 3) {
            return false;
        }

        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                return false;
            }
            if (i == CENTER) {
                // 只接受原版法术书
                if (!stack.is(HexItems.SPELLBOOK)) {
                    return false;
                }
            } else {
                if (!stack.is(HexItems.CHARGED_AMETHYST)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        ItemStack source = input.getItem(CENTER);
        ItemStack result = new ItemStack(BetterSpellbookItems.get());

        if (ServerHolder.get() != null) {
            BetterSpellbookItem.copyFrom(source, result, ServerHolder.get());
        }
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BetterSpellbookFeature.BETTER_SPELLBOOK_SERIALIZER;
    }
}