package com.llk.hexextra.pattern.switch_case;

import at.petrak.hexcasting.api.casting.iota.Iota;
import com.llk.hexextra.core.HexextraMishaps;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * switch 的<b>派发表</b> —— 整个功能性能的关键。
 *
 * <h2>为什么不复用 if-else 链的做法</h2>
 * if-else 链要靠<b>逐个求值条件代码块</b>来选分支,每选一次都是一次元求值
 * (压帧、跨若干 VM 步)。而这里是一张哈希表:选中分支只需要 <b>1 次查表</b>,
 * 代码块只在命中之后才求值一次。分支越多,差距越大。
 *
 * <h2>为什么是「单桶」</h2>
 * 一张表只匹配一种类型(由第一条 case 推出,见 {@link SwitchMatchType}),
 * 所以键空间是单一的,一张 {@code HashMap} 就够 —— 不需要先按类型分派再查。
 *
 * <h2>为什么用精确键而不是咒法学的容差相等</h2>
 * 咒法学的 iota 相等带 1e-4 容差,但那个容差<b>不满足传递性</b>
 * (a~b、b~c 却不保证 a~c),而且与精确的 {@code hashCode} 矛盾 ——
 * 哈希表要求"一个值属于哪个桶"有唯一答案,那是等价关系才有的性质。
 * 详见备忘 05 的完整推理。
 *
 * <h2>建表时会做的校验</h2>
 * <ul>
 *   <li>每个 case 的常量必须是<b>这张表的类型</b>,否则 {@code CaseTypeMismatch};</li>
 *   <li>常量不能<b>重复</b>,否则 {@code DuplicateCase}(照 Java 把重复 case 当编译错误)。</li>
 * </ul>
 * 这两条都在<b>建表时</b>检查 —— 也就是第一次执行或第一次被算术读到的时候。
 * 之所以做惰性索引:建造期允许先拼后验(玩家可能分好几步才把表拼完),
 * 提前在 {@code add} 里做 O(n²) 查重反而更糟。
 */
public final class CaseIndex {

    /** 常量键 → case 下标。用 HashMap 是因为键类型混杂(long / Boolean / 哨兵 / List)。 */
    private final Map<Object, Integer> slots;

    private CaseIndex(Map<Object, Integer> slots) {
        this.slots = slots;
    }

    /**
     * 按表声明的类型,为一批 case 建索引。
     *
     * @throws HexextraMishaps.CaseTypeMismatch case 的类型与表不符
     * @throws HexextraMishaps.DuplicateCase    同一个常量出现了两次
     */
    public static CaseIndex build(SwitchMatchType type, List<SwitchIota.Case> cases) {
        if (type == null) {
            // 未定类型的表:只可能有 null 键,永远不会有精确键,建空索引即可
            return new CaseIndex(new HashMap<>(16));
        }
        Map<Object, Integer> slots = new HashMap<>(Math.max(16, cases.size() * 2));

        for (int i = 0; i < cases.size(); i++) {
            SwitchIota.Case entry = cases.get(i);
            Iota value = entry.getValue();

            // null 是"万能键":不决定表的类型,在已定类型的表里也永远命中不到 —— 直接跳过
            if (value instanceof at.petrak.hexcasting.api.casting.iota.NullIota) {
                continue;
            }
            // 常量类型必须与表声明的一致
            if (!type.accepts(value)) {
                throw new HexextraMishaps.CaseTypeMismatch(type.translationKey(), value);
            }

            Object key = type.keyFor(value);
            if (key == null) {
                // accepts 已经保证能生成键,走到这里说明 MatchType 实现有 bug
                throw new IllegalStateException("hexextra: 无法为 case 生成规范键: " + value);
            }

            // 重复的常量:照 Java 的规矩直接报错
            Integer previous = slots.putIfAbsent(key, i);
            if (previous != null) {
                throw new HexextraMishaps.DuplicateCase(value);
            }
        }

        return new CaseIndex(slots);
    }

    /**
     * 查表。
     *
     * @return 命中的 case 下标;没命中返回 null(由调用方决定走 default 还是什么都不做)
     */
    public Integer lookup(Iota scrutinee) {
        Object key = this.keyFor(scrutinee);
        if (key == null) {
            return null;
        }
        return this.slots.get(key);
    }

    /** 把值转成键。类型不符时返回 null(调用方在查表前已经做过类型检查)。 */
    private Object keyFor(Iota iota) {
        SwitchMatchType type = SwitchMatchType.of(iota);
        if (type == null) {
            return null;
        }
        return type.keyFor(iota);
    }

    /** case 条数(方便自检与调试)。 */
    public int size() {
        return this.slots.size();
    }
}
