package com.llk.hexextra;

import com.llk.hexextra.core.HexextraIotaTypes;
import com.llk.hexextra.core.HexextraRegistries;
import com.llk.hexextra.pattern.for_loop.ForLoopFeature;
import com.llk.hexextra.pattern.if_else.IfElseFeature;
import com.llk.hexextra.pattern.switch_case.SwitchFeature;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * hexextra —— 咒法学(Hex Casting)附属模组主类。
 *
 * <p>这里只保留最小骨架,没有任何模板示例内容。
 * 图案/操作符的注册入口在构造器里接。
 *
 * <p>咒法学 1.21.1-0.12.0-devel-pre-64 的 API 入口(已实测可用):
 * <ul>
 *   <li>{@code at.petrak.hexcasting.api.casting.castables.SpellAction} —— 操作符要实现的接口</li>
 *   <li>{@code at.petrak.hexcasting.api.casting.iota.Iota} —— 栈上的数据类型(旧版叫 SpellDatum)</li>
 *   <li>{@code at.petrak.hexcasting.api.casting.eval.CastingEnvironment} —— 施法环境(旧版叫 CastingContext)</li>
 *   <li>{@code at.petrak.hexcasting.api.casting.math.HexPattern} / {@code HexDir} —— 图案与方向</li>
 *   <li>{@code at.petrak.hexcasting.common.lib.hex.HexActions} —— 图案到操作符的注册表</li>
 * </ul>
 *
 * <p>注意:网上流传的附属开发指南(Hexal/addon_guide)是 1.18/1.19 时代的,
 * 里面的 {@code api.spell.*} 包名、{@code ConstManaOperator}、{@code fg.deobf} 在 1.21 上全部不适用。
 */
@Mod(Hexextra.MODID)
public class Hexextra {

    /** 模组 ID,必须与 gradle.properties 的 mod_id 以及 neoforge.mods.toml 一致。 */
    public static final String MODID = "hexextra";

    public static final Logger LOGGER = LogUtils.getLogger();

    public Hexextra(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // ---- 刷新各功能的注册项 ----
        // 每个功能在自己的文件夹里把注册项挂进 core 的 DeferredRegister,
        // 这里是唯一需要「点名」它们的地方。新增功能时在这行下面加一句即可。
        // 注意顺序:必须在 registerAll 之前完成,否则注册表已经交给事件总线了。
        HexextraIotaTypes.init();
        ForLoopFeature.init();
        com.llk.hexextra.pattern.for_each.ForEachFeature.init();
        IfElseFeature.init();
        SwitchFeature.init();

        // ---- 把注册表挂到模组事件总线 ----
        // 法术书的 UUID 组件(书页存在文件里,物品上只留这个)
        HexextraRegistries.DATA_COMPONENT_TYPES.register("book_id",
                () -> com.llk.hexextra.item.betterspellbook.SpellbookPersistence.BOOK_ID);

        HexextraRegistries.registerAll(modEventBus);

        // 把玩家身上已有的法术书纳入文件存储(首次加载 / 每次登录)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStartedEvent event) -> {
                    for (var player : event.getServer().getPlayerList().getPlayers()) {
                        com.llk.hexextra.item.betterspellbook.SpellbookMigration.convertPlayer(player);
                    }
                });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
                    if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                        com.llk.hexextra.item.betterspellbook.SpellbookMigration.convertPlayer(player);
                    }
                });

        // 管理指令(仅 op):/hexextra spellbook access|recover
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.RegisterCommandsEvent event) ->
                        com.llk.hexextra.item.betterspellbook.BetterSpellbookCommands.register(event.getDispatcher()));



        // ---- 启动自检 ----
        // 算术引擎是懒构建的,若某个图案的算子 arity 写错,会在玩家第一次施法时才炸,
        // 且会连带整个算术系统瘫痪。这里在注册完成后立刻用快照造一个一次性引擎跑一遍校验,
        // 把"游戏中途炸"提前成"启动即报错"。
        // 之所以必须挂在 CommonSetup 而不是构造器里:构造器阶段其它模组的算术还没落表。
        //
        // 第二项自检是条件链的 NBT 编解码往返:咒法学对 NBT 侧的失败是"静默退化成 GarbageIota",
        // 编解码器写歪会让玩家存档悄悄损坏且当场无提示,所以必须提前验。
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            IfElseFeature.checkArithmeticConsistency();
            IfElseFeature.checkCodecRoundTrip();
            SwitchFeature.checkCodecRoundTrip();
        }));

        LOGGER.info("hexextra: 注册项已全部提交");
    }

    /** 生成 hexextra 命名空间下的 ResourceLocation,写注册代码时会经常用到。 */
    public static ResourceLocation modLoc(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
