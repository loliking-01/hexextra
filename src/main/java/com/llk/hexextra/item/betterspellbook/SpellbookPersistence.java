package com.llk.hexextra.item.betterspellbook;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.common.lib.HexDataComponents;
import at.petrak.hexcasting.common.lib.HexItems;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * 法术书书页的持久化助手 —— <b>对原版法术书直接生效</b>,不存在"自定义物品"。
 *
 * <h2>存哪</h2>
 * <ul>
 *   <li>物品上只留一个 {@link #BOOK_ID}(UUID),用来查文件;</li>
 *   <li>真正的书页内容在 {@link SpellbookStorage}({@code hexextra_spellbooks.dat});</li>
 *   <li>访问记录在 {@link SpellbookAccessLog}(独立文件)。</li>
 * </ul>
 *
 * <h2>组件那一份</h2>
 * 原版 {@code ItemSpellbook.writeDatum} 自己会写组件 ✗ 我们不清理它 ——
 * 客户端界面(翻页/封印/染色/页数)全读组件,清掉的话书在界面上会变空白。
 * 代价是:卸载本模组后,书只能读到最后一次写入时的内容。
 */
public final class SpellbookPersistence {

    /** 这本书的持久标识。 */
    public static final DataComponentType<UUID> BOOK_ID = DataComponentType.<UUID>builder()
            .persistent(UUIDUtil.CODEC)
            .networkSynchronized(UUIDUtil.STREAM_CODEC)
            .build();

    private static final Codec<Iota> IOTA_CODEC = IotaCodecHolder.CODEC;

    /** 延迟取 Iota 的 codec,避免类初始化顺序问题。 */
    private static final class IotaCodecHolder {
        private static final Codec<Iota> CODEC = at.petrak.hexcasting.api.casting.iota.IotaType.TYPED_CODEC;
    }

    // =======================================================================
    // UUID
    // =======================================================================

    public static UUID getBookId(ItemStack stack) {
        return stack.get(BOOK_ID);
    }

    public static UUID getOrCreateBookId(ItemStack stack) {
        UUID id = stack.get(BOOK_ID);
        if (id == null) {
            id = UUID.randomUUID();
            stack.set(BOOK_ID, id);
        }
        return id;
    }

    // =======================================================================
    // 落盘
    // =======================================================================

    /** 把物品上的四个书页组件写进文件。 */
    public static void flush(ItemStack stack, MinecraftServer server) {
        UUID id = getOrCreateBookId(stack);
        CompoundTag out = new CompoundTag();

        Tag pages = encode(HexDataComponents.SPELLBOOK_PAGES.get(), stack);
        if (pages != null) {
            out.put("pages", pages);
        }
        Tag names = encode(HexDataComponents.SPELLBOOK_PAGE_NAMES.get(), stack);
        if (names != null) {
            out.put("page_names", names);
        }
        Tag seals = encode(HexDataComponents.SPELLBOOK_PAGE_SEALS.get(), stack);
        if (seals != null) {
            out.put("page_seals", seals);
        }
        Tag variant = encode(HexDataComponents.ITEM_VARIANT.get(), stack);
        if (variant != null) {
            out.put("variant", variant);
        }

        SpellbookStorage.get(server).set(id, out);
        recordWrite(id, server);
    }

    /** 从文件把四个组件读回物品。 */
    public static void pull(ItemStack stack, MinecraftServer server) {
        UUID id = getBookId(stack);
        if (id == null) {
            return;
        }
        Tag tag = SpellbookStorage.get(server).get(id);
        if (!(tag instanceof CompoundTag data)) {
            return;
        }
        decode(HexDataComponents.SPELLBOOK_PAGES.get(), stack, data, "pages");
        decode(HexDataComponents.SPELLBOOK_PAGE_NAMES.get(), stack, data, "page_names");
        decode(HexDataComponents.SPELLBOOK_PAGE_SEALS.get(), stack, data, "page_seals");
        decode(HexDataComponents.ITEM_VARIANT.get(), stack, data, "variant");
    }

    /** 用一份存档数据造一本新的原版法术书(快照恢复)。 */
    public static ItemStack createSnapshot(CompoundTag data, MinecraftServer server) {
        ItemStack stack = new ItemStack(HexItems.SPELLBOOK.get());
        decode(HexDataComponents.SPELLBOOK_PAGES.get(), stack, data, "pages");
        decode(HexDataComponents.SPELLBOOK_PAGE_NAMES.get(), stack, data, "page_names");
        decode(HexDataComponents.SPELLBOOK_PAGE_SEALS.get(), stack, data, "page_seals");
        decode(HexDataComponents.ITEM_VARIANT.get(), stack, data, "variant");
        stack.set(BOOK_ID, UUID.randomUUID());
        flush(stack, server);
        return stack;
    }

    // =======================================================================
    // 访问记录
    // =======================================================================

    /**
     * 记录"谁通过书写图案写了这本书"。
     *
     * <p>玩家来自 {@link com.llk.hexextra.core.WriterContext},由
     * {@code MixinOpWriteSpell} 在 {@code OpWrite$Spell.cast} 时放入 ——
     * 因为 {@code writeDatum} 的签名里没有施法者。
     */
    private static void recordWrite(UUID id, MinecraftServer server) {
        var writer = com.llk.hexextra.core.WriterContext.get(server);
        if (writer == null) {
            return;
        }
        SpellbookAccessLog.get(server).recordAccess(new SpellbookAccessLog.AccessRecord(
                id,
                writer.getUUID(),
                writer.getGameProfile().getName(),
                server.overworld().getGameTime(),
                "WRITTEN",
                1));
    }

    // =======================================================================
    // 工具
    // =======================================================================

    private static <T> Tag encode(DataComponentType<T> type, ItemStack stack) {
        T value = stack.get(type);
        if (value == null) {
            return null;
        }
        return type.codec().encodeStart(NbtOps.INSTANCE, value).getOrThrow();
    }

    private static <T> void decode(DataComponentType<T> type, ItemStack stack,
                                  CompoundTag parent, String key) {
        Tag tag = parent.get(key);
        if (tag == null) {
            return;
        }
        type.codec().parse(NbtOps.INSTANCE, tag).result().ifPresent(v -> stack.set(type, v));
    }

    private SpellbookPersistence() {
    }
}