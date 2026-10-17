package com.llk.hexextra;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * hexextra 的配置骨架。
 *
 * <p>目前刻意不定义任何配置项,只把结构立起来。要加选项时:
 * <ol>
 *   <li>在 {@link #BUILDER} 上挂定义,例如
 *       {@code private static final ModConfigSpec.IntValue MAX_X = BUILDER.defineInRange("maxX", 10, 0, 100);}</li>
 *   <li>在下面加一个同名的 {@code public static int maxX;} 字段</li>
 *   <li>加一个 {@code @SubscribeEvent static void onLoad(ModConfigEvent event)} 把值读进字段</li>
 * </ol>
 *
 * <p>要收到配置加载/重载事件,给本类加上
 * {@code @EventBusSubscriber(modid = Hexextra.MODID)} 即可
 * (NeoForge 21.1 起不需要再写 {@code bus = EventBusSubscriber.Bus.MOD})。
 */
public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
