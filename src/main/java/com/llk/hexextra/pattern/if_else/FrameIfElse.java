package com.llk.hexextra.pattern.if_else;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.eval.vm.FrameEvaluate;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.CastingImages;
import com.llk.hexextra.core.HexextraMishaps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import kotlin.Pair;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 「if / elseif / else」的续延帧 —— 逐分支求值条件、按结果决定跑哪个代码块。
 *
 * <h2>它做两件事,所以有个 {@code deciding} 开关</h2>
 * 求值一个条件需要<b>跨两次 VM 步</b>:先跑条件代码块,跑完再回头看栈上留下什么。
 * 于是同一帧被弹出来两次、承担两种职责:
 * <ul>
 *   <li>{@code deciding == false} —— 「该跑第 {@code index} 个分支的条件了」。
 *       此时记下当前栈深,压出本帧的 {@code deciding == true} 版本 + 一个求值条件代码块的帧。</li>
 *   <li>{@code deciding == true} —— 「条件刚跑完,该看结果了」。
 *       检查栈是否恰好比之前多出一个布尔值:是且为真 → 接着跑该分支的代码块;
 *       为假 → 顺延到下一分支,没有下一分支就跑 else,再没有就什么都不做。</li>
 * </ul>
 *
 * <h2>为什么用 boolean 而不是枚举表示阶段</h2>
 * 只有两种阶段,用枚举还要再写一套枚举编解码器(而且序列化里多一个可能对不上的字段)。
 * 布尔在 NBT 与网络两边都是最不可能写错的类型。
 *
 * <h2>栈约定(重要)</h2>
 * 条件代码块与代码块都跑在<b>同一个栈</b>上(连续演变)。条件跑完后,
 * 本帧要求栈<b>恰好</b>比条件执行前多一个值,且那个值必须是布尔 ——
 * 也就是说条件块不能顺手改动原有栈内容(需要读取原有内容时,先用「复制」类图案复制一份)。
 * 不符合就抛 {@link HexextraMishaps.IfConditionCount} 或 {@link HexextraMishaps.IfConditionNotBool}。
 *
 * @param branches        整条链的分支(不裁剪,靠 {@code index} 指出当前进度)
 * @param index           当前分支下标;{@code deciding == true} 时表示"刚判完的那个"
 * @param deciding        false = 该求值条件;true = 条件已求值完,该看结果
 * @param elseBody        所有条件都不满足时执行的代码块,可为 null
 * @param stackSizeBefore 条件执行前的栈深(只在 {@code deciding == true} 时有意义)
 */
