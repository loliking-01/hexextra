package com.llk.hexextra.item.betterspellbook;

import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import at.petrak.hexcasting.common.lib.HexItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 把玩家身上<b>已经带着</b>的法术书纳入文件存储。
 *
 * <h2>为什么需要它</h2>
 * 落盘只发生在"书写"时 ✗ 那些<b>早就写好内容、但之后没再动过</b>的书
 * 永远不会有 UUID ✗ 也就永远不会进文件 ✗ 一旦物品本身丢失就彻底找不回。
 *
 * <p>所以在服务器启动与玩家登录时扫一遍背包:
 * 没有 {@link BetterSpellbookItem#BOOK_ID} 的法术书就地分配 UUID、
 * 把当时的书页写进文件,并补一条 {@code MIGRATED} 访问记录 ✗
 * 这样"找回"指令对它们也可用。
 *
 * <h2>扫描范围</h2>
 * 只扫玩家<b>背包与副手</b> ✗ 箱子、展示框里的书不会自动转化 ✗
 * 需要的话玩家把它拿在手上一次即可。
 */
public final class SpellbookMigration {

    public static void convertPlayer(ServerPlayer player) {
        var inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            convert(stack);
        }
        for (ItemStack stack : inventory.offhand) {
            convert(stack);
        }
    }

    private static void convert(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(HexItems.SPELLBOOK.get())) {
            return;
        }
        // 已经有身份:说明之前写完就落过盘,不用管
        if (BetterSpellbookItem.getBookId(stack) != null) {
            return;
        }
        // 空书不值得建条目(没有任何内容可丢)
        if (ItemSpellbook.arePagesEmpty(stack)) {
            return;
        }

        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        var id = BetterSpellbookItem.getOrCreateBookId(stack);
        BetterSpellbookItem.flush(stack, server);
        SpellbookAccessLog.get(server).recordAccess(new SpellbookAccessLog.AccessRecord(
                id,
                MIGRATION_PLAYER_ID,
                "MIGRATION",
                server.overworld().getGameTime(),
                "MIGRATED",
                1));
    }

    /** 迁移记录用的伪玩家 UUID(全零),方便和真实玩家区分。 */
    private static final java.util.UUID MIGRATION_PLAYER_ID = new java.util.UUID(0L, 0L);

    private SpellbookMigration() {
    }
}