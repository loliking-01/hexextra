package com.llk.hexextra.mixin;

import at.petrak.hexcasting.common.msgs.MsgShiftScrollC2S;
import com.llk.hexextra.item.betterspellbook.BetterSpellbookItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让服务端接受强化法术书的翻页请求。
 *
 * <h2>为什么客户端放行了还要改这里</h2>
 * 客户端过了 {@code ShiftScrollListener.IsScrollableItem} 之后只是"把滚动量攒起来、
 * 发一个 {@code MsgShiftScrollC2S} 给服务端"。真正改页码的是服务端的
 * {@code handleForHand},而它同样是:
 * <pre>
 * if (stack.getItem() == HexItems.SPELLBOOK.get())  this.spellbook(...);
 * else if (stack.getItem() == HexItems.ABACUS.get()) this.abacus(...);
 * </pre>
 * 还是<b>引用相等</b> ✗ 我们的物品匹配不上 ✗ 于是包收了但页码不动。
 *
 * <h2>为什么能直接调 private 的 spellbook()</h2>
 * Mixin 的注入方法在合并后会成为目标类的一部分,所以可以直接访问它的私有成员。
 */
@Mixin(MsgShiftScrollC2S.class)
public class MixinMsgShiftScrollC2S {

    @Inject(method = "handleForHand", at = @At("HEAD"), cancellable = true, remap = false)
    private void hexextra$handleBetterSpellbook(ServerPlayer player, InteractionHand hand,
                                                double delta, CallbackInfo ci) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof BetterSpellbookItem) {
            this.hexextra$spellbook(player, hand, stack, delta);
            ci.cancel();
        }
    }

    /**
     * 转发到原类的私有 {@code spellbook(...)}。
     *
     * <p>Mixin 合并后可以直接调用,但需要先声明一个同名同签名的转发方法 ——
     * 这里用 {@code @Shadow} 更直接。
     */
    @org.spongepowered.asm.mixin.Shadow
    private void spellbook(ServerPlayer player, InteractionHand hand, ItemStack stack, double delta) {
        throw new AssertionError();
    }

    private void hexextra$spellbook(ServerPlayer player, InteractionHand hand,
                                    ItemStack stack, double delta) {
        this.spellbook(player, hand, stack, delta);
    }
}