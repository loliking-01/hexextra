package com.llk.hexextra.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.common.casting.actions.lists.OpSplat;
import com.llk.hexextra.pattern.if_else.IfElseChainOps;
import com.llk.hexextra.pattern.if_else.IfElseIota;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 让「群体之拆解」({@code splat},Flock's Disintegration)也能拆条件链。
 *
 * <h2>为什么必须用 Mixin</h2>
 * 算术重载那条路走不通:{@code splat} 与 {@code last_n_list} 在咒法学里是
 * <b>普通的 Action / ConstMediaAction</b>,不经过算术引擎,所以没有官方重载入口
 * (算术引擎的 {@code Arithmetic} 接口只对"走算术的图案"有效)。
 * 剩下两条路:
 * <ul>
 *   <li>注册一个<b>同签名</b>的图案把原版顶掉 —— 但那会让原始行为整体失效,
 *       还得把原逻辑照抄一遍,且胜负取决于注册顺序,很脆;</li>
 *   <li><b>Mixin 注入</b> —— 在方法开头加一个分支:是条件链就走我们的逻辑,
 *       否则<b>原样放行</b>。原方法一行都不用改,别的类型行为完全不受影响。</li>
 * </ul>
 * 这里选后者。
 *
 * <h2>注入方式</h2>
 * {@code @At("HEAD")} + {@code cancellable}:只在参数是条件链时设置返回值并取消原方法,
 * 其余情况直接返回、不取消,于是原实现(包括它自己的报错)照常执行。
 * 这样"非条件链"的行为与没装本模组时<b>完全一致</b>。
 *
 * <h2>行为</h2>
 * 原功能是「把列表摊开成一个一个元素压栈」。对条件链的对应语义就是
 * <b>把链摊成一条条单分支链</b>:每个分支压成一个单分支链;若链带 else,
 * 最后再压一个"仅 else 链"。详见 {@link IfElseChainOps#splat}。
 */
@Mixin(OpSplat.class)
public class MixinOpSplat {

    /**
     * @param args 原方法收到的参数(此处恰好 1 个:待拆的 iota)
     * @param env  施法环境(本注入用不到,但必须与原方法签名一致才能注进去)
     * @param cir  返回值回调;设置它并由此取消原方法
     */
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, remap = false)
    private void hexextra$splatIfElseChain(List<? extends Iota> args,
                                           CastingEnvironment env,
                                           CallbackInfoReturnable<List<Iota>> cir) {
        // 原方法的 argc 是 1,这里仍做一次防御性检查,避免将来上游改了参数个数时越界
        if (args.size() == 1 && args.get(0) instanceof IfElseIota chain) {
            cir.setReturnValue(IfElseChainOps.splat(chain));
        }
        // 不是条件链 → 什么都不做,原实现照常跑(列表照常摊开,非列表照常报错)
    }
}
