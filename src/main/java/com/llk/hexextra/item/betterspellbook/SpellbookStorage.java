package com.llk.hexextra.item.betterspellbook;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 强化法术书的「物理文件」存储。
 *
 * <h2>为什么用 {@link SavedData}</h2>
 * 参考 Sophisticated Backpacks 的 {@code BackpackStorage}:整个世界一个
 * {@code world/data/hexextra_spellbooks.dat},里面是 {@code UUID → 书页数据} 的映射。
 *
 * <h2>为什么键要用 UUID 而不是物品</h2>
 * 物品栈本身没有身份(可以复制、可以拆叠),而存档里的"一份书页"必须有个稳定标识。
 * 所以每本强化法术书分配一个 UUID 存进物品组件;读取时拿这个 UUID 来这里查。
 *
 * <h2>访问记录</h2>
 * 除书页数据外,本类还维护一张「谁碰过哪本书」的记录表 ——
 * 由书写图案写入时精确记录({@code WRITTEN}),供管理指令查询与恢复。
 */
public class SpellbookStorage extends SavedData {

    /** 存档文件名(不含 .dat)。 */
    public static final String DATA_NAME = "hexextra_spellbooks";

    /** 访问记录上限,防止无限增长。 */
    private static final int MAX_LOG = 2000;

    /** UUID → 该书页面的完整 NBT。 */
    private final Map<UUID, Tag> books = new HashMap<>();

    /** 访问记录(按发生顺序追加)。 */
    private final List<AccessRecord> accessLog = new ArrayList<>();

    /**
     * 一条访问记录。
     *
     * @param source 记录来源:{@code WRITTEN} = 通过书写图案写入(精确)
     */
    public record AccessRecord(UUID bookId, UUID playerId, String playerName,
                               long gameTime, String source, int count) {
    }

    public SpellbookStorage() {
    }

    // =======================================================================
    // 取实例
    // =======================================================================

    /** 从服务端主世界取存储。所有维度共用同一份。 */
    public static SpellbookStorage get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        SpellbookStorage::new,
                        SpellbookStorage::load,
                        null),
                DATA_NAME);
    }

    // =======================================================================
    // 读写
    // =======================================================================

    /** 拿某本书的数据;没有就新建一份空的并写回。 */
    public Tag getOrCreate(UUID id) {
        Tag existing = this.books.get(id);
        if (existing != null) {
            return existing;
        }
        CompoundTag fresh = new CompoundTag();
        this.books.put(id, fresh);
        this.setDirty();
        return fresh;
    }

    /** 拿某本书的数据;没有就返回 null(调用方按"空书"处理)。 */
    public Tag get(UUID id) {
        return this.books.get(id);
    }

    /** 覆盖写入某本书的数据。 */
    public void set(UUID id, Tag tag) {
        this.books.put(id, tag);
        this.setDirty();
    }

    /** 书被彻底销毁时调用(目前没接任何东西,留着给将来用)。 */
    public void remove(UUID id) {
        if (this.books.remove(id) != null) {
            this.setDirty();
        }
    }

    public boolean has(UUID id) {
        return this.books.containsKey(id);
    }

    // =======================================================================
    // 访问记录
    // =======================================================================

    /**
     * 记录一次访问。
     *
     * <p><b>同一本书 + 同一个玩家只保留一条</b> —— 反复书写不会刷屏,
     * 只把「最近访问时间」推进到这次,并把访问次数加一。
     *
     * <p>这样记录表的 2000 条上限留给<b>真正不同的书</b>,
     * 而不是被同一本书的重复记录占满。
     */
    public void recordAccess(AccessRecord incoming) {
        for (int i = 0; i < this.accessLog.size(); i++) {
            AccessRecord existing = this.accessLog.get(i);
            if (existing.bookId().equals(incoming.bookId())
                    && existing.playerId().equals(incoming.playerId())) {
                this.accessLog.set(i, new AccessRecord(
                        existing.bookId(), existing.playerId(), incoming.playerName(),
                        incoming.gameTime(), incoming.source(), existing.count() + 1));
                this.setDirty();
                return;
            }
        }
        this.accessLog.add(incoming);
        while (this.accessLog.size() > MAX_LOG) {
            this.accessLog.remove(0);
        }
        this.setDirty();
    }

    /** 某个玩家碰过的全部书(按时间顺序)。 */
    public List<AccessRecord> recordsFor(UUID playerId) {
        return this.accessLog.stream()
                .filter(r -> r.playerId().equals(playerId))
                .toList();
    }

    /** 全部记录(给管理指令用)。 */
    public List<AccessRecord> allRecords() {
        return List.copyOf(this.accessLog);
    }

    // =======================================================================
    // 序列化
    // =======================================================================

    public static SpellbookStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        SpellbookStorage out = new SpellbookStorage();
        ListTag list = tag.getList("books", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("id")) {
                continue;
            }
            UUID id = entry.getUUID("id");
            Tag pages = entry.get("pages");
            out.books.put(id, pages);
        }

        ListTag log = tag.getList("access_log", Tag.TAG_COMPOUND);
        for (int i = 0; i < log.size(); i++) {
            CompoundTag e = log.getCompound(i);
            if (!e.hasUUID("book") || !e.hasUUID("player")) {
                continue;
            }
            out.accessLog.add(new AccessRecord(
                    e.getUUID("book"), e.getUUID("player"),
                    e.getString("name"), e.getLong("time"), e.getString("source"),
                    e.contains("count") ? e.getInt("count") : 1));
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Tag> e : this.books.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", e.getKey());
            entry.put("pages", e.getValue());
            list.add(entry);
        }
        tag.put("books", list);

        ListTag log = new ListTag();
        for (AccessRecord r : this.accessLog) {
            CompoundTag e = new CompoundTag();
            e.putUUID("book", r.bookId());
            e.putUUID("player", r.playerId());
            e.putString("name", r.playerName());
            e.putLong("time", r.gameTime());
            e.putString("source", r.source());
            e.putInt("count", r.count());
            log.add(e);
        }
        tag.put("access_log", log);
        return tag;
    }
}
