package com.llk.hexextra.pattern.for_loop;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.eval.vm.FrameEvaluate;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import kotlin.Pair;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * 「for 循环」的续延帧 —— 让一段代码块在<b>同一个栈</b>上连续执行 N 次。
 *
 * <h2>它和咒法学自带的 Thoth's Gambit 有什么区别</h2>
 * 游戏自带的 {@code FrameForEach} 是「遍历数据」:每轮用「固定上下文栈 + 一个数据」拼出一个<b>全新的栈</b>,
 * 最后把每轮结果汇总成一个列表。本帧不一样:它<b>完全不碰栈</b>,于是第 i 轮跑完留下的栈
 * 自然就是第 i+1 轮的起始栈 —— 这才是 Java {@code for} 循环的语义。
 *
 * <h2>循环是怎么转起来的</h2>
 * 施法 VM 的主循环是「弹出续延栈顶的帧 → 调用它的 evaluate → 把返回的新续延装回去」。
 * 所以本帧只要在 evaluate 里<b>把自己再压一次</b>,再压一个要执行的代码块,循环就成立了:
 * <pre>
 *   continuation.pushFrame(new FrameForLoop(code, remaining - 1))   // 下一轮的自己
 *               .pushFrame(new FrameEvaluate(code, true))           // 本轮的代码块
 * </pre>
 * pushFrame 是压到栈顶,所以必须<b>先压自己、后压代码块</b>:
 * 这样代码块执行完毕后,从栈里弹出来的才是下一轮的循环帧。
 *
 * <h2>为什么字段这么少</h2>
 * 因为「循环的工作栈」不需要本帧保存 —— 它就活在 {@link CastingImage#getStack()} 里,
 * 在两次 evaluate 之间由 VM 自己带着走。本帧只需要记住「还要跑几轮」和「跑什么」。
 *
 * @param code      要重复执行的代码块(一串待求值的 iota)
 * @param remaining 还需要执行几轮;减到 0 时本帧不再压任何帧,循环自然结束
 */
public record FrameForLoop(TreeList<Iota> code, int remaining) implements ContinuationFrame {

    /**
     * VM 每从续延栈顶弹出本帧一次,就调用一次本方法推进计算。
     *
     * @param continuation <b>本帧之下</b>剩下的续延(注意:不含本帧自己)。
     *                     往它上面 pushFrame 就是「我做完之后接着做什么」。
     * @param level        当前服务端世界
     * @param harness      施法 VM 本体,通过 {@link CastingVM#getImage()} 拿到当前施法状态
     */
    @Override
    public CastResult evaluate(SpellContinuation continuation, ServerLevel level, CastingVM harness) {
        CastingImage image = harness.getImage();

        if (this.remaining <= 0) {
            // 循环结束:一个帧都不压,控制权自然交回下层续延。
            // 栈原样带走 —— 这就是「循环体最后一轮的产出留在栈上」。
            //
            // 但瞬态求值标志要清干净:循环是一道完整的边界,最后一轮末尾留下的开关
            // (例如循环体最后一条恰好是 simulate)绝不能漏到循环外面,
            // 否则会去空跑"循环之后的指令"—— 那是玩家完全没打算空跑的东西。
            return this.result(continuation, withCleanTransients(image, image.getOpsConsumed()));
        }

        // 还有活儿:先压下一轮的自己,再压本轮的代码块(顺序见类注释)。
        // FrameEvaluate 的第二个参数 true 表示这是一次元求值(meta-eval),
        // 与「赫尔墨斯之书」和 Thoth 用的是同一条路径。
        SpellContinuation next = continuation
                .pushFrame(new FrameForLoop(this.code, this.remaining - 1))
                .pushFrame(new FrameEvaluate(this.code, true));

        // 关键:这里不修改 stack,只把算力计数 +1。
        // 每轮至少消耗 1 点操作数,于是玩家写出天文数字的循环次数时,
        // 会被引擎自己的 maxOpCount(默认 100000)兜住并抛出 MishapEvalTooMuch,
        // 不至于真把服务端卡死。
        //
        // 同时把瞬态标志清零,让每一轮都从相同的求值模式开始 ——
        // 理由与取舍见 withCleanTransients 的注释。
        return this.result(next, withCleanTransients(image, image.getOpsConsumed() + 1));
    }

    /**
     * 复制一份施法状态,保留栈、把指定算力计数写进去,并<b>清空全部瞬态求值标志</b>。
     *
     * <h2>什么是瞬态求值标志</h2>
     * 指的是描述"下一步该怎么求值"的开关,而不是咒语累积的数据:
     * <ul>
     *   <li>{@code parenCount} / {@code parenthesized} —— 括号嵌套层数、括号内已收集的 iota</li>
     *   <li>{@code escapeNext} —— 下一条 iota 不作为代码执行,而是当作"值"推入</li>
     *   <li>{@code simulateNext} —— 下一条 iota 空跑(真的算一遍,但结果整个丢弃,只压一个成败布尔)</li>
     * </ul>
     * 三者的共同点是:<b>生命周期只有"一步"</b>,由 {@code CastingVM.executeInner} 在下一步
     * 开头消费掉并立刻复位。玩家无法用任何图案"读取"它们,只能靠"上一条画了什么"间接控制。
     *
     * <h2>为什么循环里必须清</h2>
     * 循环体的每一轮都应当是同一个代码块、从相同的求值模式开始。
     * 若放任这些开关跨轮携带,就会出现"第 1 轮正常执行、第 2 轮起第一条指令被静默空跑"
     * 这种<b>同一循环体行为不一致</b>的情况 —— 那恰恰破坏了 for 循环的定义性特征。
     *
     * <h2>与引擎自带辅助方法的关系</h2>
     * 咒法学自己的 {@code CastingImage.withResetEscape()} 清了前两组,注释里也自称清的是
     * "escape/paren-related fields",但<b>漏了 {@code simulateNext}</b>。本方法把它一并清掉,
     * 于是本帧既等价于 {@code withUsedOp().withResetEscape()},又补上了那个遗漏,
     * 循环因此成为一道完整的瞬态边界。
     *
     * <h2>为什么把参数顺序逐行标注</h2>
     * {@code copy} 有 7 个位置参数,其中连着两个 boolean
     * ({@code escapeNext}、{@code simulateNext})。写错一位编译器<b>不会报错</b>,
     * 只会静默地把两者对调 —— 所以这里把每个实参的含义都写在行尾。
     * 顺序严格取自 {@code CastingImage} 的字段声明:
     * {@code stack, parenCount, parenthesized, escapeNext, simulateNext, opsConsumed, components}。
     *
     * @param image       被复制的施法状态
     * @param opsConsumed 新状态里的算力计数(其余字段要么原样照抄,要么清零)
     */
    private static CastingImage withCleanTransients(CastingImage image, long opsConsumed) {
        return image.copy(
                image.getStack(),                                  // 栈:原样保留,这是"连续演变"的落点
                0,                                                 // parenCount:归零
                TreeList.<CastingImage.ParenthesizedIota>empty(),  // parenthesized:清空
                false,                                             // escapeNext:归零
                false,                                             // simulateNext:归零(引擎漏掉的那个)
                opsConsumed,                                       // opsConsumed:由调用方决定
                image.getComponents());                            // components:原样保留
    }

    /**
     * 组装一次求值结果。三处 evaluate 分支只有「续延」和「施法状态」不同,其余完全一致。
     */
    private CastResult result(SpellContinuation continuation, CastingImage image) {
        return new CastResult(
                new ListIota(this.code),       // 本轮"执行了什么",供客户端渲染图案
                continuation,
                image,
                List.<OperatorSideEffect>of(), // 循环本身不产生任何副作用(不扣媒质、不放粒子)
                ResolvedPatternType.EVALUATED, // 必须返回"成功",否则 VM 主循环会立刻提前退出
                HexEvalSounds.THOTH.get()      // 沿用 Thoth 的施法音效:同为"迭代"类操作
        );
    }

    /**
     * 当玩家在循环体里画了 halt(中断)时,咒法学需要决定「这个中断停在哪一层」。
     *
     * <p>返回的 boolean 表示「中断是否到此为止」:
     * <ul>
     *   <li>{@code true} —— 中断被本帧吸收,也就是<b>跳出循环</b>;这正是我们要的 break 语义。</li>
     *   <li>{@code false} —— 中断继续向更外层传播(会连外层的 eval 一起中断)。</li>
     * </ul>
     *
     * <p>栈原样透传:跳出循环时,循环体已经算出来的东西要留在栈上。
     * 对比之下 {@code FrameEvaluate} 返回 {@code false},因为它只是「一层代码」而非循环边界。
     */
    @Override
    public Pair<Boolean, TreeList<Iota>> breakDownwards(TreeList<Iota> stack) {
        return new Pair<>(true, stack);
    }

    /**
     * 本帧内部含有多少个 iota。咒法学用它判断续延是否还能安全序列化
     * (续延会被写进 NBT、也会同步给客户端)。
     *
     * <p>这里返回「代码块长度 + 1」:代码块本身要存,轮次计数虽然不是 iota 但也占一份开销,
     * 与 {@code FrameForEach} 把各字段长度相加的算法保持同一口径。
     */
    @Override
    public int size() {
        return this.code.size() + 1;
    }

    /** 告诉咒法学本帧属于哪个已注册的类型 —— 序列化时靠它找到编解码器。 */
    @Override
    public ContinuationFrame.Type<?> getType() {
        return TYPE;
    }

    /**
     * 本帧的类型与编解码器。
     *
     * <p>必须把它注册进 {@code hexcasting:continuation_type} 注册表,
     * 否则续延在写 NBT 或同步到客户端时会因为找不到编码器而失败 —— 这是自定义续延帧最容易踩的坑。
     *
     * <p>编解码器写法直接照搬 {@code FrameForEach}:字段名 + getter + 构造器引用,
     * 网络编码用 {@code StreamCodec.composite}。
     */
    public static final ContinuationFrame.Type<FrameForLoop> TYPE = new ContinuationFrame.Type<>() {

        /** 存档(NBT / JSON)用的编码器。 */
        private final MapCodec<FrameForLoop> codec = RecordCodecBuilder.mapCodec(inst -> inst.group(
                TreeList.codecOf(IotaType.TYPED_CODEC).fieldOf("code").forGetter(FrameForLoop::code),
                Codec.INT.fieldOf("remaining").forGetter(FrameForLoop::remaining)
        ).apply(inst, FrameForLoop::new));

        /** 网络同步用的编码器(客户端要靠它还原服务端的施法过程以做渲染)。 */
        private final StreamCodec<RegistryFriendlyByteBuf, FrameForLoop> streamCodec = StreamCodec.composite(
                IotaType.TYPED_STREAM_CODEC.apply(TreeList.streamCodecOp()), FrameForLoop::code,
                ByteBufCodecs.VAR_INT, FrameForLoop::remaining,
                FrameForLoop::new
        );

        @Override
        public MapCodec<FrameForLoop> codec() {
            return this.codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FrameForLoop> streamCodec() {
            return this.streamCodec;
        }
    };
}
