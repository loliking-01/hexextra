package com.llk.hexextra.pattern.switch_case;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.utils.TreeList;
import com.llk.hexextra.core.HexextraMishaps;

import java.util.List;

/**
 * 造一条 case:从栈上取「常量」和「代码块」,产出 <b>{@link SwitchIota.Case}</b>。
 *
 * <h2>为什么产出独立的 SwitchIota.Case 而不是"只含一条 case 的表"</h2>
 * 如果产出表,那么「造一条 case」与「造一张表」就是同一种 iota,只有条数不同 ——
 * 玩家从类型上分不清手里是原料还是成品。分开以后:
 * <ul>
 *   <li>{@code case} 产出<b>原料</b>,用「加法之馏化」把若干条拼成表、或往已有表里追加;</li>
 *   <li>只有 {@link SwitchIota} 是可执行的<b>成品</b> —— 单独对一条 case 执行会得到
 *       "未逃逸的值"报错。</li>
 * </ul>
 *
 * <h2>栈序</h2>
 * 先压常量,再压代码块(后者在栈顶)—— 与 if_else 家族的「先条件、后代码块」方向一致,
 * 都遵循咒法学「先压的在深处」的通例。
 *
 * <h2>类型由这个常量推出</h2>
 * 所以单用它就能开始搭表:第一条 case 顺便把表的匹配类型定下来。
 * 常量不是可匹配的类型(列表 / 实体 / 向量)时当场报错。
 *
 * <h2>0.11.4 的实现要点</h2>
 * {@link ConstMediaAction} 是 Kotlin 接口,{@code val argc} 在 Java 侧实现为 {@link #getArgc()};
 * 取参数用 {@link ActionUtilsKt}(对 {@code List<Iota>} 的扩展函数)。
 * 本动作不消耗媒质(mediaCost 默认 0)。
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
        TreeList<Iota> body = TreeList.from(OperatorUtils.getList(asList(args), 1, ARGC));

        // null 是允许的键:它不决定表的类型,所以不参与"必须是常量"的检查
        if (!(value instanceof at.petrak.hexcasting.api.casting.iota.NullIota)
                && SwitchMatchType.of(value) == null) {
            // 列表 / 实体 / 向量这类值算不出规范键,不可能当 case 的匹配值
            throw new HexextraMishaps.CaseNotConstant(value);
        }

        return List.of(SwitchIota.fromCases(List.of(new SwitchIota.Case(value, body)), null));
    }

    /**
     * 把协变的参数列表转成 {@code OperatorUtils} 扩展函数要求的 {@code List<Iota>}。
     * Kotlin 的 {@code List<Iota>} 编译到 Java 是 {@code List<? extends Iota>},而扩展函数收的是后者。
     */
    @SuppressWarnings("unchecked")
    private static List<Iota> asList(List<? extends Iota> args) {
        return (List<Iota>) args;
    }
}
