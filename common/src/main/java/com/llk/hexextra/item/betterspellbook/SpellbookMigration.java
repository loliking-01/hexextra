package com.llk.hexextra.item.betterspellbook;

import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import at.petrak.hexcasting.common.lib.HexItems;
import com.llk.hexextra.core.ServerHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 把玩家身上<b>已经带着</b>的法术书纳入文件存储。
 *
 * <p>落盘只发生在内容变更时 ✗ 那些早就写好、之后没再动过的书永远没有 UUID ✗
 * 物品一丢就彻底找不回。所以服务器启动与玩家登录时扫一遍背包:
 * 没有 UUID 的就地分配、写进文件、补一条 {@code MIGRATED} 记录;
 * 已有 UUID 的从文件回填一次组件(文件才是权威)。
 *
 * <p>只扫背包与副手 ✗ 箱子里的书需要玩家拿到手上一次。
 */
public final class SpellbookMigration {

    private static final java.util.UUID MIGRATION_PLAYER_ID = new java.util.UUID(0L, 0L);

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
        if (stack.isEmpty() || !stack.is(HexItems.SPELLBOOK)) {
            return;
        }
        var server = ServerHolder.get();
        if (server == null) {
            return;
        }

        // 已有身份:文件里可能才是最新的,回填一次
        if (SpellbookPersistence.getBookId(stack) != null) {
            SpellbookPersistence.pull(stack, server);
            return;
        }

        if (ItemSpellbook.arePagesEmpty(stack)) {
            return;
        }

        var id = SpellbookPersistence.getOrCreateBookId(stack);
        SpellbookPersistence.flush(stack, server);
        SpellbookAccessLog.get(server).recordAccess(new SpellbookAccessLog.AccessRecord(
                id,
                MIGRATION_PLAYER_ID,
                "MIGRATION",
                System.currentTimeMillis(),
                "MIGRATED",
                1));
    }

    private SpellbookMigration() {
    }
}