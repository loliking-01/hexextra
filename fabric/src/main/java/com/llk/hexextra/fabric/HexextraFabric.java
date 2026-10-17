package com.llk.hexextra.fabric;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.common.lib.hex.HexActions;
import at.petrak.hexcasting.common.lib.hex.HexArithmetics;
import at.petrak.hexcasting.common.lib.hex.HexContinuationTypes;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import com.llk.hexextra.core.HexextraContent;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 侧的入口 —— 与 Forge 侧一一对应的<b>注册胶水</b>。
 *
 * <h2>与 Forge 侧的唯一差别:注册方式</h2>
 * Forge 有 {@code DeferredRegister} 与注册事件,可以直接吃注册表键;
 * Fabric 没有那套,得拿到<b>活的</b> {@link Registry} 实例再往里 {@code register}。
 * 咒法学 0.11.4 把这三张表都暴露出来了:
 * {@link HexIotaTypes#REGISTRY} / {@link HexArithmetics#REGISTRY} / {@link HexActions#REGISTRY}
 * (它们内部是 {@code IXplatAbstractions.INSTANCE.getXxxRegistry()},即两侧共用同一张表)。
 *
 * <p>两边喂的内容都来自 {@link HexextraContent},所以不会出现两版行为不一致。
 */
public final class HexextraFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("hexextra");

    @Override
    public void onInitialize() {
        for (HexextraContent.Entry<IotaType<?>> entry : HexextraContent.iotaTypes()) {
            Registry.register(HexIotaTypes.REGISTRY, entry.id(), entry.value());
        }
        for (HexextraContent.Entry<Arithmetic> entry : HexextraContent.arithmetics()) {
            Registry.register(HexArithmetics.REGISTRY, entry.id(), entry.value());
        }
        for (HexextraContent.Entry<at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame.Type<?>> entry : HexextraContent.frameTypes()) {
            Registry.register(HexContinuationTypes.REGISTRY, entry.id(), entry.value());
        }
        for (HexextraContent.Entry<ActionRegistryEntry> entry : HexextraContent.actions()) {
            Registry.register(HexActions.REGISTRY, entry.id(), entry.value());
        }

        LOGGER.info("hexextra: 注册完成 —— iota 类型 2 个、算术 1 个、图案 1 个");
    }
}
