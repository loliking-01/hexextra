package com.llk.hexextra.pattern.for_each;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.eval.vm.FrameEvaluate;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.GarbageIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import kotlin.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 本项目<b>自建</b>的 foreach 循环帧 —— 与咒法学自带的托特是两套东西。
 *
 * <h2>与托特的区别</h2>
 * 托特每轮把元素<b>压进栈</b>;本帧<b>不压</b>,元素只保存在帧里,
 * 由「取当前元素」图案按需读取。好处是循环体不会因为"栈上凭空多一个值"而打乱原有栈布局。
 *
 * <h2>字段</h2>
 * <ul>
 *   <li>{@code body} —— 循环体代码块,每轮都跑这一份</li>
 *   <li>{@code pending} —— <b>还没开始</b>的元素(按顺序)</li>
 *   <li>{@code current} —— <b>正在处理</b>的那个元素;循环体跑的时候,
 *       续延链里这一帧的 {@code current} 就是「取当前元素」要返回的值</li>
 * </ul>
 *
 * <h2>循环体与外面共用同一个栈</h2>
 * 本帧从不替换栈,只推进续延和算力计数。
 */
public record FrameForEach(TreeList<Iota> body, TreeList<Iota> pending, Iota current)
        implements ContinuationFrame {

    @Override
    public CastResult evaluate(SpellContinuation continuation, ServerLevel level, CastingVM harness) {
        CastingImage image = harness.getImage();

        if (this.pending.isEmpty()) {
            // 全部跑完:一个帧都不压,控制权交回下层续延;栈原样带走。
            return result(continuation, withCleanTransients(image, image.getOpsConsumed()));
        }

        Iota element = this.pending.head();
        TreeList<Iota> rest = this.pending.tail();

        // 先压「下一轮的自己」(pending 去掉当前元素、current 换成该元素),再压本轮的代码块。
        // 后压的先跑,所以代码块先执行,跑完回到下一轮的帧。
        SpellContinuation next = continuation
                .pushFrame(new FrameForEach(this.body, rest, element))
                .pushFrame(new FrameEvaluate(new at.petrak.hexcasting.api.casting.SpellList.LList(this.body), true));

        return result(next, withCleanTransients(image, image.getOpsConsumed() + 1));
    }

    /**
     * 复制施法状态:保留栈、写入算力计数、清空全部瞬态求值标志。
     *
     * <p>理由与 {@code FrameForLoop} 完全一致 —— 循环是一道完整的瞬态边界,
     * 每一轮都必须从相同的求值模式开始。0.11.4 的 {@code withResetEscape()} 只清括号与转义,
     * 这里照 {@code FrameForLoop} 的做法把该清的都清掉。
     */
    private static CastingImage withCleanTransients(CastingImage image, long opsConsumed) {
        return image.copy(
                image.getStack(),                                  // 栈:原样保留
                0,                                                 // parenCount:归零
                TreeList.<CastingImage.ParenthesizedIota>empty(),  // parenthesized:清空
                false,                                             // escapeNext:归零
                opsConsumed,                                       // opsConsumed:调用方决定
                image.getUserData());                              // userData:原样保留
    }

    private CastResult result(SpellContinuation continuation, CastingImage image) {
        return new CastResult(
                new ListIota(this.body),
                continuation,
                image,
                List.<OperatorSideEffect>of(),
                ResolvedPatternType.EVALUATED,
                HexEvalSounds.THOTH);
    }

    /** 与 for 循环一致:循环自己被丢弃时,栈原样向上传。 */
    @Override
    public Pair<Boolean, List<Iota>> breakDownwards(List<? extends Iota> stack) {
        @SuppressWarnings("unchecked")
        List<Iota> mutable = (List<Iota>) stack;
        return new Pair<>(false, mutable);
    }

    @Override
    public int size() {
        return this.body.size() + this.pending.size() + 1;
    }

    @Override
    public ContinuationFrame.Type<?> getType() {
        return TYPE;
    }

    // =======================================================================
    // 序列化
    // =======================================================================

    @Override
    public CompoundTag serializeToNBT() {
        var out = new CompoundTag();
        out.put("body", serializeBlock(this.body));
        out.put("pending", serializeBlock(this.pending));
        out.put("current", IotaType.serialize(this.current));
        return out;
    }

    /** 把一个代码块序列化成 ListTag。 */
    static ListTag serializeBlock(TreeList<Iota> block) {
        var out = new ListTag();
        for (Iota iota : block) {
            out.add(IotaType.serialize(iota));
        }
        return out;
    }

    /** 从 ListTag 解回一个代码块。解不开的元素退化成 GarbageIota,不崩。 */
    static TreeList<Iota> deserializeBlock(@Nullable Tag tag, ServerLevel world) {
        if (tag == null) {
            return TreeList.empty();
        }
        var listTag = (ListTag) tag;
        List<Iota> out = new ArrayList<>(listTag.size());
        for (Tag sub : listTag) {
            var iota = IotaType.deserialize((CompoundTag) sub, world);
            out.add(iota == null ? new GarbageIota() : iota);
        }
        return TreeList.from(out);
    }

    /** 本帧的类型对象 —— 只要求实现"从 NBT 还原"。 */
    public static final ContinuationFrame.Type<FrameForEach> TYPE = new ContinuationFrame.Type<>() {
        @Override
        public @NotNull FrameForEach deserializeFromNBT(CompoundTag tag, ServerLevel world) {
            var currentTag = tag.get("current");
            Iota current = currentTag == null
                    ? new GarbageIota()
                    : IotaType.deserialize((CompoundTag) currentTag, world);
            return new FrameForEach(
                    deserializeBlock(tag.get("body"), world),
                    deserializeBlock(tag.get("pending"), world),
                    current == null ? new GarbageIota() : current);
        }
    };

}
