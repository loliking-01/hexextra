package com.llk.hexextra.pattern.if_else;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.castables.SpecialHandler;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.CastingImages;
import com.llk.hexextra.pattern.switch_case.CaseIota;
import com.llk.hexextra.pattern.switch_case.SwitchIota;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 让「群体之拆解」({@code splat})与「群体之策略」({@code last_n_list})认识条件链与 switch 表。
 *
 * <h2>为什么用 {@link SpecialHandler}</h2>
 * 咒法学在解析图案时,会<b>先</b>把所有 {@code special_handler} 跑一遍,任何一个返回非 null
 * 就用它,否则才看正常的角度签名。这正是"替换某枚图案行为"的官方口子 ——
 * 不需要 Mixin,也就不必为"目标是别的 mod 的类"去解决混淆映射问题。
 *
 * <h2>为什么 tryMatch 里只做常数时间比较</h2>
 * 每个图案解析时都会把所有 special handler 跑一遍,所以这里只能拿
 * {@code HexPattern.getSignature()} 比对形状,绝不能干重活。
 */
public final class IfElseSpecialHandlers {

    /** 「群体之拆解」的图案:角度串 {@code qwaeawq},起始方向西北。 */
    public static final HexPattern SPLAT_PATTERN =
            HexPattern.fromAngleString("qwaeawq", HexDir.NORTH_WEST);

    /** 「群体之策略」的图案:角度串 {@code ewdqdwe},起始方向西南。 */
    public static final HexPattern LAST_N_LIST_PATTERN =
            HexPattern.fromAngleString("ewdqdwe", HexDir.SOUTH_WEST);

    public static final SpecialHandler.Factory<Handler> SPLAT = (pattern, env) ->
            SPLAT_PATTERN.getSignature().equals(pattern.getSignature()) ? new Handler(new OpSplatChains(), "splat") : null;

    public static final SpecialHandler.Factory<Handler> LAST_N_LIST = (pattern, env) ->
            LAST_N_LIST_PATTERN.getSignature().equals(pattern.getSignature()) ? new Handler(new OpCollectChains(), "last_n_list") : null;

    /** 一个最小的 {@link SpecialHandler}:只包一个 Action 与一个名字。 */
    public record Handler(Action action, String name) implements SpecialHandler {

        @Override
        public Action act() {
            return this.action;
        }

        @Override
        public Component getName() {
            return Component.translatable("hexcasting.action.hexcasting:" + this.name);
        }
    }

    // =======================================================================
    // 「群体之拆解」:把链摊成单分支链 / 把表摊成一条条 case
    // =======================================================================

    /**
     * 参数是<b>一条条件链</b>时摊成若干单分支链;是<b>一张 switch 表</b>时摊成若干 case;
     * 其余情况保持原版行为(列表照常摊开,非列表照常报错)。
     */
    public static final class OpSplatChains implements Action {

        @Override
        public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
            TreeList<Iota> stack = image.getStack();
            if (stack.isEmpty()) {
                throw new MishapNotEnoughArgs(1, 0);
            }

            Iota top = stack.last();

            List<Iota> produced = new ArrayList<>();
            if (top instanceof IfElseIota chain) {
                // 每条分支变成一个单分支链;若原链带 else,最后再追加一个"只有 else"的链
                produced.addAll(IfElseChainOps.splat(chain));
            } else if (top instanceof SwitchIota table) {
                for (CaseIota entry : table.getCases()) {
                    produced.add(entry);
                }
            } else if (top instanceof ListIota list) {
                // 原版行为:把列表摊开
                list.getList().forEach(produced::add);
            } else {
                throw new MishapInvalidIota(top, 0, Component.translatable("hexcasting.iota_type.list"));
            }

            return new OperationResult(
                    CastingImages.withStack(image.withUsedOp(), stack.dropRight(1).appendedAll(produced)),
                    List.<OperatorSideEffect>of(),
                    continuation,
                    HexEvalSounds.NORMAL_EXECUTE.get());
        }
    }

    // =======================================================================
    // 「群体之策略」:把栈顶 n 个全为链的 iota 收拢成一条
    // =======================================================================

    /**
     * 与原版一样:先弹一个 {@code n},再取它下面的 n 个。
     *
     * <p>那 n 个<b>全是条件链</b>时收拢成一条(等价于连续做加法之馏化);
     * 否则保持原版行为(打包成一个列表)。
     */
    public static final class OpCollectChains implements Action {

        @Override
        public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
            TreeList<Iota> stack = image.getStack();
            if (stack.isEmpty()) {
                throw new MishapNotEnoughArgs(1, 0);
            }

            // 与原版一致:拿栈顶那一个当 n,取值范围 [0, stack.size()-1]
            int n = OperatorUtils.getPositiveIntUnderInclusive(stack, stack.size() - 1, stack.size() - 1, 1);

            TreeList<Iota> rest = stack.dropRight(1);
            int take = Math.min(n, rest.size());
            TreeList<Iota> picked = rest.takeRight(take);
            TreeList<Iota> kept = rest.dropRight(take);

            IfElseIota merged = IfElseChainOps.concatAllOrNull(picked);

            TreeList<Iota> newStack = (merged != null)
                    ? kept.appended(merged)
                    : kept.appended(new ListIota(picked));

            return new OperationResult(
                    CastingImages.withStack(image.withUsedOp(), newStack),
                    List.<OperatorSideEffect>of(),
                    continuation,
                    HexEvalSounds.NORMAL_EXECUTE.get());
        }
    }

    private IfElseSpecialHandlers() {
    }
}
