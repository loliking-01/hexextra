package com.llk.hexextra.item.betterspellbook;

import com.llk.hexextra.core.ServerHolder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
 * <p>两端共用:Forge 从 {@code RegisterCommandsEvent} 拿 dispatcher,
 * Fabric 从 {@code CommandRegistrationCallback} 拿,签名一致。
 */
public final class SpellbookCommands {

    /** 记录时间的显示格式:2026-10-19 23:15:42。时区取服务器本地。 */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

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

    private static int access(CommandSourceStack source, ServerPlayer player) {
        var server = source.getServer();
        List<SpellbookAccessLog.AccessRecord> records =
                SpellbookAccessLog.get(server).recordsFor(player.getUUID());

        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    player.getGameProfile().getName() + " 没有任何法术书访问记录"), false);
            return 0;
        }

        // 去重在「记录写入时」已完成,这里只按时间倒序
        var latest = records.stream()
                .sorted(Comparator.comparingLong(SpellbookAccessLog.AccessRecord::accessTimeMillis).reversed())
                .toList();

        source.sendSuccess(() -> Component.literal(
                "=== " + player.getGameProfile().getName() + " 的法术书记录(共 " + latest.size() + " 本)==="), false);

        for (SpellbookAccessLog.AccessRecord r : latest) {
            int pageCount = countPages(server, r.bookId());
            String line = String.format("  %s  [%s]  %d 页  %d 次  %s",
                    r.bookId(), r.source(), pageCount, r.count(),
                    TIME_FORMAT.format(Instant.ofEpochMilli(r.accessTimeMillis())));
            source.sendSuccess(() -> Component.literal(line), false);

            String command = "/hexextra spellbook recover "
                    + player.getGameProfile().getName() + " " + r.bookId();
            source.sendSuccess(() -> Component.literal("    [复制] " + command)
                    .withStyle(style -> style
                            .withColor(0x55FFFF)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, command))), false);
        }
        return latest.size();
    }

    private static int countPages(net.minecraft.server.MinecraftServer server, UUID bookId) {
        CompoundTag data = SpellbookStorage.get(server).get(bookId);
        if (data != null) {
            Tag pages = data.get(ItemSpellbookTagNames.PAGES);
            if (pages instanceof CompoundTag pagesTag) {
                return pagesTag.size();
            }
        }
        return 0;
    }

    private static int recover(CommandSourceStack source, ServerPlayer player, String uuidText) {
        UUID bookId;
        try {
            bookId = UUID.fromString(uuidText);
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("UUID 格式不对: " + uuidText));
            return 0;
        }

        var server = ServerHolder.get();
        if (server == null) {
            source.sendFailure(Component.literal("当前没有可用的服务端实例"));
            return 0;
        }
        CompoundTag data = SpellbookStorage.get(server).get(bookId);
        if (data == null) {
            source.sendFailure(Component.literal("没有这本书的存档数据: " + bookId));
            return 0;
        }

        ItemStack book = SpellbookPersistence.createSnapshot(data, server);
        player.getInventory().placeItemBackInInventory(book);

        source.sendSuccess(() -> Component.literal(
                "已为 " + player.getGameProfile().getName() + " 恢复一本法术书(新 UUID,内容为快照)"), true);
        return 1;
    }

    private SpellbookCommands() {
    }
}