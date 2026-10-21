package com.llk.hexextra.pattern.switch_case;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.utils.TreeList;
import com.llk.hexextra.core.HexextraMishaps;

import java.util.List;

/**
 * 造一条分支:从栈上取「键」和「代码块」,产出 <b>只有一条分支的 {@link SwitchIota}</b>。
 *
 * <h2>为什么产出单分支的表而不是独立的 case iota</h2>
 * 早先「凯斯之策略」产出的是独立类型 {@code hexextra:case},结果"造一条分支"与
 * "造一张表"是两种类型,玩家分不清手里的是半成品还是成品。现在统一成 switch 本身:
 * 这个图案造出的就是一张只有一条分支的表,可以直接执行;再用「加法之馏化」
 * 与别的表合并即可长大。
 *
 * <h2>栈序</h2>
 * 先压键,再压代码块(后者在栈顶)——
 * 与 {@code if_chain}(先条件、后代码块)的方向一致,都遵循咒法学「先压的在深处」的通例。
 *
 * <h2>类型由这个键推出</h2>
 * 第一条分支的键顺便把表的匹配类型定下来:
 * <ul>
 *   <li>数值 / 布尔 / 图案 —— 咒法学自带的三种,键用精确的原生形式;</li>
 *   <li><b>其它任何 iota</b>(其它模组的自定义类型都算)——
 *       按它的<b>注册 id</b> 区分类型,键用序列化后的 {@code Tag}。
 *       类型 id 是运行时从 iota 自己身上读的,我们不写死任何外部 id;</li>
 *   <li>{@code null} —— 不决定类型,可以进任何表。</li>
 * </ul>
 *
 * <p>本动作不消耗媒质(mediaCost 默认 0)。
 */
public final class OpCreateCase implements ConstMediaAction {

    /** 从栈上消费两个参数:常量 + 代码块。 */
    private static final int ARGC = 2;

    @Override
    public int getArgc() {
        return ARGC;
    }

    @Override
    public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
        // args[0] 是较深的那个(常量),args[1] 是栈顶(代码块)
        Iota value = args.get(0);
        // getList 会在参数不是列表时抛 MishapInvalidIota,并把错误定位到对应参数上
        TreeList<Iota> body = OperatorUtils.getList(args, 1, ARGC);

        // null 是允许的键:它不决定表的类型,所以不参与"必须是常量"的检查
        if (!(value instanceof at.petrak.hexcasting.api.casting.iota.NullIota)
                && SwitchIota.MatchType.of(value) == null) {
            // 列表 / 实体 / 向量这类值算不出规范键,不可能当 case 的匹配值
            throw new HexextraMishaps.CaseNotConstant(value);
        }

        return List.of(SwitchIota.fromCases(List.of(new SwitchIota.Case(value, body)), null));
    }
}
