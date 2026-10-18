package com.llk.hexextra.core;

import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;

import java.util.List;

/**
 * 施法状态的复制助手。
 *
 * <h2>为什么值得单独抽出来</h2>
 * {@code CastingImage} 是 Kotlin 的 data class,Kotlin 侧写 {@code image.copy(stack = newStack)}
 * 靠具名参数;Java 没有这个语法,只能把 <b>6 个位置参数</b>挨个传一遍:
 * <pre>
 *   stack, parenCount, parenthesized, escapeNext, opsConsumed, userData
 * </pre>
 *
 * <p>0.11.4 的字段里只有一个 {@code escapeNext} 是 boolean(0.12.0 才多出一个相邻的
 * {@code simulateNext},那里对调的风险更大),但仍然没必要在每个用到的地方抄一遍 ——
 * 本工程一度在 {@code FrameIfElse}、{@code IfElseSpecialHandlers}、{@code SwitchIota}
 * 三处各写了一份完全相同的私有实现,任何一处将来漏改都会造成行为不一致。
 */
public final class CastingImages {

    private CastingImages() {
    }

    /**
     * 只替换栈,其余字段(括号状态、转义标志、算力计数、附加数据)原样保留。
     */
    public static CastingImage withStack(CastingImage image, List<Iota> stack) {
        return image.copy(
                stack,
                image.getParenCount(),
                image.getParenthesized(),
                image.getEscapeNext(),
                image.getOpsConsumed(),
                image.getUserData());
    }
}
