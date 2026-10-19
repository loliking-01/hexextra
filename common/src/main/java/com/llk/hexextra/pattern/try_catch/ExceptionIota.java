package com.llk.hexextra.pattern.try_catch;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/**
 * 「异常」iota —— 把一次 {@code Mishap} 的<b>玩家可见提示</b>包成一个值。
 *
 * <h2>0.11.4 与 0.12.0 的差别</h2>
 * 这个版本没有 Codec / StreamCodec ✗ iota 的类型自己要实现:
 * <ul>
 *   <li>{@link Iota#serialize()} —— 把内容写成一个 NBT 值</li>
 *   <li>{@link IotaType#deserialize(Tag, ServerLevel)} —— 从那个 NBT 读回来</li>
 *   <li>{@link IotaType#display(Tag)} —— 无实体时的显示(客户端预览等)</li>
 * </ul>
 *
 * <h2>内容是什么</h2>
 * 存的是 {@code mishap.errorMessageWithName(env, ctx)} 的结果 ——
 * 也就是玩家当时屏幕上看到的那段文字(含名字前缀与样式)✓
 * 所以 {@link #display()} 逐字一致,不需要再格式化。
 */
public class ExceptionIota extends Iota {

    public ExceptionIota(Component message) {
        super(TYPE, message);
    }

    /** 玩家可见的完整提示。 */
    public Component getMessage() {
        return (Component) this.payload;
    }

    @Override
    public boolean isTruthy() {
        return true;
    }

    @Override
    protected boolean toleratesOther(Iota that) {
        return typesMatch(this, that)
                && that instanceof ExceptionIota other
                && this.getMessage().equals(other.getMessage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getMessage().getString());
    }

    /** 序列化成一个字符串 tag(Component 的 JSON)。 */
    @Override
    public Tag serialize() {
        return StringTag.valueOf(Component.Serializer.toJson(this.getMessage()));
    }

    /** 悬停显示:直接就是那段报错文字。 */
    @Override
    public Component display() {
        return this.getMessage();
    }

    public static final IotaType<ExceptionIota> TYPE = new IotaType<>() {

        @Override
        public ExceptionIota deserialize(Tag tag, ServerLevel level) {
            return new ExceptionIota(read(tag));
        }

        @Override
        public Component display(Tag tag) {
            return read(tag);
        }

        /** JSON 解析失败时退回空文本,避免显示阶段 NPE。 */
        private static Component read(Tag tag) {
            Component parsed = Component.Serializer.fromJson(tag.getAsString());
            return parsed != null ? parsed : Component.empty();
        }

        /** 与咒法学报错同色系的红。 */
        @Override
        public int color() {
            return 0xff_aa3333;
        }
    };
}