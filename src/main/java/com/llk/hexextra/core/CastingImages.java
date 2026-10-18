package com.llk.hexextra.core;

import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.utils.TreeList;

/**
 * 施法状态的复制助手。
 *
 * <h2>为什么值得单独抽出来</h2>
 * {@code CastingImage} 是 Kotlin 的 data class,Kotlin 侧写 {@code image.copy(stack = newStack)}
 * 靠具名参数;Java 没有这个语法,只能把 <b>7 个位置参数</b>挨个传一遍:
 * <pre>
 *   stack, parenCount, parenthesized, escapeNext, simulateNext, opsConsumed, components
 * </pre>
 * 而其中 {@code escapeNext} 与 {@code simulateNext} 是<b>两个相邻的 boolean</b> ——
 * 写成 {@code image.copy(stack, 0, empty, true, false, ...)} 这种形式时,
 * 对调这两个值编译器<b>不会报错</b>,只会静默地把"转义"当成"空跑",行为诡异且极难排查。
 *
 * <p>本项目里至少有三处需要"只换栈"的复制(循环帧、条件链帧、Mixin 里的 last_n_list),
 * 与其各写一遍、各自冒一次对调风险,不如只在这里写一次。
 */
public final class CastingImages {

    private CastingImages() {
    }

    /**
     * 只替换栈,其余字段(括号状态、转义/空跑标志、算力计数、附加组件)原样保留。
     *
     * <p><b>实现委托给 Kotlin 扩展</b> {@code HexExtraHelpersKt.withStack}:那边可以写
     * {@code copy(stack = stack)} 用具名参数只改一个字段。若在这里用位置参数手抄 7 个值,
     * 就必须把 {@code escapeNext} 与 {@code simulateNext} 这两个相邻 boolean 摆对 ——
     * 摆反了编译器不报错,只会静默地把"转义"当成"空跑"。
     *
     * <p>所以:实现只留 Kotlin 那一份,这里只做一层 Java 友好的门面。
     */
    public static CastingImage withStack(CastingImage image, TreeList<Iota> stack) {
        return com.llk.hexextra.helper.HexExtraHelpersKt.withStack(image, stack);
    }

}
