package com.llk.hexextra.item.betterspellbook;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import at.petrak.hexcasting.common.lib.HexDataComponents;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
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

    /** 把组件里的书页缓存整本写进存档文件。 */
    public static void flush(ItemStack stack, MinecraftServer server) {
        UUID id = getOrCreateBookId(stack);
        Iota data = stack.get(HexDataComponents.IOTA_HOLDER_IOTA.get());

        SpellbookStorage storage = SpellbookStorage.get(server);
        if (data == null) {
            storage.getOrCreate(id);
            return;
        }
        Tag tag = IotaType.TYPED_CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        storage.set(id, tag);
    }

    /** 从存档文件把书页读回组件缓存(只在服务端有意义)。 */
    public static void pull(ItemStack stack, MinecraftServer server) {
        UUID id = getBookId(stack);
        if (id == null) {
            return;
        }
        Tag tag = SpellbookStorage.get(server).get(id);
        if (tag == null) {
            return;
        }
        Iota restored = IotaType.TYPED_CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        stack.set(HexDataComponents.IOTA_HOLDER_IOTA.get(), restored);
    }

    /**
     * 合成用:把原版法术书的内容搬进一本新的强化法术书。
     *
     * @param source 原版法术书(或任何带书页组件的物品)
     * @param target 新造出来的强化法术书
     */
    public static void copyFrom(ItemStack source, ItemStack target, MinecraftServer server) {
        Iota data = source.get(HexDataComponents.IOTA_HOLDER_IOTA.get());
        if (data != null) {
            target.set(HexDataComponents.IOTA_HOLDER_IOTA.get(), data);
        }
        // 新书分配全新 UUID,然后把内容立即落盘
        target.set(BOOK_ID, UUID.randomUUID());
        flush(target, server);
    }

    /** 触发本类的静态初始化。 */
    public static void init() {
        // 故意为空:静态字段的初始化就是全部工作。
    }
}
