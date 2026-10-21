package com.llk.hexextra.pattern.switch_case;

import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import com.llk.hexextra.core.HexextraMishaps;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * switch 的派发表 —— 整个功能的性能核心,把「这个值该走哪条 case」压到 O(1)。
 *
 * <h2>为什么是"单桶"</h2>
 * 一条 switch 在<b>建造期</b>就用一个样例常量声明了自己匹配的类型
 * (见 {@link SwitchIota.MatchType}),所以表里所有键天然同属一种类型 ——
 * 查表就是一次直接的 {@code get},不需要再按类型分派。
 *
 * <h2>为什么不能直接用 {@code HashMap<Iota, Integer>}</h2>
 * 因为咒法学的 iota <b>相等语义与散列语义互相矛盾</b>:
 * <pre>
 *   Iota.equals → tolerates → |a - b| &lt; TOLERANCE(1e-4)   ← 容差
 *   DoubleIota.hashCode()   → Double.hashCode(value)      ← 精确
 * </pre>
 * 于是 {@code 1.0} 与 {@code 1.00001} 被判相等、散列值却不同 —— 拿它们当哈希表的键,
 * <b>必然查不到</b>。这不是性能问题,是正确性问题。
 *
 * <h2>更根本的原因:容差相等连传递性都没有</h2>
 * <pre>
 *   a = 0        b = 0.75e-4      c = 1.5e-4
 *   |a-b| &lt; 1e-4  → a ~ b
 *   |b-c| &lt; 1e-4  → b ~ c
 *   |a-c| &gt; 1e-4  → a ≁ c        ← 传递性断裂
 * </pre>
 * 派发表要求「一个值属于哪个 case」有<b>唯一</b>答案,而那是等价关系才有的性质。
 * 所以 switch 的匹配走<b>精确相等</b> —— 这同时正好与 Java 的 switch(用 {@code ==})
 * 一致。
 *
 * <h2>于是可匹配的类型只能是那四种常量</h2>
 * 只有数值 / 布尔 / null / 图案能算出规范的精确键。其它类型(列表、实体、向量,
 * 以及未来任何附属的自定义 iota)都不行 —— 因为 {@code IotaType} 根本没有
 * 「给我这个 iota 的规范键」这样的钩子,外模组无法为自己的类型补上。
 * 这与 Java 传统 switch 只吃「整型/字符/字符串/枚举」是同构的限制。
 */
public final class CaseIndex {

    /**
     * 「null 型」switch 的键。
     *
     * <p>null 本身没法当哈希键,所以用一个哨兵对象代它 —— 这样四种类型可以共用同一张表。
     * null 型最多只可能有一条 case(两条就重复了),哨兵天然也只可能命中一次。
     */
    private static final Object NULL_KEY = new Object();

    private final SwitchIota.MatchType type;

    /** 规范化键 → case 下标。键的实际类型由 {@link #type} 决定(见 {@link #keyFor})。 */
    private final Map<Object, Integer> slots = new HashMap<>();

    private CaseIndex(SwitchIota.MatchType type) {
        this.type = type;
    }

    /** 本表声明匹配的类型。 */
    public SwitchIota.MatchType getType() {
        return this.type;
    }

    /**
     * 按「声明类型 + case 表」建索引。
     *
     * <p>这是 O(case 数) 的一次性工作,结果由 {@link SwitchIota#index()} 缓存,
     * 正常施法路径不会反复付这个成本。
     *
     * @throws HexextraMishaps.CaseTypeMismatch 某个 case 的类型与声明的类型不符
     * @throws HexextraMishaps.DuplicateCase    某个常量出现了不止一次
     */
    public static CaseIndex build(@org.jetbrains.annotations.Nullable SwitchIota.MatchType type,
                                  List<SwitchIota.Case> cases) {
        if (type == null) {
            // 未定类型的表:只可能有 null 键,永远不会有精确键,所以建空索引即可
            return new CaseIndex(null);
        }
        CaseIndex index = new CaseIndex(type);

        for (int i = 0; i < cases.size(); i++) {
            Iota value = cases.get(i).getValue();

            // null 是"万能键":它不决定表的类型,也不该被具体类型的校验拦下。
            // 在已确定类型的表里它永远匹配不到(受控值不可能是 null),所以直接跳过。
            if (value instanceof at.petrak.hexcasting.api.casting.iota.NullIota) {
                continue;
            }
            if (!type.accepts(value)) {
                // 类型不符在这里就拦下 —— 照 Java 的定位,这类错误该在建造期发现,
                // 而不是等到执行时"默默不命中"
                throw new HexextraMishaps.CaseTypeMismatch(type.displayKey(), value);
            }

            Object key = cases.get(i).keyFor(type);
            // putIfAbsent 返回旧值:非 null 就说明这个键已经被占了。
            // 不能用 get() == null 判断 —— 下标 0 是合法值、本身不是 null,所以这里安全。
            if (index.slots.putIfAbsent(key, i) != null) {
                throw new HexextraMishaps.DuplicateCase(value);
            }
        }

        return index;
    }

    /**
     * 查表。
     *
     * <p>调用方应当先用 {@link SwitchIota.MatchType#accepts} 确认过类型,
     * 否则类型不符时会直接返回 {@code null}(退化成"不命中")。
     *
     * @return 命中的 case 下标;{@code null} 表示没有 case 命中(调用方应转去 default)
     */
    public @Nullable Integer lookup(Iota value) {
        if (!this.type.accepts(value)) {
            return null;
        }
        return this.slots.get(this.type.keyOf(value));
    }
}
