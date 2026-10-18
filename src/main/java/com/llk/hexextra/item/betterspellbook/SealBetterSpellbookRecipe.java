package com.llk.hexextra.item.betterspellbook;

import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 强化法术书的封印配方:蜂蜜块 + 书 → 封印当前页。
 *
 * <h2>为什么必须自己做</h2>
 * 咒法学的封印用配方类型 {@code hexcasting:seal_spellbook}(见它的
 * {@code data/hexcasting/recipe/dynamicseal_spellbook.json})。那个类型是咒法学注册的、
 * 判定对象是它<b>自己那个物品实例</b> ✗ 所以蜂蜜块对强化法术书完全没反应 ✗
 * 这也是"强化书无法密封"的直接原因。
 *
 * <h2>行为对齐</h2>
 * 与咒法学一致:封印<b>当前选中的那一页</b>;已封印的书不再参与合成(避免浪费蜂蜜块)。
 * 解封仍走「擦除物品」图案,由咒法学自己的逻辑处理。
 */
public class SealBetterSpellbookRecipe extends CustomRecipe {

    public static final MapCodec<SealBetterSpellbookRecipe> MAP_CODEC =
            MapCodec.unit(SealBetterSpellbookRecipe::new);

    /** 唯一实例:unit codec 要求编码与解码是同一个对象,否则服务端同步配方会抛异常。 */
    public static final SealBetterSpellbookRecipe INSTANCE = new SealBetterSpellbookRecipe();

    public static final StreamCodec<RegistryFriendlyByteBuf, SealBetterSpellbookRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    public SealBetterSpellbookRecipe() {
        super(CraftingBookCategory.MISC);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int books = 0;
        int honeycombs = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof BetterSpellbookItem) {
                // 已经封印过就不用再合了
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
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack book = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof BetterSpellbookItem) {
                book = stack;
                break;
            }
        }
        if (book.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 复制整本书(保留 BOOK_ID 与四个书页组件),再封印当前页
        ItemStack result = book.copy();
        result.setCount(1);
        ItemSpellbook.setSealed(result, true);

        // setSealed 直接改组件、不经过 writeDatum,所以这里必须手动落盘
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            BetterSpellbookItem.flush(result, server);
        }
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return BetterSpellbookFeature.SEAL_SERIALIZER.get();
    }
}