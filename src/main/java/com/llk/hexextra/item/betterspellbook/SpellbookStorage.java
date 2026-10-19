package com.llk.hexextra.item.betterspellbook;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 强化法术书的「物理文件」存储:<b>只存书页数据</b>。
 *
 * <p>访问记录在另一个 {@link SpellbookAccessLog} 里(独立的 .dat),
 * 免得频繁写记录时把整份书页也一起重写。
 *
 * <h2>为什么用 {@link SavedData}</h2>
 * 参考 Sophisticated Backpacks 的 {@code BackpackStorage}:整个世界一个
 * {@code world/data/hexextra_spellbooks.dat},里面是 {@code UUID → 书页数据} 的映射。
 */
public class SpellbookStorage extends SavedData {

    /** 存档文件名(不含 .dat)。 */
    public static final String DATA_NAME = "hexextra_spellbooks";

    /** UUID → 该书页面的完整 NBT。 */
    private final Map<UUID, Tag> books = new HashMap<>();

    public SpellbookStorage() {
    }

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

    public Tag get(UUID id) {
        return this.books.get(id);
    }

    public void set(UUID id, Tag tag) {
        this.books.put(id, tag);
        this.setDirty();
    }

    public void remove(UUID id) {
        if (this.books.remove(id) != null) {
            this.setDirty();
        }
    }

    public boolean has(UUID id) {
        return this.books.containsKey(id);
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
            out.books.put(entry.getUUID("id"), entry.get("pages"));
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
        return tag;
    }
}