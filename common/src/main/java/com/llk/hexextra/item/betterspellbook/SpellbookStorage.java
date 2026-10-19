package com.llk.hexextra.item.betterspellbook;

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
 */
public class SpellbookStorage extends SavedData {

    public static final String DATA_NAME = "hexextra_spellbooks";

    /** UUID → 该书那几个 NBT tag 的集合。 */
    private final Map<UUID, CompoundTag> books = new HashMap<>();

    public SpellbookStorage() {
    }

    public static SpellbookStorage get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                SpellbookStorage::load,
                SpellbookStorage::new,
                DATA_NAME);
    }

    public CompoundTag getOrCreate(UUID id) {
        CompoundTag existing = this.books.get(id);
        if (existing != null) {
            return existing;
        }
        CompoundTag fresh = new CompoundTag();
        this.books.put(id, fresh);
        this.setDirty();
        return fresh;
    }

    public CompoundTag get(UUID id) {
        return this.books.get(id);
    }

    public void set(UUID id, CompoundTag tag) {
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

    public static SpellbookStorage load(CompoundTag tag) {
        SpellbookStorage out = new SpellbookStorage();
        ListTag list = tag.getList("books", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("id")) {
                continue;
            }
            out.books.put(entry.getUUID("id"), entry.getCompound("data"));
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, CompoundTag> e : this.books.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", e.getKey());
            entry.put("data", e.getValue());
            list.add(entry);
        }
        tag.put("books", list);
        return tag;
    }
}