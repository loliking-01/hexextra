package com.llk.hexextra.item.betterspellbook;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 法术书的<b>访问记录</b>,独立存一个文件(和书页分开)。
 *
 * <p>同一本书 + 同一个玩家只保留一条,重复书写只推进「最近访问时间」并累加次数。
 * 时间是<b>系统时间</b>(epoch 毫秒),显示时按服务器时区格式化。
 */
public class SpellbookAccessLog extends SavedData {

    public static final String DATA_NAME = "hexextra_spellbook_access";

    private static final int MAX_ENTRIES = 2000;

    /**
     * @param accessTimeMillis 记录时刻,系统时间
     * @param source           {@code WRITTEN} / {@code MIGRATED}
     */
    public record AccessRecord(UUID bookId, UUID playerId, String playerName,
                               long accessTimeMillis, String source, int count) {
    }

    private final List<AccessRecord> entries = new ArrayList<>();

    public SpellbookAccessLog() {
    }

    public static SpellbookAccessLog get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                SpellbookAccessLog::load,
                SpellbookAccessLog::new,
                DATA_NAME);
    }

    public void recordAccess(AccessRecord incoming) {
        for (int i = 0; i < this.entries.size(); i++) {
            AccessRecord existing = this.entries.get(i);
            if (existing.bookId().equals(incoming.bookId())
                    && existing.playerId().equals(incoming.playerId())) {
                this.entries.set(i, new AccessRecord(
                        existing.bookId(), existing.playerId(), incoming.playerName(),
                        incoming.accessTimeMillis(), incoming.source(), existing.count() + 1));
                this.setDirty();
                return;
            }
        }
        this.entries.add(incoming);
        while (this.entries.size() > MAX_ENTRIES) {
            this.entries.remove(0);
        }
        this.setDirty();
    }

    public List<AccessRecord> recordsFor(UUID playerId) {
        return this.entries.stream()
                .filter(r -> r.playerId().equals(playerId))
                .toList();
    }

    public List<AccessRecord> allRecords() {
        return List.copyOf(this.entries);
    }

    public static SpellbookAccessLog load(CompoundTag tag) {
        SpellbookAccessLog out = new SpellbookAccessLog();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (!e.hasUUID("book") || !e.hasUUID("player")) {
                continue;
            }
            out.entries.add(new AccessRecord(
                    e.getUUID("book"), e.getUUID("player"),
                    e.getString("name"), e.getLong("time"), e.getString("source"),
                    e.contains("count") ? e.getInt("count") : 1));
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (AccessRecord r : this.entries) {
            CompoundTag e = new CompoundTag();
            e.putUUID("book", r.bookId());
            e.putUUID("player", r.playerId());
            e.putString("name", r.playerName());
            e.putLong("time", r.accessTimeMillis());
            e.putString("source", r.source());
            e.putInt("count", r.count());
            list.add(e);
        }
        tag.put("entries", list);
        return tag;
    }
}