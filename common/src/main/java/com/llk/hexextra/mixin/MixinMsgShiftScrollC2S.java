package com.llk.hexextra.mixin;

import at.petrak.hexcasting.common.msgs.MsgShiftScrollC2S;
import com.llk.hexextra.item.betterspellbook.BetterSpellbookItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让服务端接受强化法术书的翻页请求。
 *
 * <p>客户端放行之后只是把滚动量攒起来发包;真正改页码的是服务端的
 * {@code handleForHand},而它同样是引用相等判断 ✗ 我们的物品匹配不上 ✗
 * 包收了但页码不动。
 *
 * <p>Mixin 合并后可以直接访问目标类的私有成员,所以用 {@link Shadow} 拿到
 * 私有的 {@code spellbook(...)} 再转发。
 */
@Mixin(MsgShiftScrollC2S.class)
public class MixinMsgShiftScrollC2S {

    @Shadow(remap = false)
    private void spellbook(ServerPlayer player, InteractionHand hand, ItemStack stack, double delta) {
        throw new AssertionError("shadow");
    }

    @Inject(method = "handleForHand", at = @At("HEAD"), cancellable = true, remap = false)
    private void hexextra$handleBetterSpellbook(ServerPlayer player, InteractionHand hand,
                                                double delta, CallbackInfo ci) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof BetterSpellbookItem) {
            this.spellbook(player, hand, stack, delta);
            ci.cancel();
        }
    }
}