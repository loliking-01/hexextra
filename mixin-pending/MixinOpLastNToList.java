package com.llk.hexextra.mixin;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.common.casting.actions.lists.OpLastNToList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.pattern.if_else.IfElseChainOps;
import com.llk.hexextra.pattern.if_else.IfElseIota;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 给「群体之策略」({@code last_n_list})加一条快捷路径:
 * 当栈顶那 n 个 iota <b>全是条件链</b>时,把它们直接收拢成一条链,而不是打包成一个列表。
 *
 * <h2>为什么值得做</h2>
 * 用加法之馏化一条条接链是 O(n) 次图案;而 {@code last_n_list} 一次就能收拢,
 * 玩家写多分支 if 时手感差别很大。这与 1.21.1 版的行为一致。
 *
 * <h2>为什么只在"全是链"时出手</h2>
 * 只要有一个不是链,就<b>安静地把控制权交回原实现</b>(打包成列表)——
 * 不抛错、不改原语义。附加功能不该改变原有图案在其它场景下的行为。
 *
 * <h2>⚠ 0.11.4 与 1.21.1 的差异(重写时踩的)</h2>
 * <ul>
 *   <li>{@code CastingImage.getStack()} 返回 <b>{@code List<Iota>}</b>,不是 {@code TreeList}
 *       —— 所以没有 {@code init()} / {@code dropRight()} / {@code appended()} 可用;</li>
 *   <li>{@code HexEvalSounds} 的常量是普通静态字段(<b>没有</b> {@code .get()});</li>
 *   <li>取值助手 {@code OperatorUtils} 收 {@code List<Iota>}(1.21.1 上是 {@code TreeList});</li>
 *   <li>没有 {@code CastingImages.withStack(...)} 那种辅助,得自己 {@code copy(...)}。</li>
 * </ul>
 */
@Mixin(OpLastNToList.class)
public class MixinOpLastNToList {

    /**
     * 在原 {@code operate} 的开头尝试接管。
     *
     * @param env          施法环境
     * @param image        当前施法镜像(栈、括号状态等)
     * @param continuation 续延栈
     * @param cir          返回值回调;设置它并取消原方法
     */
    @Inject(method = "operate", at = @At("HEAD"), cancellable = true)
    private void hexextra$lastNListOfChains(CastingEnvironment env,
                                            CastingImage image,
                                            SpellContinuation continuation,
                                            CallbackInfoReturnable<OperationResult> cir) {
        List<Iota> stack = image.getStack();

        // 栈空时交给原实现去抛"参数不足" —— 我们只加分支,不替它做错误处理
        if (stack.isEmpty()) {
            return;
        }

        // 与原实现同样地取 n:看栈顶那一个,按 getPositiveIntUnderInclusive(0, stack.size()-1)。
        // 若 n 非法,这里抛的 Mishap 与原实现会抛的相同,直接放它往外传。
        List<Iota> topOne = new ArrayList<>(1);
        topOne.add(stack.get(stack.size() - 1));
        int yoinkCount = OperatorUtils.getPositiveIntUnderInclusive(topOne, 0, stack.size() - 1, 0);

        // 参数占了栈顶 1 个,要收拢的是它下面那 n 个
        List<Iota> stackWithoutArg = new ArrayList<>(stack.subList(0, stack.size() - 1));
        if (yoinkCount > stackWithoutArg.size()) {
            return;
        }

        List<Iota> picked = new ArrayList<>(
                stackWithoutArg.subList(stackWithoutArg.size() - yoinkCount, stackWithoutArg.size()));

        // 全是链才合并;否则返回 null,我们把控制权交回原实现
        IfElseIota merged = IfElseChainOps.concatAllOrNull(picked);
        if (merged == null) {
            return;
        }

        List<Iota> rest = new ArrayList<>(
                stackWithoutArg.subList(0, stackWithoutArg.size() - yoinkCount));
        rest.add(merged);

        // CastingImage 是 6 字段的 data class,copy 全是位置参数 —— 顺序写错会把布尔量对调
        CastingImage newImage = image.withUsedOp().copy(rest,
                image.getParenCount(),
                image.getParenthesized(),
                image.getEscapeNext(),
                image.getOpsConsumed(),
                image.getUserData());

        cir.setReturnValue(new OperationResult(
                newImage,
                List.<OperatorSideEffect>of(),   // 与原实现一致:不产生任何副作用
                continuation,
                HexEvalSounds.NORMAL_EXECUTE));
    }
}
