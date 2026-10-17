package com.llk.hexextra.pattern.switch_case;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.utils.HexUtils;
import at.petrak.hexcasting.api.utils.TreeList;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * switch 的一条 case —— 「常量 → 命中时执行的代码块」。
 *
 * <h2>为什么它得是独立类型</h2>
 * 如果「造一条 case」与「造一张表」产出同一种 iota,只有 case 条数不同,
 * 玩家就分不清手里拿的是"一条分支"还是"一张完整的表"。分开以后:
 * <ul>
 *   <li>{@link CaseIota} 是<b>原料</b>:用「加法之馏化」把若干条拼成表,或往已有表里追加;</li>
 *   <li>{@link SwitchIota} 是<b>成品</b>:能被执行的表。</li>
 * </ul>
 *
 * <h2>它不可执行 —— 而且代价为零</h2>
 * 本类<b>不覆写</b> {@code execute()} 与 {@code executable()}。
 * 咒法学 0.11.4 的 {@link Iota} 基类在这种情况下默认返回带
 * {@code MishapUnescapedValue} 的结果 —— 也就是说"原料不能直接执行"这条语义
 * 是<b>默认行为</b>白送的,不需要我们写任何报错代码。
 *
 * <h2>与 1.21.1 版的差异(0.11.4 的 API 特点)</h2>
 * <ul>
 *   <li>构造器把 {@code (type, payload)} 交给基类,<b>payload 由基类持有</b>,
 *       所以 {@code hashCode()} 也由基类按 payload 算 —— 不用自己写;</li>
 *   <li>序列化是<b>手写 NBT</b>({@link #serialize()}),没有 Codec / StreamCodec;</li>
 *   <li>显示逻辑写在 {@link #TYPE} 的 {@code display(Tag)} 里而不是对象上 ——
 *       因为 0.11.4 的 {@link Iota#display()} 默认就是 {@code type.display(serialize())},
 *       客户端手里只有 Tag、没有对象。</li>
 * </ul>
 */
public class CaseIota extends Iota {

    /**
     * payload 用 record:等值判断与散列都由 Java 自动生成,
     * 正好配合基类那句 {@code hashCode() { return payload.hashCode(); }}。
     */
    public record CaseData(Iota value, TreeList<Iota> body) {
    }

    /**
     * @param value 匹配用的常量(数值 / 布尔 / null / 图案)
     * @param body  命中时执行的代码块
     */
    public CaseIota(Iota value, TreeList<Iota> body) {
        super(TYPE, new CaseData(value, body));
    }

    private CaseData data() {
        return (CaseData) this.payload;
    }

    /** 匹配用的常量。 */
    public Iota getValue() {
        return this.data().value();
    }

    /** 命中时执行的代码块。 */
    public TreeList<Iota> getBody() {
        return this.data().body();
    }

    /**
     * 这条 case 要求的匹配类型。
     *
     * <p>正常途径(用「凯斯之策略」造)一定非 null;
     * 只有存档被手改之类的情况下才可能为 null —— 那时建表会报
     * {@code CaseNotConstant},而不是悄悄放过。
     */
    public @Nullable SwitchMatchType matchType() {
        return SwitchMatchType.of(this.getValue());
    }

    /** 真假:一条 case 总有东西可做,恒真(对照 ListIota 的"非空为真")。 */
    @Override
    public boolean isTruthy() {
        return true;
    }

    /** 容差相等:类型相同且常量与代码块逐层相等。 */
    @Override
    protected boolean toleratesOther(Iota that) {
        return typesMatch(this, that)
                && that instanceof CaseIota other
                && Iota.tolerates(this.getValue(), other.getValue())
                && this.getBody().equals(other.getBody());
    }

    /**
     * 手写 NBT 序列化(0.11.4 没有 Codec)。
     *
     * <p>每个子 iota 都用 {@link IotaType#serialize(Iota)} 包成 {@code {type, data}},
     * 于是嵌套结构与存档格式完全交给本体维护,我们不必自己操心类型名。
     */
    @Override
    public @NotNull Tag serialize() {
        var out = new CompoundTag();
        out.put("value", IotaType.serialize(this.getValue()));

        var bodyTag = new ListTag();
        for (Iota iota : this.getBody()) {
            bodyTag.add(IotaType.serialize(iota));
        }
        out.put("body", bodyTag);
        return out;
    }

    /** 直接子 iota:匹配值 + 代码块里的元素(给真名扫描等工具用)。 */
    @Override
    public @Nullable Iterable<Iota> subIotas() {
        List<Iota> out = new ArrayList<>();
        out.add(this.getValue());
        this.getBody().forEach(out::add);
        return out;
    }

    /** 内存占用(与 ListIota 同一口径)。 */
    @Override
    public int size() {
        int total = 2;   // 自身 + 匹配值
        for (Iota iota : this.getBody()) {
            total += iota.size();
        }
        return total;
    }

    /** 嵌套深度。 */
    @Override
    public int depth() {
        int maxChild = this.getValue().depth();
        for (Iota iota : this.getBody()) {
            maxChild = Math.max(maxChild, iota.depth());
        }
        return maxChild + 1;
    }

    /**
     * 本 iota 的类型对象。
     *
     * <p>注意 {@code display(Tag)} 里要把代码块渲染出来:0.11.4 的显示入口是
     * **Tag** 而不是对象,所以这里只能照着 ListIota 的做法,遍历 {@link ListTag}
     * 逐个调 {@link IotaType#getDisplay(CompoundTag)}。
     */
    public static final IotaType<CaseIota> TYPE = new IotaType<>() {

        @Override
        public @Nullable CaseIota deserialize(Tag tag, ServerLevel world) throws IllegalArgumentException {
            var ctag = HexUtils.downcast(tag, CompoundTag.TYPE);

            var valueTag = HexUtils.downcast(ctag.get("value"), CompoundTag.TYPE);
            var value = IotaType.deserialize(valueTag, world);

            var bodyTag = HexUtils.downcast(ctag.get("body"), ListTag.TYPE);
            var body = new ArrayList<Iota>(bodyTag.size());
            for (Tag sub : bodyTag) {
                var csub = HexUtils.downcast(sub, CompoundTag.TYPE);
                var iota = IotaType.deserialize(csub, world);
                if (iota == null) {
                    return null;
                }
                body.add(iota);
            }

            return new CaseIota(value, TreeList.from(body));
        }

        @Override
        public Component display(Tag tag) {
            var ctag = HexUtils.downcast(tag, CompoundTag.TYPE);

            MutableComponent out = Component.empty();
            out.append(Component.literal("case ").withStyle(ChatFormatting.GOLD));
            out.append(IotaType.getDisplay(HexUtils.downcast(ctag.get("value"), CompoundTag.TYPE)));
            out.append(Component.literal(" -> {").withStyle(ChatFormatting.GOLD));

            // 代码块:和 ListIota 一样的渲染方式(外面套一层 list_contents 的括号样式)
            MutableComponent bodyOut = Component.empty();
            var bodyTag = HexUtils.downcast(ctag.get("body"), ListTag.TYPE);
            for (int i = 0; i < bodyTag.size(); i++) {
                var csub = HexUtils.downcast(bodyTag.get(i), CompoundTag.TYPE);
                bodyOut.append(IotaType.getDisplay(csub));
                if (i < bodyTag.size() - 1) {
                    bodyOut.append(", ");
                }
            }
            out.append(Component.translatable("hexcasting.tooltip.list_contents", bodyOut)
                    .withStyle(ChatFormatting.DARK_PURPLE));

            return out;
        }

        /** 与 switch 表的蓝色区分开的青色。 */
        @Override
        public int color() {
            return 0xff_55aacc;
        }
    };
}
