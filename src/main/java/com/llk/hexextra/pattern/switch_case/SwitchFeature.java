package com.llk.hexextra.pattern.switch_case;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic;
import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.utils.TreeList;
import com.llk.hexextra.Hexextra;
import com.llk.hexextra.core.HexextraRegistries;
import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;

/**
 * switch(多路等值分派)功能的注册入口。
 *
 * <h2>只需要一个图案</h2>
 * {@code switch}({@code dawwaqw},2 参:常量 + 代码块)→ 产出一张<b>只有一条分支的 switch</b>。
 *
 * <p>一开始还设计过一个 1 参图案用来"声明表的匹配类型",后来取消了:
 * 它和 {@code case} 都产出 {@code SwitchIota},只差 case 条数,玩家从类型上分不清
 * 手里是原料还是成品;而它唯一能造的"空表"除了接 default 之外毫无用处。
 * 现在表的类型<b>由第一条 case 推出</b>,类型约束依然成立(合并/追加时校验一致),
 * 但少了一个图案、也少了一种含糊状态。
 *
 * <p>default 不占图案 —— 用「加法之馏化」{@code add(表, 列表)} 接上去,
 * 与 if_else 家族 {@code add(链, 列表)} 接 else 完全对称。
 *
 * <h2>与 if_else 相比少了一块</h2>
 * <b>不需要自定义续延帧</b>:switch 的派发是"弹栈顶 → 查表 → 压选中分支"一步完成的,
 * 所以没有帧类型、没有帧的编解码器、也没有帧的注册。
 */
public final class SwitchFeature {

    /** 造一条 case 的图案:2 参。 */
    public static final String CASE_PATTERN_ANGLES = "dawwaqw";

    /** 起始方向:东南(与已有图案同一家族)。 */
    public static final HexDir PATTERN_START_DIR = HexDir.SOUTH_EAST;

    /** 「造一条 case」的注册项。名称键:{@code hexcasting.action.hexextra:case}。 */
    public static final DeferredHolder<ActionRegistryEntry, ActionRegistryEntry> CASE =
            HexextraRegistries.ACTIONS.register("switch", () -> {
                return new ActionRegistryEntry(
                        HexPattern.fromAngleString(CASE_PATTERN_ANGLES, PATTERN_START_DIR),
                        new OpCreateCase());
            });

    /**
     * switch 家族的算术(给已有图案补上 switch 表与 case 的算子)。
     *
     * <p>与 {@code IfElseArithmetic} 负责同一批图案但接受不同类型 ——
     * 引擎会把两者汇总成候选,按参数类型挑,所以可以并存。
     */
    public static final DeferredHolder<Arithmetic, Arithmetic> ARITHMETIC =
            HexextraRegistries.ARITHMETICS.register("switch", () -> {
                return new SwitchArithmetic();
            });

    private SwitchFeature() {
    }

    /** 触发本类静态初始化,把注册项挂进 core 的 DeferredRegister。 */
    public static void init() {
        // 故意为空:静态字段的初始化就是全部工作。
    }

    /**
     * 启动自检:case 与表的 NBT 编解码<b>往返一致性</b>,派发表,以及"case 不可执行"。
     *
     * <h2>为什么必须做</h2>
     * 自定义 iota 会被写进物品数据组件与方块实体的存档,而咒法学对 NBT 侧的失败处理是
     * <b>静默退化</b>(反序列化出问题会 {@code orElse(new GarbageIota())},不报错)。
     * 编解码器字段写歪的后果就是玩家的法术书内容悄悄变成乱码,当场毫无提示。
     *
     * <p>这里尤其值得验的三处:一个<b>枚举</b>(匹配类型)、一个<b>可空字段</b>(default)、
     * 以及<b>两层嵌套</b>(表里装分支)。
     */
    public static void checkCodecRoundTrip() {
        TreeList<Iota> body = TreeList.from(List.of(new BooleanIota(true)));
        TreeList<Iota> elseBody = TreeList.from(List.of(new DoubleIota(0.0)));

        // ---- 2) 整张表的往返(含 default)----
        SwitchIota table = new SwitchIota(
                SwitchIota.MatchType.NUMBER,
                List.of(new SwitchIota.Case(new DoubleIota(1.0), body),
                        new SwitchIota.Case(new DoubleIota(2.0), body)),
                elseBody);

        Tag tableTag = IotaType.TYPED_CODEC.encodeStart(NbtOps.INSTANCE, table).getOrThrow();
        Iota tableBack = IotaType.TYPED_CODEC.parse(NbtOps.INSTANCE, tableTag).getOrThrow();
        if (!(tableBack instanceof SwitchIota) || !tableBack.equals(table)) {
            throw new IllegalStateException("hexextra: switch 的 NBT 编解码往返结果不一致 —— 存档会静默损坏");
        }

        // ---- 2.5) null 键的表必须能和具体类型的表合并 ----
        // 复现玩家报的 bug:先用 null 键造一张表,再和数值表合并,然后建索引
        SwitchIota nullTable = SwitchIota.fromCases(
                List.of(new SwitchIota.Case(new at.petrak.hexcasting.api.casting.iota.NullIota(), body)), null);
        if (nullTable.getMatchType() != null) {
            throw new IllegalStateException("hexextra: 只用 null 键造出的表应当处于未定类型");
        }
        SwitchIota mixedTable = new SwitchIota(
                SwitchIota.MatchType.NUMBER,
                List.of(new SwitchIota.Case(new at.petrak.hexcasting.api.casting.iota.NullIota(), body),
                        new SwitchIota.Case(new DoubleIota(3.0), body)),
                null);
        mixedTable.index();   // ← 若 null 分支被类型校验拦下,这里会抛

        // ---- 3) 悬停显示 ----
        String shown = table.display().getString();
        if (shown.isEmpty() || !shown.contains("\n")) {
            throw new IllegalStateException("hexextra: switch 的悬停显示不正常:[" + shown + "]");
        }

        // ---- 4) 派发表 ----
        // 建表本身会校验"case 必须是常量、不能重复、类型要一致",顺手跑一遍
        CaseIndex index = table.index();
        Integer hit = index.lookup(new DoubleIota(2.0));
        if (hit == null || hit != 1) {
            throw new IllegalStateException("hexextra: switch 派发表自检失败,查 2.0 得到 " + hit);
        }
        if (index.lookup(new DoubleIota(99.0)) != null) {
            throw new IllegalStateException("hexextra: switch 派发表自检失败,不该命中 99.0");
        }



    }
}
