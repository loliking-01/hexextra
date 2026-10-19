package com.llk.hexextra.item.betterspellbook;

import net.minecraft.world.item.Item;

/**
 * 强化法术书的<b>唯一实例</b>持有者。
 *
 * <h2>为什么要单例</h2>
 * Forge 用 {@code DeferredRegister}(延迟创建)、Fabric 直接 {@code Registry.register},
 * 但物品的<b>实例身份</b>必须两端只有一个 —— 否则物品栏里的引用和注册表里的不是同一个,
 * 而且 0.11.4 里大量判断用的是 {@code ==}。
 */
public final class BetterSpellbookItems {

    private static Item instance;

    public static Item get() {
        if (instance == null) {
            instance = new BetterSpellbookItem(new Item.Properties().stacksTo(1));
        }
        return instance;
    }

    private BetterSpellbookItems() {
    }
}