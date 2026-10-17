package com.llk.hexextra;

/**
 * 与加载器无关的公共代码入口。
 *
 * <p>这个类目前只是占位,用来验证 common 模块能被两个加载器模块正确编译进来。
 * 后续三个功能(for 循环 / 条件链 / 多路分派)的 iota、帧与算术都会放在本模块里 ——
 * 因为它们只依赖咒法学 API 与 Minecraft 原版,不依赖 Forge 或 Fabric。
 */
public final class HexextraCommon {

    /** 模组 id,与 gradle.properties 里的 modID 保持一致。 */
    public static final String MOD_ID = "hexextra";

    private HexextraCommon() {
    }
}