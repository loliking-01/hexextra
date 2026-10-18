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
 * 强化法术书的「物理文件」存储(0.11.4 版)。
 *
 * <h2>与 1.21.1 版的差异</h2>
 * <ul>
 *   <li>0.11.4 的 {@link SavedData#save(CompoundTag)} 是<b>单参数</b>(1.21.1 多一个
 *       {@code HolderLookup.Provider});</li>
 *   <li>0.11.4 的法术书把书页放在<b>物品 NBT</b> 里,键名有公开常量
 *       ({@code ItemSpellbook.TAG_PAGES} 等),所以本类<b>原样拷贝那几个 tag</b> 即可,
 *       不必关心它们的具体结构。</li>
 * </ul>
 *
 * <h2>为什么用 SavedData</h2>
 * 与参考实现(Sophisticated Backpacks 的 {@code BackpackStorage})一致:
 * 整个世界一个 {@code world/data/hexextra_spellbooks.dat},里面是 {@code UUID → 书页数据}。
 * 文件命名、目录创建、并发写入都交给 {@link net.minecraft.world.level.storage.DimensionDataStorage}。
 */
public class SpellbookStorage extends SavedData {

    /** 存档文件名(不含 .dat)。 */
    public static final String DATA_NAME = "hexextra_spellbooks";

    /** UUID → 该书那几个 NBT tag 的集合。 */
    private final Map<UUID, CompoundTag> books = new HashMap<>();

    public SpellbookStorage() {
    }

    /** 从服务端主世界取存储。所有维度共用同一份。 */
    public static SpellbookStorage get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                SpellbookStorage::load,
                SpellbookStorage::new,
                DATA_NAME);
    }

    // =======================================================================
    // 读写
    // =======================================================================

    /** 拿某本书的数据;没有就新建一份空的并写回。 */
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

    /** 拿某本书的数据;没有就返回 null。 */
    public CompoundTag get(UUID id) {
        return this.books.get(id);
    }

    /** 覆盖写入某本书的数据。 */
    public void set(UUID id, CompoundTag tag) {
        this.books.put(id, tag);
        this.setDirty();
    }

    public void remove(UUID id) {
        if (this.books.remove(id) != null) {
            this.setDirty();
        }
    }

    // =======================================================================
    // 序列化(0.11.4 单参数)
    // =======================================================================

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
