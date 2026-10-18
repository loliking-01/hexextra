package com.llk.hexextra.mixin;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.casting.actions.lists.OpLastNToList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.CastingImages;
import com.llk.hexextra.pattern.if_else.IfElseChainOps;
import com.llk.hexextra.pattern.if_else.IfElseIota;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 让「群体之策略」({@code last_n_list},Flock's Gambit)能把栈上连续的几条条件链
 * <b>一次性串成一条</b>。
 *
 * <h2>这解决什么问题</h2>
 * 拆解(见 {@link MixinOpSplat})会把一条链摊成许多条单分支链。若要重新组装,
 * 一条条画「加法之馏化」会非常啰嗦 —— N 条分支就要画 N-1 次加法。
 * 这个重载让玩家把若干条链压在栈上,再用一次 {@code last_n_list} 全部串起来。
 *
 * <h2>为什么不破坏原功能</h2>
 * 只有在「被选中的那 n 个 iota <b>全部</b>是条件链,且 n ≥ 2」时才接管;
 * 其余任何情况都<b>原样放行</b>,于是"把栈顶 n 个打包成一个列表"这个原行为丝毫不受影响。
 * 判定见 {@link IfElseChainOps#concatAll}。
 *
 * <h2>注入方式</h2>
 * {@code @At("HEAD")} + {@code cancellable},与 {@link MixinOpSplat} 同理。
 */
@Mixin(OpLastNToList.class)
public class MixinOpLastNToList {

    /**
     * @param env          施法环境
     * @param image        当前施法状态(栈从这里取)
     * @param continuation 当前续延
     * @param cir          返回值回调;只有真的接管时才设置它
     */
    @Inject(method = "operate", at = @At("HEAD"), cancellable = true, remap = false)
    private void hexextra$lastNListOfChains(CastingEnvironment env,
                                            CastingImage image,
                                            SpellContinuation continuation,
                                            CallbackInfoReturnable<OperationResult> cir) {
        TreeList<Iota> stack = image.getStack();

        // 栈空时交给原实现去抛"参数不足" —— 我们只是加一个分支,不该替它做错误处理
        if (stack.isEmpty()) {
            return;
        }

        // 取参数的方式与原实现逐字一致:
        //   stack.takeRight(1).getPositiveIntUnderInclusive(0, stack.size - 1)
        // 第 4 个参数是 argc,原实现用的是默认值 0,这里显式传 0 保持一致。
        // 若 n 非法,这里抛出的 Mishap 与原实现会抛的完全相同,所以直接放它往外传。
        int yoinkCount = OperatorUtils.getPositiveIntUnderInclusive(
                stack.takeRight(1), 0, stack.size() - 1, 0);

        TreeList<Iota> stackWithoutArg = stack.init();
        TreeList<Iota> picked = TreeList.from(stackWithoutArg.takeRight(yoinkCount));

        // TreeList 本身就是一个 List,可以直接交给共用逻辑
        IfElseIota merged = IfElseChainOps.concatAll(picked);
        if (merged == null) {
            // 不全是链,或不足两条 → 保持原行为:打包成一个列表
            return;
        }

        TreeList<Iota> rest = stackWithoutArg.dropRight(yoinkCount);
        CastingImage newImage = CastingImages.withStack(image.withUsedOp(), rest.appended(merged));

        cir.setReturnValue(new OperationResult(
                newImage,
                List.<OperatorSideEffect>of(),   // 与原实现一致:不产生任何副作用
                continuation,
                HexEvalSounds.NORMAL_EXECUTE.get()));
    }
}
