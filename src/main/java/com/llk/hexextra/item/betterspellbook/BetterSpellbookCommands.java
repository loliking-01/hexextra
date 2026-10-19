package com.llk.hexextra.item.betterspellbook;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 管理指令 —— <b>仅 op</b>。
 *
 * <pre>
 * /hexextra spellbook access  &lt;player&gt;          查看该玩家的法术书访问记录
 * /hexextra spellbook recover &lt;player&gt; &lt;uuid&gt;   按记录恢复一本(快照,新 UUID)
 * </pre>
 *
 * <h2>为什么仅 op</h2>
 * 恢复指令等于<b>凭空造物品</b> ✗ 权限必须收到 4 级(仅 op),
 * 否则任何管理员都能无限刷法术书。
 */
public final class BetterSpellbookCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hexextra")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("spellbook")
                        .then(Commands.literal("access")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> access(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("recover")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("uuid", StringArgumentType.string())
                                                .executes(ctx -> recover(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "uuid"))))))));
    }

    // =======================================================================
    // /hexextra spellbook access <player>
    // =======================================================================

    private static int access(CommandSourceStack source, ServerPlayer player) {
        var server = source.getServer();
        List<SpellbookStorage.AccessRecord> records =
                SpellbookStorage.get(server).recordsFor(player.getUUID());

        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    player.getGameProfile().getName() + " 没有任何强化法术书访问记录"), false);
            return 0;
        }

        // 去重在「记录写入时」就已经做过了(同一本书 + 同一个玩家只保留一条,
        // 重复书写只推进时间),所以这里直接按时间倒序列出即可。
        var latest = records.stream()
                .sorted(Comparator.comparingLong(SpellbookStorage.AccessRecord::gameTime).reversed())
                .toList();

        source.sendSuccess(() -> Component.literal(
                "=== " + player.getGameProfile().getName() + " 的强化法术书记录(共 " + latest.size() + " 本)==="), false);

        for (SpellbookStorage.AccessRecord r : latest) {
            int pageCount = countPages(server, r.bookId());
            String line = String.format("  %s  [%s]  %d 页  %d 次  tick %d",
                    r.bookId(), r.source(), pageCount, r.count(), r.gameTime());
            source.sendSuccess(() -> Component.literal(line), false);
            source.sendSuccess(() -> Component.literal(
                    "    恢复: /hexextra spellbook recover " + player.getGameProfile().getName() + " " + r.bookId()), false);
        }
        return latest.size();
    }

    private static int countPages(net.minecraft.server.MinecraftServer server, UUID bookId) {
        Tag data = SpellbookStorage.get(server).get(bookId);
        if (data instanceof CompoundTag tag) {
            Tag pages = tag.get("pages");
            if (pages instanceof CompoundTag pagesTag) {
                return pagesTag.size();
            }
        }
        return 0;
    }

    // =======================================================================
    // /hexextra spellbook recover <player> <uuid>
    // =======================================================================

    private static int recover(CommandSourceStack source, ServerPlayer player, String uuidText) {
        UUID bookId;
        try {
            bookId = UUID.fromString(uuidText);
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("UUID 格式不对: " + uuidText));
            return 0;
        }

        var server = source.getServer();
        Tag data = SpellbookStorage.get(server).get(bookId);
        if (!(data instanceof CompoundTag tag)) {
            source.sendFailure(Component.literal("没有这本书的存档数据: " + bookId));
            return 0;
        }

        ItemStack book = BetterSpellbookItem.createSnapshot(tag, server);
        player.getInventory().placeItemBackInInventory(book);

        source.sendSuccess(() -> Component.literal(
                "已为 " + player.getGameProfile().getName() + " 恢复一本强化法术书(新 UUID,内容为快照)"), true);
        return 1;
    }

    private BetterSpellbookCommands() {
    }
}