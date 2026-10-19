package com.llk.hexextra.item.betterspellbook;

import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import at.petrak.hexcasting.common.lib.HexItems;
import com.llk.hexextra.core.ServerHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 法术书书页的持久化助手 —— <b>对原版法术书直接生效</b>,没有自定义物品。
 *
 * <h2>0.11.4 的存储方式</h2>
 * 这个版本没有数据组件,书页在<b>物品 NBT</b> 里,键名由
 * {@code ItemSpellbook.TAG_*} 公开提供。所以落盘就是<b>原样拷贝那几个 tag</b>,
 * 不必自己做 Iota ↔ NBT 编解码。
 *
 * <h2>组件那一份</h2>
 * 原版 {@code writeDatum} 自己会写 NBT ✗ 我们不清理它 ——
 * 客户端界面(翻页/封印/染色/页数)全读它,清掉的话书在界面上会变空白。
 */
public final class SpellbookPersistence {

    /** 这本书的持久标识,存在物品 NBT 里。 */
    public static final String TAG_BOOK_ID = "hexextra_book_id";

    /** 需要持久化的四个键:书页、页名、封印、书皮。 */
    private static final String[] PERSISTED = {
            ItemSpellbook.TAG_PAGES,
            ItemSpellbook.TAG_PAGE_NAMES,
            ItemSpellbook.TAG_SEALED,
            ItemSpellbook.TAG_VARIANT,
    };

    // =======================================================================
    // UUID
    // =======================================================================

    public static UUID getBookId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(TAG_BOOK_ID)) {
            return tag.getUUID(TAG_BOOK_ID);
        }
        return null;
    }

    public static UUID getOrCreateBookId(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.hasUUID(TAG_BOOK_ID)) {
            return tag.getUUID(TAG_BOOK_ID);
        }
        UUID fresh = UUID.randomUUID();
        tag.putUUID(TAG_BOOK_ID, fresh);
        return fresh;
    }

    // =======================================================================
    // 落盘 / 回填
    // =======================================================================

    public static void flush(ItemStack stack, MinecraftServer server) {
        UUID id = getOrCreateBookId(stack);
        CompoundTag itemTag = stack.getOrCreateTag();

        CompoundTag out = new CompoundTag();
        for (String key : PERSISTED) {
            Tag value = itemTag.get(key);
            if (value != null) {
                out.put(key, value.copy());
            }
        }
        SpellbookStorage.get(server).set(id, out);
        recordWrite(id, server);
    }

    public static void pull(ItemStack stack, MinecraftServer server) {
        UUID id = getBookId(stack);
        if (id == null) {
            return;
        }
        Tag data = SpellbookStorage.get(server).get(id);
        if (!(data instanceof CompoundTag source)) {
            return;
        }
        CompoundTag itemTag = stack.getOrCreateTag();
        for (String key : PERSISTED) {
            Tag value = source.get(key);
            if (value != null) {
                itemTag.put(key, value.copy());
            }
        }
    }

    /**
     * 用一份存档数据造一本新的原版法术书(快照恢复),分配新 UUID。
     *
     * <h2>为什么不带封印</h2>
     * 0.11.4 的封印是<b>按页</b>存的({@code sealed_pages: {"1": true}}),
     * 而 {@code isSealed} 看的是<b>当前页</b>,快照里没有 {@code page_idx} 时默认第 1 页。
     * 于是只要原书第 1 页是封的,恢复出来就整本显示"已封印"——
     * 玩家拿到的应该是一本<b>可以继续写</b>的书,所以这里显式不带封印。
     * 需要的话再拿蜂蜜块封一次即可。
     */
    public static ItemStack createSnapshot(CompoundTag data, MinecraftServer server) {
        ItemStack stack = new ItemStack(HexItems.SPELLBOOK);
        CompoundTag itemTag = stack.getOrCreateTag();
        for (String key : PERSISTED) {
            if (ItemSpellbookTagNames.SEALED.equals(key)) {
                continue;
            }
            Tag value = data.get(key);
            if (value != null) {
                itemTag.put(key, value.copy());
            }
        }
        itemTag.remove(ItemSpellbookTagNames.SEALED);
        itemTag.putUUID(TAG_BOOK_ID, UUID.randomUUID());
        flush(stack, server);
        return stack;
    }

    /** 记录"谁通过书写图案写了这本书"。 */
    private static void recordWrite(UUID id, MinecraftServer server) {
        var writer = com.llk.hexextra.core.WriterContext.get(server);
        if (writer == null) {
            return;
        }
        SpellbookAccessLog.get(server).recordAccess(new SpellbookAccessLog.AccessRecord(
                id,
                writer.getUUID(),
                writer.getGameProfile().getName(),
                System.currentTimeMillis(),
                "WRITTEN",
                1));
    }

    private SpellbookPersistence() {
    }
}