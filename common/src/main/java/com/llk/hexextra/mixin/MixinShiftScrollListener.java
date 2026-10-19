package com.llk.hexextra.mixin;

import at.petrak.hexcasting.client.ShiftScrollListener;
import com.llk.hexextra.item.betterspellbook.BetterSpellbookItem;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让强化法术书也能被「潜行 + 滚轮」翻页(0.11.4 / 1.20.1 版)。
 *
 * <p>咒法学客户端的判定是引用相等而不是 {@code instanceof}:
 * <pre>
 * return item == HexItems.SPELLBOOK || item == HexItems.ABACUS;
 * </pre>
 * 所以任何继承 {@code ItemSpellbook} 的自定义物品都过不了这道门 ✗ 页码请求不会发出。
 *
 * <p><b>客户端专用</b>:本类必须在 mixins.json 的 {@code client} 数组里,
 * 否则服务端会因为找不到 {@code ShiftScrollListener} 而崩。
 */
@Mixin(ShiftScrollListener.class)
public class MixinShiftScrollListener {

    @Inject(method = "IsScrollableItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void hexextra$allowBetterSpellbook(Item item, CallbackInfoReturnable<Boolean> cir) {
        if (item instanceof BetterSpellbookItem) {
            cir.setReturnValue(true);
        }
    }
}