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
 * 强化法术书的「物理文件」存储。
 *
 * <h2>为什么用 {@link SavedData}</h2>
 * 参考 Sophisticated Backpacks 的 {@code BackpackStorage}:整个世界一个
 * {@code world/data/hexextra_spellbooks.dat},里面是 {@code UUID → 书页数据} 的映射。
 * 这样既满足"数据不在物品组件里",又不用自己处理目录创建、文件命名、并发写入 ——
 * 原版 {@code DimensionDataStorage} 已经把这些都做掉了。
 *
 * <h2>为什么键要用 UUID 而不是物品</h2>
 * 物品栈本身没有身份(可以复制、可以拆叠),而存档里的"一份书页"必须有个稳定标识。
 * 所以每本强化法术书在合成时分配一个 UUID,存进物品组件;读取时拿这个 UUID 来这里查。
 *
 * <h2>共享是特性</h2>
 * 玩家复制物品会得到两个 UUID 相同的强化法术书 —— 它们共享同一份书页。
 * 这是刻意保留的行为,不做去重。
 *
 * <h2>服务器权威</h2>
 * 本类只在服务端存在。客户端拿到的书页来自物品组件里的缓存副本
 * (见 {@link BetterSpellbookItem}),因为咒法学法术书界面的翻页逻辑全是
 * {@code ItemSpellbook} 的静态方法,只能读组件,改不了。
 */
public class SpellbookStorage extends SavedData {

    /** 存档文件名(不含 .dat)。 */
    public static final String DATA_NAME = "hexextra_spellbooks";

    /** UUID → 该书页面的完整 NBT(结构与咒法学原版 HEXCASTING_DATA 一致)。 */
    private final Map<UUID, Tag> books = new HashMap<>();

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
