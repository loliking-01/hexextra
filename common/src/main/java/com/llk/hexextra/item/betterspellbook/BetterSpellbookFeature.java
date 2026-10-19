package com.llk.hexextra.item.betterspellbook;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

/**
 * 强化法术书的配方序列化器定义。
 *
 * <p>1.20.1 的自定义配方用 {@link SimpleCraftingRecipeSerializer} 就够了 ——
 * 它负责给 {@link net.minecraft.world.item.crafting.CustomRecipe} 子类做
 * 序列化,不需要自己写 codec。构造工厂的签名是
 * {@code (ResourceLocation id, CraftingBookCategory category)}。
 *
 * <p>「注册」这一步两端不同(Forge 用 DeferredRegister、Fabric 直接 Registry.register),
 * 所以真正的注册动作放在各自的 loader 里,这里只提供<b>同一个实例</b>。
 */
public final class BetterSpellbookFeature {

    /** 合成配方的序列化器。 */
    public static final SimpleCraftingRecipeSerializer<BetterSpellbookRecipe> BETTER_SPELLBOOK_SERIALIZER =
            new SimpleCraftingRecipeSerializer<>(BetterSpellbookRecipe::new);

    /** 封印配方的序列化器。 */
    public static final SimpleCraftingRecipeSerializer<SealBetterSpellbookRecipe> SEAL_SERIALIZER =
            new SimpleCraftingRecipeSerializer<>(SealBetterSpellbookRecipe::new);

    public static final ResourceLocation BETTER_SPELLBOOK_RECIPE_ID =
            new ResourceLocation("hexextra", "betterspellbook");
    public static final ResourceLocation SEAL_RECIPE_ID =
            new ResourceLocation("hexextra", "seal_betterspellbook");

    private BetterSpellbookFeature() {
    }
}