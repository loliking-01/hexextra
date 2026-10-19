package com.llk.hexextra.item.betterspellbook;

import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import com.llk.hexextra.core.ServerHolder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 强化法术书的封印配方:蜂蜜块 + 书 → 封印当前页。
 *
 * <p>咒法学自己的封印配方只认它自己那个物品实例,蜂蜜块对强化书没有反应,
 * 所以这里必须补一份。
 */
public class SealBetterSpellbookRecipe extends CustomRecipe {

    public SealBetterSpellbookRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        int books = 0;
        int honeycombs = 0;
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof BetterSpellbookItem) {
                if (ItemSpellbook.isSealed(stack)) {
                    return false;
                }
                books++;
            } else if (stack.is(Items.HONEYCOMB)) {
                honeycombs++;
            } else {
                return false;
            }
        }
        return books == 1 && honeycombs == 1;
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        ItemStack book = ItemStack.EMPTY;
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof BetterSpellbookItem) {
                book = stack;
                break;
            }
        }
        if (book.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack result = book.copy();
        result.setCount(1);
        ItemSpellbook.setSealed(result, true);

        // setSealed 直接改物品 NBT、不经过 writeDatum,所以这里必须手动落盘
        if (ServerHolder.get() != null) {
            BetterSpellbookItem.flush(result, ServerHolder.get());
        }
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BetterSpellbookFeature.SEAL_SERIALIZER;
    }
}