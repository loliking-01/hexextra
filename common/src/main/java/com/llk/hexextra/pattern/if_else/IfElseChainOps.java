package com.llk.hexextra.pattern.if_else;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.utils.TreeList;
import com.llk.hexextra.core.HexextraMishaps;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 条件链的<b>纯数据操作</b> —— 拼接、接 else、拆散、批量拼接。
 *
 * <h2>为什么单独一类</h2>
 * 这些操作既被算术层(加法之馏化 / 外科医生之提整 等)用,也被动作(造链)用。
 * 抽出来以后两边共享同一套语义,不会出现"算术能接、动作接出来不一样"的情况。
 *
 * <h2>核心不变式</h2>
 * <ul>
 *   <li>一条链的 {@code branches} 可以为空(那就是"只有 else"的链);</li>
 *   <li>else 是<b>唯一</b>的,而且永远在最后 —— 所以往已经带 else 的链后面再接东西要报错;</li>
 *   <li>分支本身只是「条件 + 代码块」的配对,不含 else。</li>
 * </ul>
 */
public final class IfElseChainOps {

    /**
     * 拼两条链。
     *
     * <p>左链若已带 else,再接东西就是非法的(else 后面不能再有分支)—— 报
     * {@code ChainAlreadyHasElse},而不是默默丢掉左链的 else。
     */
    public static IfElseIota concat(IfElseIota first, IfElseIota second) {
        if (first.getElseBody() != null) {
            throw new HexextraMishaps.ChainAlreadyHasElse();
        }

        List<IfElseIota.Branch> branches = new ArrayList<>(first.getBranches());
        branches.addAll(second.getBranches());

        // else 取右链的(左链已经保证没有)
        return new IfElseIota(branches, second.getElseBody());
    }

    /** 给链接上 else。已经带 else 的链再接受不了,报错。 */
    public static IfElseIota attachElse(IfElseIota chain, TreeList<Iota> elseBody) {
        if (chain.getElseBody() != null) {
            throw new HexextraMishaps.ChainAlreadyHasElse();
        }
        return new IfElseIota(chain.getBranches(), elseBody);
    }

    /**
     * 把链拆散。
     *
     * <p>每条分支变成一个<b>单分支链</b>;若原链带 else,最后再追加一个"只有 else"的链。
     * 这样 {@code splat} 之后再用 {@code concat} 拼回去是无损的 —— 拆装可逆。
     */
    public static List<IfElseIota> splat(IfElseIota chain) {
        List<IfElseIota> out = new ArrayList<>();
        for (IfElseIota.Branch branch : chain.getBranches()) {
            out.add(new IfElseIota(List.of(branch), null));
        }
        if (chain.getElseBody() != null) {
            out.add(new IfElseIota(List.of(), chain.getElseBody()));
        }
        return out;
    }

    /**
     * 把一批链拼成一条。
     *
     * <p>中间任何一条带 else 都必须排在最后,否则报错(与 {@link #concat} 同一规矩)。
     */
    public static IfElseIota concatAll(List<IfElseIota> chains) {
        if (chains.isEmpty()) {
            return new IfElseIota(List.of(), null);
        }
        IfElseIota acc = chains.get(0);
        for (int i = 1; i < chains.size(); i++) {
            acc = concat(acc, chains.get(i));
        }
        return acc;
    }

    /**
     * 尝试把一批 iota 收拢成一条链;只要有一个不是链、或不足两条,就返回 null。
     *
     * <p>给「群体之策略」(last_n_list)的算术算子用:那里必须能<b>无副作用地失败</b>
     * —— 不是全链时要安静地"不匹配",把控制权交回原实现。
     */
    public static @Nullable IfElseIota concatAllOrNull(List<Iota> iotas) {
        if (iotas.size() < 2) {
            return null;
        }
        List<IfElseIota> chains = new ArrayList<>(iotas.size());
        for (Iota iota : iotas) {
            if (!(iota instanceof IfElseIota c)) {
                return null;
            }
            chains.add(c);
        }
        try {
            return concatAll(chains);
        } catch (RuntimeException ex) {
            return null;
        }
    }
    private IfElseChainOps() {
    }
}
