package com.llk.hexextra.pattern.for_each;

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
 * 本项目<b>自建</b>的 foreach 循环帧 —— 与咒法学自带的托特({@code FrameForEach})是两套东西。
 *
 * <h2>与托特的区别</h2>
 * 托特每轮会把当前元素<b>压进栈</b>;本帧<b>不压</b>,元素只保存在帧里,
 * 由「取当前元素」图案({@code hexextra:get_current})按需读取。
 * 好处是循环体不会因为"栈上凭空多一个值"而打乱原有的栈布局。
 *
 * <h2>字段</h2>
 * <ul>
 *   <li>{@code code} —— 循环体代码块,每轮都跑这一份</li>
 *   <li>{@code pending} —— <b>还没开始</b>的元素(按顺序)</li>
 *   <li>{@code current} —— <b>正在处理</b>的那个元素;循环体的代码块跑的时候,
 *       续延链里这一帧的 {@code current} 就是「取当前元素」要返回的值</li>
 * </ul>
 *
 * <h2>循环体与外面共用同一个栈</h2>
 * 本帧从不替换栈,只推进续延和算力计数。所以循环体的每一轮都接着上一轮的栈继续演变。
 */
public record FrameForEach(TreeList<Iota> code, TreeList<Iota> pending, Iota current)
        implements ContinuationFrame {

    @Override
    public CastResult evaluate(SpellContinuation continuation, ServerLevel level, CastingVM harness) {
        CastingImage image = harness.getImage();

        if (this.pending.isEmpty()) {
            // 全部跑完:一个帧都不压,控制权交回下层续延;栈原样带走。
            return this.result(continuation, withCleanTransients(image, image.getOpsConsumed()));
        }

        Iota element = this.pending.head();
        TreeList<Iota> rest = this.pending.tail();

        // 先压「下一轮的自己」(pending 已经去掉当前元素、current 换成当前元素),
        // 再压本轮的代码块。后压的先跑,所以代码块先执行,跑完回到下一轮的帧。
        SpellContinuation next = continuation
                .pushFrame(new FrameForEach(this.code, rest, element))
                .pushFrame(new FrameEvaluate(this.code, true));

        return this.result(next, withCleanTransients(image, image.getOpsConsumed() + 1));
    }

    /**
     * 复制施法状态:保留栈、写入算力计数、清空全部瞬态求值标志。
     *
     * <p>理由与 {@code FrameForLoop} 里同名方法完全一致 —— 循环是一道完整的瞬态边界,
     * 每一轮都必须从相同的求值模式开始,否则会出现"第 1 轮正常、第 2 轮起首条指令被静默空跑"。
     * 这里连引擎自带的 {@code withResetEscape()} 漏掉的 {@code simulateNext} 一起清掉。
     */
    private static CastingImage withCleanTransients(CastingImage image, long opsConsumed) {
        return image.copy(
                image.getStack(),                                  // 栈:原样保留
                0,                                                 // parenCount:归零
                TreeList.<CastingImage.ParenthesizedIota>empty(),  // parenthesized:清空
                false,                                             // escapeNext:归零
                false,                                             // simulateNext:归零
                opsConsumed,                                       // opsConsumed:调用方决定
                image.getComponents());                            // components:原样保留
    }

    private CastResult result(SpellContinuation continuation, CastingImage image) {
        return new CastResult(
                new ListIota(this.code),
                continuation,
                image,
                List.<OperatorSideEffect>of(),
                ResolvedPatternType.EVALUATED,
                HexEvalSounds.THOTH.get()
        );
    }

    /** 与 for 循环一致:循环自己被 {@code breakDownwards} 丢弃时,栈原样向上传。 */
    @Override
    public Pair<Boolean, TreeList<Iota>> breakDownwards(TreeList<Iota> stack) {
        return new Pair<>(true, stack);
    }

    @Override
    public int size() {
        return this.code.size() + this.pending.size() + 1;
    }

    @Override
    public ContinuationFrame.Type<?> getType() {
        return TYPE;
    }

    /**
     * 续延帧类型。必须注册进 {@code hexcasting:continuation_type},
     * 否则续延写 NBT / 同步客户端时会因找不到编码器而失败。
     */
    public static final ContinuationFrame.Type<FrameForEach> TYPE = new ContinuationFrame.Type<>() {

        private final MapCodec<FrameForEach> codec = RecordCodecBuilder.mapCodec(inst -> inst.group(
                TreeList.codecOf(IotaType.TYPED_CODEC).fieldOf("code").forGetter(FrameForEach::code),
                TreeList.codecOf(IotaType.TYPED_CODEC).fieldOf("pending").forGetter(FrameForEach::pending),
                IotaType.TYPED_CODEC.fieldOf("current").forGetter(FrameForEach::current)
        ).apply(inst, FrameForEach::new));

        private final StreamCodec<RegistryFriendlyByteBuf, FrameForEach> streamCodec = StreamCodec.composite(
                IotaType.TYPED_STREAM_CODEC.apply(TreeList.streamCodecOp()), FrameForEach::code,
                IotaType.TYPED_STREAM_CODEC.apply(TreeList.streamCodecOp()), FrameForEach::pending,
                IotaType.TYPED_STREAM_CODEC, FrameForEach::current,
                FrameForEach::new
        );

        @Override
        public MapCodec<FrameForEach> codec() {
            return this.codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FrameForEach> streamCodec() {
            return this.streamCodec;
        }
    };
}
