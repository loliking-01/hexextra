package com.llk.hexextra.core;

import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;

import java.util.List;

/**
 * {@link CastingImage} 的 Java 侧小工具。
 *
 * <h2>⚠ 为什么这里是「直接构造」而不是 Kotlin 的 copy(...)</h2>
 * Kotlin data class 的 {@code copy(...)} 在字节码里会生成一个<b>合成方法</b>
 * {@code copy$default}(带 {@code int mask} + {@code Object marker} 两个额外参数)。
 * Fabric 的 loom 在重映射模组依赖时<b>不会处理这类合成方法</b> ✗ 于是它的描述符里
 * 仍然带着 Mojmap 的 {@code CompoundTag},而运行时的咒法学用的是另一套映射 ——
 * 调用时直接 {@code NoSuchMethodError},整个服务端崩掉。
 *
 * <p>构造函数 {@link CastingImage#CastingImage(List, int, List, boolean, long, net.minecraft.nbt.CompoundTag)}
 * 是<b>真实方法</b> ✗ 会被正常重映射 ✓ 所以这里按「原样复制其余五个字段 + 换掉栈」
 * 的方式重建一个实例,绕开那个合成方法。
 */
public final class CastingImages {

    /** 用新的栈重建一个 {@link CastingImage},其余字段保持原样。 */
    public static CastingImage withStack(CastingImage image, List<Iota> stack) {
        return new CastingImage(
                stack,
                image.getParenCount(),
                image.getParenthesized(),
                image.getEscapeNext(),
                image.getOpsConsumed(),
                image.getUserData());
    }

    private CastingImages() {
    }
}