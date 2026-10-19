package com.llk.hexextra.pattern.try_catch;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import com.llk.hexextra.core.HexextraIotaTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 「异常」iota —— 把一次 {@link at.petrak.hexcasting.api.casting.mishaps.Mishap} 的
 * <b>玩家可见提示</b>包成一个值,供 {@code try_catch} 的 catch 块取用。
 *
 * <h2>为什么存的是 {@link Component} 而不是 Mishap</h2>
 * {@code Mishap} 是 {@code RuntimeException} ✗ 里面还可能挂着施法上下文、实体引用 ✗
 * 既不能序列化进存档 ✗ 也不该让玩家把它带到别处。
 *
 * <p>这里存的是 {@code mishap.errorMessageWithName(env, ctx)} 的<b>结果</b> ——
 * 也就是玩家当时屏幕上看到的那段文字(含名字前缀与红色样式)✓
 * 所以本 iota 的 {@link #display()} <b>逐字一致</b>,不需要任何额外格式化。
 *
 * <h2>它不可执行</h2>
 * 与 {@code CaseIota} 同理:刻意不覆写 {@code executable()} ✗
 * 对它执行「赫尔墨斯之策略」会照惯例得到"未逃逸的值" ✓
 */
public class ExceptionIota extends Iota {

    /** 存档用的编码:直接复用 Component 的 codec。 */
    private static final Codec<ExceptionIota> CODEC =
            ComponentSerialization.CODEC.xmap(ExceptionIota::new, ExceptionIota::getMessage);

    /** {@code IotaType.codec()} 要 MapCodec,所以把上面那个包成单字段。 */
    private static final MapCodec<ExceptionIota> MAP_CODEC = CODEC.fieldOf("message");

    /** 网络同步:Component 的 stream codec 直接映射。 */
    private static final StreamCodec<RegistryFriendlyByteBuf, ExceptionIota> STREAM_CODEC =
            ComponentSerialization.STREAM_CODEC.map(ExceptionIota::new, ExceptionIota::getMessage);

    private final Component message;

    public ExceptionIota(Component message) {
        super(HexextraIotaTypes.EXCEPTION::get);
        this.message = message;
    }

    /** 玩家可见的完整提示(与抛出错时一模一样)。 */
    public Component getMessage() {
        return this.message;
    }

    /** 有内容的值恒为真(对照 null iota 为假)。 */
    @Override
    public boolean isTruthy(@Nullable CastingEnvironment env) {
        return true;
    }

    @Override
    protected boolean toleratesOther(Iota that) {
        return typesMatch(this, that)
                && that instanceof ExceptionIota other
                && this.message.equals(other.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.message.getString());
    }

    /** 悬停显示:直接就是那段报错文字,不另加包装。 */
    @Override
    public Component display() {
        return this.message;
    }

    public static final IotaType<ExceptionIota> TYPE = new IotaType<>() {

        @Override
        public MapCodec<ExceptionIota> codec() {
            return MAP_CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ExceptionIota> streamCodec() {
            return STREAM_CODEC;
        }

        /** 与咒法学报错同色系的红。 */
        @Override
        public int color() {
            return 0xff_aa3333;
        }
    };
}