public record FrameIfElse(
        List<IfElseIota.Branch> branches,
        int index,
        boolean deciding,
        @Nullable TreeList<Iota> elseBody,
        int stackSizeBefore
) implements ContinuationFrame {

    @Override
    public CastResult evaluate(SpellContinuation continuation, ServerLevel level, CastingVM harness) {
        CastingImage image = harness.getImage();

        if (!this.deciding) {
            // ---- 阶段一:该求值第 index 个分支的条件 ----
            IfElseIota.Branch branch = this.branches.get(this.index);

            // 记下条件执行前的栈深,下一步才能判断"条件是否恰好留下一个值"
            TreeList<Iota> condition = branch.condition();
            IfElseFeature.trace("阶段1:求值第 {} 分支条件,条件块 {} 个元素,执行前栈深={}",
                    this.index, condition.size(), image.getStack().size());

            SpellContinuation next = continuation
                    .pushFrame(new FrameIfElse(this.branches, this.index, true, this.elseBody,
                            image.getStack().size()))
                    .pushFrame(new FrameEvaluate(condition, true));

            return this.cast(next, image.withUsedOp(), condition);
        }

        // ---- 阶段二:条件刚跑完,检查栈 ----
        TreeList<Iota> stack = image.getStack();
        int produced = stack.size() - this.stackSizeBefore;
        IfElseFeature.trace("阶段2:第 {} 分支条件跑完,栈深 {} (执行前 {}),产出={},栈顶={}",
                this.index, stack.size(), this.stackSizeBefore, produced,
                stack.isEmpty() ? "<空>" : stack.last().getClass().getSimpleName());

        if (produced != 1) {
            // 多留、少留、甚至把原有栈内容吃掉了,都算写错
            IfElseFeature.trace("阶段2 → 报错:条件必须恰好留下 1 个值,实际 {}", produced);
            return this.mishap(new HexextraMishaps.IfConditionCount(produced), harness, continuation, stack);
        }

        Iota top = stack.last();
        if (!(top instanceof BooleanIota bool)) {
            IfElseFeature.trace("阶段2 → 报错:条件留下的不是布尔值,而是 {}", top.getClass().getSimpleName());
            return this.mishap(new HexextraMishaps.IfConditionNotBool(top), harness, continuation, stack);
        }

        // 把这个布尔值从栈上消费掉 —— 它是条件的"结果",不该留在栈上
        CastingImage consumed = CastingImages.withStack(image, stack.dropRight(1));

        if (bool.getBool()) {
            // 命中该分支:接着跑它的代码块。跑完之后不需要本帧再做任何事,
            // 所以只压一个求值帧,控制权随后自然交回下层续延。
            TreeList<Iota> body = this.branches.get(this.index).body();
            IfElseFeature.trace("阶段2 → 条件为真,执行第 {} 分支的代码块({} 个元素)",
                    this.index, body.size());
            SpellContinuation next = continuation.pushFrame(new FrameEvaluate(body, true));
            return this.cast(next, consumed.withUsedOp(), body);
        }

        int nextIndex = this.index + 1;
        if (nextIndex < this.branches.size()) {
            // 顺延到下一个分支,重新进入阶段一
            TreeList<Iota> nextCondition = this.branches.get(nextIndex).condition();
            IfElseFeature.trace("阶段2 → 条件为假,顺延到第 {} 分支", nextIndex);
            SpellContinuation next = continuation.pushFrame(
                    new FrameIfElse(this.branches, nextIndex, false, this.elseBody, -1));
            return this.cast(next, consumed.withUsedOp(), nextCondition);
        }

        if (this.elseBody != null) {
            // 所有条件都不满足,跑 else(不做任何条件判断)
            IfElseFeature.trace("阶段2 → 条件为假且没有更多分支,执行 else({} 个元素)",
                    this.elseBody.size());
            SpellContinuation next = continuation.pushFrame(new FrameEvaluate(this.elseBody, true));
            return this.cast(next, consumed.withUsedOp(), this.elseBody);
        }

        // 既没有命中分支也没有 else —— 整个 if 链什么都不做,控制权交回下层
        IfElseFeature.trace("阶段2 → 条件为假、没有更多分支、也没有 else —— 整条链什么都不做");
        return this.cast(continuation, consumed.withUsedOp(), TreeList.empty());
    }

    /**
     * halt(中断)打到本帧时的行为。
     *
     * <p>返回 {@code false} 表示「中断继续往外传」—— 因为 if 链<b>不是循环</b>,
     * 它没有"跳出自己"这种语义。条件或代码块里画 halt,应当照常中断更外层的求值,
     * 这与 {@code FrameEvaluate} 的处理一致(它也返回 false)。
     * 对照之下 for 循环帧返回 true,因为那里"break 出循环"才是有意义的。
     */
    @Override
    public Pair<Boolean, TreeList<Iota>> breakDownwards(TreeList<Iota> stack) {
        return new Pair<>(false, stack);
    }

    /**
     * 本帧携带的 iota 总量 —— 咒法学用它判断续延是否还允许序列化。
     * 口径与 {@code FrameForEach} 一致:把各代码块的长度加起来。
     */
    @Override
    public int size() {
        int total = 0;
        for (IfElseIota.Branch branch : this.branches) {
            total += branch.condition().size();
            total += branch.body().size();
        }
        if (this.elseBody != null) {
            total += this.elseBody.size();
        }
        return total + 1;
    }

    /** 告诉咒法学本帧属于哪个已注册类型 —— 序列化时靠它找编解码器。 */
    @Override
    public ContinuationFrame.Type<?> getType() {
        return TYPE;
    }

    /**
     * 把报错<b>折进 {@link CastResult} 返回</b>,而不是抛出去。
     *
     * <h2>⚠ 为什么绝不能在这里 throw</h2>
     * 咒法学 VM 主循环里,{@code frame.evaluate(...)} 外面<b>没有 try/catch</b>
     * (唯一那个 catch 包的是 {@code performSideEffects})。源码里那句注释把契约写得很明白:
     * <blockquote>there used to be error checking code here; I'm pretty sure any and all mishaps
     * should already get caught and folded into CastResult by evaluate.</blockquote>
     * 也就是说:<b>续延帧的 evaluate 不许抛异常</b>。真抛出去会直接穿透整个施法流程,
     * 玩家看到的不是设计好的红字提示,而是"内部错误"甚至崩溃。
     *
     * <p>咒法学自己处理 {@code MishapStackSize} / {@code MishapEvalTooMuch} 时,
     * 走的也正是「{@code DoMishap} 副作用 + {@code ERRORED} + MISHAP 音效」这条路。
     * 本方法的写法照抄 {@code PatternIota} 捕获 Mishap 的那段(包括 wipeParens 的处理)。
     */
    private CastResult mishap(Mishap mishap, CastingVM harness, SpellContinuation continuation,
                              TreeList<Iota> stack) {
        // 与 PatternIota 一致:若报错发生在"元求值"过程中(我们的条件/代码块正是元求值),
        // 要把未闭合的括号状态一并清掉 —— 否则元求值已经中止了,括号却还开着。
        boolean wipeParens = continuation instanceof SpellContinuation.NotDone notDone
                && notDone.getFrame() instanceof FrameEvaluate frameEval
                && frameEval.isMetacasting();

        return new CastResult(
                new ListIota(stack),               // 供客户端渲染"出事时在跑哪段代码"
                continuation,
                // newData 传 null 表示"不改动 VM 当前状态";条件已经造成的变化在之前的步里
                // 就已写回了,这里不需要再补。只有要清括号时才给一个新状态。
                wipeParens ? harness.getImage().withResetEscape() : null,
                List.of(new OperatorSideEffect.DoMishap(mishap, new Mishap.Context(null, null))),
                mishap.resolutionType(harness.getEnv()),
                HexEvalSounds.MISHAP.get());
    }

    /** 组装一次求值结果。四条件分支只有「续延」和「施法状态」不同,其余一致。 */
    private CastResult cast(SpellContinuation continuation, CastingImage image, TreeList<Iota> currentBlock) {
        return new CastResult(
                new ListIota(currentBlock),     // 本步在跑哪段代码,供客户端渲染
                continuation,
                image,
                List.<OperatorSideEffect>of(),  // if 链本身不产生副作用(不扣媒质、不放粒子)
                ResolvedPatternType.EVALUATED,  // 必须返回"成功",否则 VM 主循环会提前退出
                HexEvalSounds.HERMES.get()      // 与"赫尔墨斯之书"同类的元求值音效
        );
    }

    /**
     * 本帧的类型与编解码器。
     *
     * <p><b>必须注册</b>进 {@code hexcasting:continuation_type}:续延会被写进存档、
     * 也会同步给客户端,没登记类型的帧会让这两条路径失败 —— 这是自定义续延帧最容易踩的坑。
     *
     * <p>编解码器的字段必须与 record 分量一一对应,网络侧尤其不能多读或少读,
     * 否则字节流会整体错位。
     */
    public static final ContinuationFrame.Type<FrameIfElse> TYPE = new ContinuationFrame.Type<>() {

        private final MapCodec<FrameIfElse> codec = RecordCodecBuilder.mapCodec(inst -> inst.group(
                IfElseCodecs.BRANCH.listOf().fieldOf("branches").forGetter(FrameIfElse::branches),
                Codec.INT.fieldOf("index").forGetter(FrameIfElse::index),
                Codec.BOOL.fieldOf("deciding").forGetter(FrameIfElse::deciding),
                IfElseCodecs.CODE_BLOCK.optionalFieldOf("else")
                        .forGetter(f -> Optional.ofNullable(f.elseBody())),
                Codec.INT.fieldOf("stack_size_before").forGetter(FrameIfElse::stackSizeBefore)
        ).apply(inst, (branches, index, deciding, els, before) ->
                new FrameIfElse(branches, index, deciding, els.orElse(null), before)));

        private final StreamCodec<RegistryFriendlyByteBuf, FrameIfElse> streamCodec = StreamCodec.composite(
                IfElseCodecs.BRANCH_STREAM.apply(ByteBufCodecs.list()), FrameIfElse::branches,
                ByteBufCodecs.VAR_INT, FrameIfElse::index,
                ByteBufCodecs.BOOL, FrameIfElse::deciding,
                ByteBufCodecs.optional(IfElseCodecs.CODE_BLOCK_STREAM),
                f -> Optional.ofNullable(f.elseBody()),
                ByteBufCodecs.VAR_INT, FrameIfElse::stackSizeBefore,
                (branches, index, deciding, els, before) ->
                        new FrameIfElse(branches, index, deciding, els.orElse(null), before));

        @Override
        public MapCodec<FrameIfElse> codec() {
            return this.codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FrameIfElse> streamCodec() {
            return this.streamCodec;
        }
    };
}
