package com.llk.hexextra.item.betterspellbook;

import net.minecraft.core.HolderLookup;
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
 * 强化法术书的<b>访问记录</b>,独立存一个文件。
 *
 * <h2>为什么和书页数据分开</h2>
 * 书页是玩家最在乎的数据 ✗ 而访问记录是辅助信息 ✗ 混在一个 {@link SavedData} 里时,
 * 每写一次记录都会把<b>整个文件</b>(含全部书页)标记为脏 ✗ 保存时整份重新序列化。
 *
 * <p>拆开之后:
 * <ul>
 *   <li>记录再怎么增长,也不会影响书页那一份的写入量;</li>
 *   <li>任何一个文件损坏都不会连累另一个。</li>
 * </ul>
 *
 * <h2>去重在写入时完成</h2>
 * 同一本书 + 同一个玩家只保留一条,重复写入只推进「最近访问时间」并累加次数 ——
 * 这样记录条数只和"不同的书"数量相关,不会被反复书写刷爆。
 */
public class SpellbookAccessLog extends SavedData {

    /** 存档文件名(不含 .dat)。 */
    public static final String DATA_NAME = "hexextra_spellbook_access";

    /** 记录条数上限,超过就丢最旧的。 */
    private static final int MAX_ENTRIES = 2000;

    /**
     * 一条访问记录。
     *
     * @param accessTimeMillis 记录时刻,<b>系统时间</b>(epoch 毫秒),显示时按服务器时区格式化
     * @param count  累计次数(去重后只增这个数字)
     */
    public record AccessRecord(UUID bookId, UUID playerId, String playerName,
                               long accessTimeMillis, String source, int count) {
    }

    private final List<AccessRecord> entries = new ArrayList<>();

    public SpellbookAccessLog() {
    }

    /** 从服务端主世界取日志。所有维度共用同一份。 */
    public static SpellbookAccessLog get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        SpellbookAccessLog::new,
                        SpellbookAccessLog::load,
                        null),
                DATA_NAME);
    }

    // =======================================================================
    // 记录
    // =======================================================================

    /**
     * 记录一次访问。同一本书 + 同一个玩家只保留一条。
     */
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

    /** 某个玩家碰过的全部书(未排序)。 */
    public List<AccessRecord> recordsFor(UUID playerId) {
        return this.entries.stream()
                .filter(r -> r.playerId().equals(playerId))
                .toList();
    }

    /** 全部记录(给管理指令用)。 */
    public List<AccessRecord> allRecords() {
        return List.copyOf(this.entries);
    }

    // =======================================================================
    // 序列化
    // =======================================================================

    public static SpellbookAccessLog load(CompoundTag tag, HolderLookup.Provider registries) {
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
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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