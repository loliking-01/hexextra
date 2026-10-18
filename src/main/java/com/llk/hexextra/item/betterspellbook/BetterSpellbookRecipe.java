package com.llk.hexextra.item.betterspellbook;

import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import at.petrak.hexcasting.common.lib.HexDataComponents;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 「普通法术书 + 周围一圈充能紫水晶 → 强化法术书」的合成。
 *
 * <h2>为什么必须是自定义配方</h2>
 * 普通的工作台 JSON 配方只能指定"产出什么物品",没法把<b>输入物品上的数据</b>
 * 搬给产物。这里要把原版法术书的书页原样复制到强化法术书里,所以只能用
 * {@link CustomRecipe} 在代码里完成,JSON 那边只留一个 {@code "type"} 指过来。
 *
 * <h2>形状</h2>
 * 中间必须是法术书,其余八格全是充能紫水晶。所有格子必须被占满(不允许空着)。
 */
public class BetterSpellbookRecipe extends CustomRecipe {

    /** 中间那一格在 3×3 输入里的下标。 */
    private static final int CENTER = 4;

    /** 配方序列化器。1.21 的配方用 MapCodec + StreamCodec,不需要写 JSON 解析代码。 */
    public static final MapCodec<BetterSpellbookRecipe> MAP_CODEC =
            MapCodec.unit(BetterSpellbookRecipe::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, BetterSpellbookRecipe> STREAM_CODEC =
            StreamCodec.unit(new BetterSpellbookRecipe());

    public BetterSpellbookRecipe() {
        super(net.minecraft.world.item.crafting.CraftingBookCategory.MISC);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        // 只接受 3×3 的摆法
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                return false;
            }
            if (i == CENTER) {
                if (!(stack.getItem() instanceof ItemSpellbook)) {
                    return false;
                }
            } else {
                if (!stack.is(at.petrak.hexcasting.common.lib.HexItems.CHARGED_AMETHYST.get())) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack source = input.getItem(CENTER);
        ItemStack result = new ItemStack(BetterSpellbookFeature.ITEM.get());

        // 把原书的书页复制过来,并分配新的 UUID 落盘
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            BetterSpellbookItem.copyFrom(source, result, ServerLifecycleHooks.getCurrentServer());
        } else {
            // 理论上合成总在服务端;真到这里就只搬组件,不落盘
            result.set(HexDataComponents.IOTA_HOLDER_IOTA.get(),
                    source.get(HexDataComponents.IOTA_HOLDER_IOTA.get()));
        }
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return BetterSpellbookFeature.SERIALIZER.get();
    }
}
