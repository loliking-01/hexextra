package com.llk.hexextra.item.betterspellbook;

import com.llk.hexextra.core.HexextraRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * 强化法术书的注册入口:一个数据组件(存 UUID)+ 物品本身。
 *
 * <p>组件必须先于物品被使用,但注册顺序对 NeoForge 不重要 ——
 * 两者都在 {@link HexextraRegistries} 的 {@code DeferredRegister} 里统一挂到 mod 事件总线。
 */
public final class BetterSpellbookFeature {

    /** 存书页身份的 UUID 组件。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> BOOK_ID =
            HexextraRegistries.DATA_COMPONENT_TYPES.register("book_id",
                    () -> BetterSpellbookItem.BOOK_ID);

    /** 强化法术书本体。 */
    public static final DeferredHolder<Item, Item> ITEM =
            HexextraRegistries.ITEMS.register("betterspellbook",
                    () -> new BetterSpellbookItem(new Item.Properties()
                    .stacksTo(1)
                    // 第 1 页预置一个 NullIota。
                    //
                    // 为什么必须预置:ItemSpellbook.rotatePageIdx 的字节码是
                    //   int page = getPage(stack, 0);      // 默认值 0
                    //   if (page != 0) { page += forward ? 1 : -1; ... }
                    // 书页为空时 getPage 返回 0,if 不成立,页码永远停在 0 —— 翻页彻底失效。
                    // 咒法学原版是靠法术书的合成配方填上第 1 页的;我们直接给物品默认组件,
                    // 这样 /give 出来的书也能用。
                    .component(at.petrak.hexcasting.common.lib.HexDataComponents.SPELLBOOK_PAGES.get(),
                            java.util.Map.of("1", new at.petrak.hexcasting.api.casting.iota.NullIota()))
                    .component(at.petrak.hexcasting.common.lib.HexDataComponents.ITEM_VARIANT.get(), 0)));

    /** 合成用的自定义配方序列化器。 */
    public static final DeferredHolder<
            net.minecraft.world.item.crafting.RecipeSerializer<?>,
            net.minecraft.world.item.crafting.RecipeSerializer<BetterSpellbookRecipe>> SERIALIZER =
            HexextraRegistries.RECIPE_SERIALIZERS.register("betterspellbook",
                    () -> new net.minecraft.world.item.crafting.RecipeSerializer<BetterSpellbookRecipe>() {
                        @Override
                        public MapCodec<BetterSpellbookRecipe> codec() {
                            return BetterSpellbookRecipe.MAP_CODEC;
                        }

                        @Override
                        public StreamCodec<RegistryFriendlyByteBuf, BetterSpellbookRecipe> streamCodec() {
                            return BetterSpellbookRecipe.STREAM_CODEC;
                        }
                    });

    private BetterSpellbookFeature() {
    }

    /** 触发本类静态初始化。 */
    public static void init() {
        // 故意为空。
    }
}
