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
     */
    public static CastingImage withStack(CastingImage image, TreeList<Iota> stack) {
        return image.copy(
                stack,
                image.getParenCount(),
                image.getParenthesized(),
                image.getEscapeNext(),
                image.getSimulateNext(),
                image.getOpsConsumed(),
                image.getComponents());
    }

    //这也开一个类？我推荐用kt写图案（而不是java） 因为它允许 image.copy(stack = stack) 或者用 com/llk/hexextra/helper/HexExtraHelpers.kt 这种写法 来直接加方法  在java侧调用也不难
}
