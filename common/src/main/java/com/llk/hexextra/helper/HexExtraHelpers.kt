package com.llk.hexextra.helper

import at.petrak.hexcasting.api.casting.eval.vm.CastingImage
import at.petrak.hexcasting.api.casting.iota.Iota

/**
 * 施法状态的复制助手(Kotlin 版)。
 *
 * <p>Java 侧调 `CastingImage.copy(...)` 只能按位置传 6 个参数;
 * 在这里用具名参数 `copy(stack = stack)` 只改一个字段,位置传错的风险从根上消失。
 *
 * <p>按 AGENTS.md 规则 3:这类"工具扩展"是 Kotlin 唯一允许出现的地方,
 * 玩法逻辑仍在 Java 里。
 */
fun CastingImage.withStack(stack: List<Iota>): CastingImage {
    return this.copy(stack = stack)
}