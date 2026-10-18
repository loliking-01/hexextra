package com.llk.hexextra.item.betterspellbook;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import com.llk.hexextra.core.ServerHolder;

import java.util.UUID;

/**
 * 强化法术书(0.11.4 / 1.20.1 版) —— 功能与咒法学原版法术书一致,
 * 只是书页内容存在世界存档的 {@link SpellbookStorage} 里,而不是物品 NBT 里。
 *
 * <h2>0.11.4 与 1.21.1 的根本差异</h2>
 * 1.21.1 用数据组件;0.11.4 用 <b>物品 NBT</b>,而且把键名暴露成了公开常量:
 * <pre>
 * ItemSpellbook.TAG_PAGES / TAG_PAGE_NAMES / TAG_SEALED / TAG_VARIANT / TAG_SELECTED_PAGE
 * </pre>
 * 这让实现变简单了:落盘时<b>原样拷贝这几个 tag</b> 即可,不必关心它们内部是什么结构,
 * 也不必自己做 Iota ↔ NBT 的编解码。
 *
 * <h2>UUID 存在哪</h2>
 * 物品自己的 NBT 里,键 {@link #TAG_BOOK_ID}。与咒法学的 tag 不冲突。
 */
public class BetterSpellbookItem extends ItemSpellbook {

    /** 本书身份在物品 NBT 里的键。 */
    public static final String TAG_BOOK_ID = "hexextra_book_id";

    /** 需要持久化到文件的四个 tag:书页、页名、封印、书皮。 */
    private static final String[] PERSISTED_TAGS = {
            ItemSpellbook.TAG_PAGES,
            ItemSpellbook.TAG_PAGE_NAMES,
            ItemSpellbook.TAG_SEALED,
            ItemSpellbook.TAG_VARIANT,
    };

    public BetterSpellbookItem(Properties props) {
        super(props);
    }

    // =======================================================================
    // 写入:原版逻辑 + 落盘
    // =======================================================================

    /**
     * 写书页。先走原版(写物品 NBT),随后整本落盘。
     *
     * <p>{@code IotaHolderItem} 的签名里没有 Level,所以从 {@link ServerHolder} 取当前服务端
     * (由两个 loader 在启停时填入);客户端为 null,正好跳过落盘。
     */
    @Override
    public void writeDatum(ItemStack stack, Iota iota) {
        super.writeDatum(stack, iota);

        MinecraftServer server = ServerHolder.get();
        if (server != null) {
            flush(stack, server);
        }
    }

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

    /** 读 UUID;没有就生成一个写进物品 NBT。 */
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
    // 物品 NBT ↔ 文件
    // =======================================================================

    /** 把物品上的四个 tag 整本写进存档文件。 */
    public static void flush(ItemStack stack, MinecraftServer server) {
        UUID id = getOrCreateBookId(stack);
        CompoundTag itemTag = stack.getOrCreateTag();

        CompoundTag data = new CompoundTag();
        for (String key : PERSISTED_TAGS) {
            copyTag(itemTag, data, key);
        }
        SpellbookStorage.get(server).set(id, data);
    }

    /** 从存档文件把四个 tag 写回物品(只在服务端有意义)。 */
    public static void pull(ItemStack stack, MinecraftServer server) {
        UUID id = getBookId(stack);
        if (id == null) {
            return;
        }
        CompoundTag data = SpellbookStorage.get(server).get(id);
        if (data == null) {
            return;
        }
        CompoundTag itemTag = stack.getOrCreateTag();
        for (String key : PERSISTED_TAGS) {
            if (data.contains(key)) {
                itemTag.put(key, data.get(key).copy());
            }
        }
    }

    /**
     * 合成用:把原版法术书的内容搬进一本新的强化法术书。
     *
     * <p>四个 tag 全搬(书页 / 页名 / 封印 / 书皮),再补上当前页码 ——
     * 页码不参与文件存储(它是阅读位置),但合成时跟随源书更符合直觉。
     */
    public static void copyFrom(ItemStack source, ItemStack target, MinecraftServer server) {
        CompoundTag sourceTag = source.getTag();
        if (sourceTag != null) {
            CompoundTag targetTag = target.getOrCreateTag();
            for (String key : PERSISTED_TAGS) {
                if (sourceTag.contains(key)) {
                    targetTag.put(key, sourceTag.get(key).copy());
                }
            }
            if (sourceTag.contains(ItemSpellbook.TAG_SELECTED_PAGE)) {
                targetTag.put(ItemSpellbook.TAG_SELECTED_PAGE,
                        sourceTag.get(ItemSpellbook.TAG_SELECTED_PAGE).copy());
            }
        }

        // 新书分配全新 UUID,然后把内容立即落盘
        target.getOrCreateTag().putUUID(TAG_BOOK_ID, UUID.randomUUID());
        flush(target, server);
    }

    // =======================================================================
    // 工具
    // =======================================================================

    private static void copyTag(CompoundTag from, CompoundTag to, String key) {
        Tag value = from.get(key);
        if (value != null) {
            to.put(key, value.copy());
        }
    }
}
