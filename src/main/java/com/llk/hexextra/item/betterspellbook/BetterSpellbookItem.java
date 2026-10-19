package com.llk.hexextra.item.betterspellbook;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import at.petrak.hexcasting.common.lib.HexDataComponents;
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
 * 强化法术书 —— 功能与咒法学原版法术书完全一致,只是书页内容存在世界存档的
 * {@link SpellbookStorage} 里,而不是物品组件里。
 *
 * <h2>组件结构</h2>
 * 物品上只多一个 {@link #BOOK_ID}(这本书的身份)。原版法术书本来的书页组件仍然留着,
 * 当作<b>缓存副本</b>。
 *
 * <h2>为什么还要缓存一份书页</h2>
 * 咒法学法术书界面的翻页 / 封印 / 页数读取全在 {@code ItemSpellbook} 的<b>静态方法</b>里
 * ({@code getPage} / {@code setSealed} / {@code highestPage} / {@code rotatePageIdx})。
 * 静态方法不能被覆写,所以界面只能读组件。留一份缓存既让界面零改动,
 * 也让文件损坏时还有兜底。
 *
 * <h2>权威在文件</h2>
 * {@link #writeDatum} 先走原版逻辑(写组件缓存),随后立刻把整本书落盘。
 */
public class BetterSpellbookItem extends ItemSpellbook {

    /** 这本书的持久标识。取值时惰性生成,所以调试物品也能用。 */
    public static final DataComponentType<UUID> BOOK_ID = DataComponentType.<UUID>builder()
            .persistent(UUIDUtil.CODEC)
            .networkSynchronized(UUIDUtil.STREAM_CODEC)
            .build();

    public BetterSpellbookItem(Properties props) {
        super(props);
    }

    // =======================================================================
    // 写入:组件缓存 + 落盘
    // =======================================================================

    /**
     * 写书页。原版逻辑照常跑(写进组件,客户端立刻能看见),然后整本书覆盖到存档文件。
     *
     * <p>{@code IotaHolderItem} 的签名里没有 {@code Level},所以这里从
     * {@link ServerLifecycleHooks} 取当前服务端;客户端调用时它返回 null,正好跳过落盘。
     */
    @Override
    public void writeDatum(ItemStack stack, Iota iota) {
        super.writeDatum(stack, iota);

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            flush(stack, server);
        }
    }

    // =======================================================================
    // UUID
    // =======================================================================

    /** 读这本书的 UUID;没有就生成一个写进组件。 */
    public static UUID getOrCreateBookId(ItemStack stack) {
        UUID id = stack.get(BOOK_ID);
        if (id == null) {
            id = UUID.randomUUID();
            stack.set(BOOK_ID, id);
        }
        return id;
    }

    public static UUID getBookId(ItemStack stack) {
        return stack.get(BOOK_ID);
    }

    // =======================================================================
    // 组件 ↔ 文件
    // =======================================================================

    /**
     * 真正需要持久化的四个组件。
     *
     * <p><b>为什么不含 {@code SELECTED_SPELLBOOK_PAGE}</b>:那是"翻到第几页"的阅读位置,
     * 不是书的内容。换台机器、换个人打开,从第 1 页开始更合理;
     * 而且 {@code rotatePageIdx} 是静态方法,翻页时不会经过物品的实例方法,
     * 我们没机会把它同步到文件 —— 与其留一份经常过期的数据,不如不存。
     *
     * <p><b>为什么是这四个</b>:直接对照 {@code ItemSpellbook} 的字节码。
     * 咒法学<b>不用</b> {@code IOTA_HOLDER_IOTA} 存书页(那是最初踩的坑),
     * 而是下面这套 Map<String, ...> 结构。
     */
    private static final java.util.List<String> PERSISTED_KEYS =
            java.util.List.of("pages", "page_names", "page_seals", "variant");

    /** 把一个组件值编码成 NBT;组件不存在时返回 null。 */
    private static <T> Tag encodeComponent(DataComponentType<T> type, ItemStack stack) {
        T value = stack.get(type);
        if (value == null) {
            return null;
        }
        return type.codec().encodeStart(NbtOps.INSTANCE, value).getOrThrow();
    }

    /** 把一个组件从 NBT 解回来并写进物品;NBT 里没有就跳过。 */
    private static <T> void decodeComponent(DataComponentType<T> type, ItemStack stack,
                                            CompoundTag parent, String key) {
        if (!parent.contains(key)) {
            return;
        }
        Tag tag = parent.get(key);
        var parsed = type.codec().parse(NbtOps.INSTANCE, tag);
        parsed.result().ifPresent(value -> stack.set(type, value));
    }

    /**
     * 把物品上的书页数据整本写进存档文件。
     *
     * <p>四个组件逐个编码成一个 CompoundTag:
     * <pre>
     * {
     *   "pages":      { ... },   // SPELLBOOK_PAGES
     *   "page_names": { ... },   // SPELLBOOK_PAGE_NAMES
     *   "page_seals": { ... },   // SPELLBOOK_PAGE_SEALS
     *   "variant":    0          // ITEM_VARIANT
     * }
     * </pre>
     */
    public static void flush(ItemStack stack, MinecraftServer server) {
        UUID id = getOrCreateBookId(stack);
        CompoundTag out = new CompoundTag();

        Tag pages = encodeComponent(HexDataComponents.SPELLBOOK_PAGES.get(), stack);
        if (pages == null && !stack.has(BOOK_ID)) {
            // 全新空书:只登记一个空条目
            SpellbookStorage.get(server).getOrCreate(id);
            return;
        }
        if (pages != null) {
            out.put("pages", pages);
        }
        Tag names = encodeComponent(HexDataComponents.SPELLBOOK_PAGE_NAMES.get(), stack);
        if (names != null) {
            out.put("page_names", names);
        }
        Tag seals = encodeComponent(HexDataComponents.SPELLBOOK_PAGE_SEALS.get(), stack);
        if (seals != null) {
            out.put("page_seals", seals);
        }
        Tag variant = encodeComponent(HexDataComponents.ITEM_VARIANT.get(), stack);
        if (variant != null) {
            out.put("variant", variant);
        }

        SpellbookStorage.get(server).set(id, out);

        // 只有经由「书写之策略」写入时 WriterContext 才非空,
        // 所以这里不会把合成/恢复也记成"访问"。
        recordWrite(stack, id, server);
    }

    /** 从存档文件把四个组件读回物品(只在服务端有意义)。 */
    public static void pull(ItemStack stack, MinecraftServer server) {
        UUID id = getBookId(stack);
        if (id == null) {
            return;
        }
        Tag tag = SpellbookStorage.get(server).get(id);
        if (!(tag instanceof CompoundTag data)) {
            return;
        }
        decodeComponent(HexDataComponents.SPELLBOOK_PAGES.get(), stack, data, "pages");
        decodeComponent(HexDataComponents.SPELLBOOK_PAGE_NAMES.get(), stack, data, "page_names");
        decodeComponent(HexDataComponents.SPELLBOOK_PAGE_SEALS.get(), stack, data, "page_seals");
        decodeComponent(HexDataComponents.ITEM_VARIANT.get(), stack, data, "variant");
    }

    /**
     * 合成用:把原版法术书的内容搬进一本新的强化法术书。
     *
     * <p>四个组件都要搬 —— 只搬书页会让新书丢失页名、封印状态与书皮外观。
     *
     * @param source 原版法术书(或任何带书页组件的物品)
     * @param target 新造出来的强化法术书
     */
    public static void copyFrom(ItemStack source, ItemStack target, MinecraftServer server) {
        copyComponent(HexDataComponents.SPELLBOOK_PAGES.get(), source, target);
        copyComponent(HexDataComponents.SPELLBOOK_PAGE_NAMES.get(), source, target);
        copyComponent(HexDataComponents.SPELLBOOK_PAGE_SEALS.get(), source, target);
        copyComponent(HexDataComponents.ITEM_VARIANT.get(), source, target);
        // 当前翻到第几页也一并复制 —— 页码只是物品组件,不参与文件存储(它是"阅读位置"),
        // 但合成时让新书停在源书那一页更符合直觉。
        copyComponent(HexDataComponents.SELECTED_SPELLBOOK_PAGE.get(), source, target);

        // 新书分配全新 UUID,然后把内容立即落盘
        target.set(BOOK_ID, UUID.randomUUID());
        flush(target, server);
    }

    /**
     * 记录"谁通过书写图案写了这本书"。
     *
     * <p>玩家来自 {@link com.llk.hexextra.core.WriterContext},由
     * {@code MixinOpWriteSpell} 在 {@code OpWrite$Spell.cast} 时放入 ——
     * 因为 {@code writeDatum} 的签名里没有施法者。
     */
    private static void recordWrite(ItemStack stack, UUID id, MinecraftServer server) {
        var writer = com.llk.hexextra.core.WriterContext.get();
        if (writer == null) {
            return;
        }
        SpellbookStorage.get(server).recordAccess(new SpellbookStorage.AccessRecord(
                id,
                writer.getUUID(),
                writer.getGameProfile().getName(),
                server.overworld().getGameTime(),
                "WRITTEN"));
    }

    /**
     * 用一份存档数据造一本<b>新的</b>强化法术书(快照恢复)。
     *
     * <p>刻意分配<b>新 UUID</b>:恢复出来的书与原书互不影响 ✗
     * 这样管理指令不会变成"无限复制同一本书"。
     */
    public static ItemStack createSnapshot(CompoundTag data, MinecraftServer server) {
        ItemStack stack = new ItemStack(com.llk.hexextra.item.betterspellbook.BetterSpellbookFeature.ITEM.get());
        decodeComponent(HexDataComponents.SPELLBOOK_PAGES.get(), stack, data, "pages");
        decodeComponent(HexDataComponents.SPELLBOOK_PAGE_NAMES.get(), stack, data, "page_names");
        decodeComponent(HexDataComponents.SPELLBOOK_PAGE_SEALS.get(), stack, data, "page_seals");
        decodeComponent(HexDataComponents.ITEM_VARIANT.get(), stack, data, "variant");
        stack.set(BOOK_ID, UUID.randomUUID());
        flush(stack, server);
        return stack;
    }

    private static <T> void copyComponent(DataComponentType<T> type, ItemStack source, ItemStack target) {
        T value = source.get(type);
        if (value != null) {
            target.set(type, value);
        }
    }

    /** 触发本类的静态初始化。 */
    public static void init() {
        // 故意为空:静态字段的初始化就是全部工作。
    }
}
