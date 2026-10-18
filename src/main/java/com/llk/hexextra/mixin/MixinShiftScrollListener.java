package com.llk.hexextra.mixin;

import at.petrak.hexcasting.client.ShiftScrollListener;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import com.llk.hexextra.item.betterspellbook.BetterSpellbookItem;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让强化法术书也能被「潜行 + 滚轮」翻页。
 *
 * <h2>为什么必须是 Mixin</h2>
 * 咒法学客户端的翻页入口是 {@code ShiftScrollListener.IsScrollableItem(Item)},字节码是:
 * <pre>
 * return item == HexItems.SPELLBOOK.get()      // if_acmpeq:引用相等
 *     || item == HexItems.ABACUS.get();
 * </pre>
 * 用的是<b>引用相等</b>而不是 {@code instanceof} ✗ 所以任何继承 {@code ItemSpellbook}
 * 的自定义物品都过不了这道门 ✗ 页码请求根本不会发给服务端。
 *
 * <h2>为什么加 remap = false</h2>
 * 目标是咒法学自己的类与方法,方法名是 mod 自定义的、不走 SRG 重映射;
 * 标了之后注解处理器也不会去查一个查不到的混淆映射。
 */
@Mixin(ShiftScrollListener.class)
public class MixinShiftScrollListener {

    /** 放行我们的强化法术书。 */
    @Inject(method = "IsScrollableItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void hexextra$allowBetterSpellbook(Item item, CallbackInfoReturnable<Boolean> cir) {
        if (item instanceof BetterSpellbookItem) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 滚轮方向:方向与配置跟原版法术书保持一致。
     *
     * <p>原方法的第一个判断是 {@code if (!sprinting) return 1.0},所以只在冲刺时才需要覆盖。
     */
    @Inject(method = "getScrollModifier", at = @At("HEAD"), cancellable = true, remap = false)
    private static void hexextra$scrollModifier(Item item, boolean sprinting,
                                                CallbackInfoReturnable<Double> cir) {
        if (sprinting && item instanceof BetterSpellbookItem) {
            boolean invert = at.petrak.hexcasting.api.mod.HexConfig.client()
                    .invertSpellbookScrollDirection();
            cir.setReturnValue(invert ? -1.0 : 1.0);
        }
    }
}