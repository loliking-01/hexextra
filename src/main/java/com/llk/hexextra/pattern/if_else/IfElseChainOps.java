package com.llk.hexextra.pattern.if_else;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.utils.TreeList;
import com.llk.hexextra.core.HexextraMishaps;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 条件链的组装 / 拆解操作 —— 被算术重载与 Mixin 重载<b>共用</b>的那部分逻辑。
 *
 * <h2>为什么要单独抽一个类</h2>
 * 「把两条链接起来」这件事同时出现在两个地方:
 * <ul>
 *   <li>算术重载「加法之馏化」——{@link IfElseArithmetic}</li>
 *   <li>Mixin 重载「群体之策略」——把栈上连续的几条链一次性串成一条</li>
 * </ul>
 * 如果两处各写一遍,「已有 else 时报错」这条规则迟早会在其中一处被改歪。
 * 所以规则只写在这里,两边都调它。
 */
public final class IfElseChainOps {

    private IfElseChainOps() {
    }

    /**
     * 把两条链接起来:头链的分支在前,尾链的分支接在后面。
     *
     * <p>尾链若带 else,它会顺理成章成为整条链的最终 else。
     *
     * @throws HexextraMishaps.ChainAlreadyHasElse 头链自己已经有 else 时
     */
    public static IfElseIota concat(IfElseIota head, IfElseIota tail) {
        if (head.getElseBody() != null) {
            throw new HexextraMishaps.ChainAlreadyHasElse();
        }
        List<IfElseIota.Branch> merged = new ArrayList<>(head.getBranches());
        merged.addAll(tail.getBranches());
        return new IfElseIota(merged, tail.getElseBody());
    }

    /**
     * 给链接上一个最终 else 代码块。
     *
     * @throws HexextraMishaps.ChainAlreadyHasElse 该链已经有 else 时
     */
    public static IfElseIota attachElse(IfElseIota head, TreeList<Iota> elseBody) {
        if (head.getElseBody() != null) {
            throw new HexextraMishaps.ChainAlreadyHasElse();
        }
        return new IfElseIota(head.getBranches(), elseBody);
    }

    /**
     * 拆解:把一条链摊成"每一项都是一个单分支链"的一串 iota。
     *
     * <p>每一条分支(条件 + 代码块)压成一个<b>单分支链</b>;若原链带 else,
     * 最后再压一个<b>仅 else 链</b>。这样压出来的每一项都是同一种类型,
     * 重新组装时(用 {@link #concatAll} 或连续的加法)判定是纯类型判定,不会认错。
     *
     * <p>空链(无分支也无 else)摊出来是空的 —— 没什么可摊的。
     */
    public static List<Iota> splat(IfElseIota chain) {
        List<Iota> out = new ArrayList<>(chain.getBranches().size() + 1);
        for (IfElseIota.Branch branch : chain.getBranches()) {
            // 单分支链 = 一条普通分支
            out.add(new IfElseIota(List.of(branch), null));
        }
        if (chain.getElseBody() != null) {
            // 仅 else 链 = 不带条件的最终分支
            out.add(new IfElseIota(List.of(), chain.getElseBody()));
        }
        return out;
    }

    /**
     * 组装:把一串 iota <b>全部都是条件链</b>时,依次串成一条链(等价于连续做加法)。
     *
     * <p>这正是「群体之策略」用来解决"拆开之后一条条加法太慢"的地方。
     *
     * <p>返回 {@code null} 表示<b>不该由本逻辑接手</b>,调用方应保持原有行为。以下情况返回 null:
     * <ul>
     *   <li>里面有任何一个不是条件链 —— 那就该走原本的"打包成列表";</li>
     *   <li>元素少于 2 个 —— "串成一条"至少要两条才有意义。
     *       只有一条时保持原行为(把它包成一个列表),否则
     *       {@code last_n_list(1)} 会变成什么都不做的恒等操作,反而更容易让人困惑。</li>
     * </ul>
     */
    @Nullable
    public static IfElseIota concatAll(List<Iota> iotas) {
        if (iotas.size() < 2) {
            return null;
        }
        for (Iota iota : iotas) {
            if (!(iota instanceof IfElseIota)) {
                return null;
            }
        }

        IfElseIota acc = (IfElseIota) iotas.get(0);
        for (int i = 1; i < iotas.size(); i++) {
            // 复用与「加法之馏化」完全一致的规则:中途某条链自带 else 会在这里报错
            acc = concat(acc, (IfElseIota) iotas.get(i));
        }
        return acc;
    }
}
