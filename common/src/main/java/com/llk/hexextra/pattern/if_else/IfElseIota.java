package com.llk.hexextra.pattern.if_else;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.utils.HexUtils;
import at.petrak.hexcasting.api.utils.TreeList;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * if / elseif / else 的<b>条件链</b>。
 *
 * <h2>它只是数据,执行要另画一个「赫尔墨斯之策略」</h2>
 * 造链的图案只负责把链放到栈上 —— 这样链可以被加法之馏化接长、被算术拆装,
 * 也能当普通值传递。要真的求值,得在链上画「赫尔墨斯之策略」。
 *
 * <h2>分支用「单分支链」表示</h2>
 * 一条分支就是「条件代码块 + 代码块」的配对;而一条<b>单分支</b>的链本身也是一条合法分支 ——
 * 这样 if_chain 图案造出来的东西既可以直接执行(只有一条分支的 if),
 * 也可以被加法之馏化接成多分支。这个设计与 1.21.1 版完全一致。
 *
 * <h2>0.11.4 的差异</h2>
 * 没有 Codec:序列化是手写 NBT({@link #serialize()}),显示写在
 * {@link #TYPE} 的 {@code display(Tag)} 里。分支记录可以直接嵌在类里 ——
 * 1.21.1 上为了绕开循环静态初始化才不得不拆出 IfElseCodecs,这里不需要。
 */
public class IfElseIota extends Iota {

    /** 一条分支:条件代码块 + 命中时执行的代码块。 */
    public record Branch(TreeList<Iota> condition, TreeList<Iota> body) {
    }

    /** payload:分支列表 + 可空的 else。 */
    public record IfElseData(List<Branch> branches, @Nullable TreeList<Iota> elseBody) {
    }

    public IfElseIota(List<Branch> branches, @Nullable TreeList<Iota> elseBody) {
        super(TYPE, new IfElseData(branches, elseBody));
    }

    private IfElseData data() {
        return (IfElseData) this.payload;
    }

    public List<Branch> getBranches() {
        return this.data().branches();
    }

    public @Nullable TreeList<Iota> getElseBody() {
        return this.data().elseBody();
    }

    /** 分支条数(不含 else)。 */
    public int branchCount() {
        return this.getBranches().size();
    }

    @Override
    public boolean isTruthy() {
        return true;
    }

    @Override
    protected boolean toleratesOther(Iota that) {
        return typesMatch(this, that)
                && that instanceof IfElseIota other
                && this.getBranches().equals(other.getBranches())
                && Objects.equals(this.getElseBody(), other.getElseBody());
    }

    /**
     * 手写 NBT 序列化。
     *
     * <p>格式:{@code { branches: [ {condition: ListTag, body: ListTag}, ... ], else?: ListTag }}。
     */
    @Override
    public @NotNull Tag serialize() {
        var out = new CompoundTag();

        var branchesTag = new ListTag();
        for (Branch branch : this.getBranches()) {
            var one = new CompoundTag();
            one.put("condition", serializeBlock(branch.condition()));
            one.put("body", serializeBlock(branch.body()));
            branchesTag.add(one);
        }
        out.put("branches", branchesTag);

        TreeList<Iota> elseBody = this.getElseBody();
        if (elseBody != null) {
            out.put("else", serializeBlock(elseBody));
        }
        return out;
    }

    @Override
    public @Nullable Iterable<Iota> subIotas() {
        List<Iota> out = new ArrayList<>();
        for (Branch branch : this.getBranches()) {
            branch.condition().forEach(out::add);
            branch.body().forEach(out::add);
        }
        TreeList<Iota> elseBody = this.getElseBody();
        if (elseBody != null) {
            elseBody.forEach(out::add);
        }
        return out;
    }

    @Override
    public int size() {
        int total = 2;
        for (Branch branch : this.getBranches()) {
            for (Iota iota : branch.condition()) total += iota.size();
            for (Iota iota : branch.body()) total += iota.size();
        }
        TreeList<Iota> elseBody = this.getElseBody();
        if (elseBody != null) {
            for (Iota iota : elseBody) total += iota.size();
        }
        return total;
    }

    @Override
    public int depth() {
        int maxChild = 1;
        for (Branch branch : this.getBranches()) {
            for (Iota iota : branch.condition()) maxChild = Math.max(maxChild, iota.depth());
            for (Iota iota : branch.body()) maxChild = Math.max(maxChild, iota.depth());
        }
        TreeList<Iota> elseBody = this.getElseBody();
        if (elseBody != null) {
            for (Iota iota : elseBody) maxChild = Math.max(maxChild, iota.depth());
        }
        return maxChild + 1;
    }

    /** 链是可执行的 —— 执行时从第一条分支开始逐个求值条件。 */
    @Override
    public boolean executable() {
        return true;
    }

    /**
     * 执行:压入一条 {@link FrameIfElse} 开始求值。
     *
     * <p>本 iota 自己不做任何判断 —— 真正的两阶段逻辑(先求值条件、再检查结果)
     * 全在帧里,因为那需要跨多个 VM 步。
     */
    @Override
    public @NotNull CastResult execute(CastingVM vm, ServerLevel world, SpellContinuation continuation) {
        SpellContinuation next = continuation.pushFrame(
                new FrameIfElse(this.getBranches(), this.getElseBody(), 0, false, 0));

        return new CastResult(
                this,
                next,
                vm.getImage().withUsedOp(),
                List.of(),
                at.petrak.hexcasting.api.casting.eval.ResolvedPatternType.EVALUATED,
                at.petrak.hexcasting.common.lib.hex.HexEvalSounds.HERMES);
    }

    // =======================================================================
    // 工具
    // =======================================================================

    public static ListTag serializeBlock(TreeList<Iota> block) {
        var out = new ListTag();
        for (Iota iota : block) {
            out.add(IotaType.serialize(iota));
        }
        return out;
    }

    public static TreeList<Iota> deserializeBlock(@Nullable Tag tag, ServerLevel world) {
        if (tag == null) {
            return TreeList.empty();
        }
        var listTag = HexUtils.downcast(tag, ListTag.TYPE);
        List<Iota> out = new ArrayList<>(listTag.size());
        for (Tag sub : listTag) {
            var csub = HexUtils.downcast(sub, CompoundTag.TYPE);
            var iota = IotaType.deserialize(csub, world);
            out.add(iota == null ? new at.petrak.hexcasting.api.casting.iota.GarbageIota() : iota);
        }
        return TreeList.from(out);
    }

    /** 本 iota 的类型对象。 */
    public static final IotaType<IfElseIota> TYPE = new IotaType<>() {

        @Override
        public @Nullable IfElseIota deserialize(Tag tag, ServerLevel world) throws IllegalArgumentException {
            var ctag = HexUtils.downcast(tag, CompoundTag.TYPE);

            var branchesTag = HexUtils.downcast(ctag.get("branches"), ListTag.TYPE);
            var branches = new ArrayList<Branch>(branchesTag.size());
            for (Tag sub : branchesTag) {
                var csub = HexUtils.downcast(sub, CompoundTag.TYPE);
                branches.add(new Branch(
                        deserializeBlock(csub.get("condition"), world),
                        deserializeBlock(csub.get("body"), world)));
            }

            TreeList<Iota> elseBody = null;
            if (ctag.contains("else", Tag.TAG_LIST)) {
                elseBody = deserializeBlock(ctag.get("else"), world);
            }

            return new IfElseIota(branches, elseBody);
        }

        @Override
        public Component display(Tag tag) {
            var ctag = HexUtils.downcast(tag, CompoundTag.TYPE);
            MutableComponent out = Component.empty();

            var branchesTag = HexUtils.downcast(ctag.get("branches"), ListTag.TYPE);
            for (int i = 0; i < branchesTag.size(); i++) {
                var csub = HexUtils.downcast(branchesTag.get(i), CompoundTag.TYPE);
                out.append(Component.literal(i == 0 ? "if(" : "\nelseif(")
                        .withStyle(ChatFormatting.GOLD));
                out.append(renderBlock(csub.get("condition")));
                out.append(Component.literal("){").withStyle(ChatFormatting.GOLD));
                out.append(renderBlock(csub.get("body")));
                    out.append(Component.literal("}").withStyle(ChatFormatting.GOLD));
            }

            if (ctag.contains("else", Tag.TAG_LIST)) {
                out.append(Component.literal("\nelse {").withStyle(ChatFormatting.GOLD));
                out.append(renderBlock(ctag.get("else")));
                    out.append(Component.literal("}").withStyle(ChatFormatting.GOLD));
            }
            return out;
        }

        private MutableComponent renderBlock(@Nullable Tag tag) {
            var listTag = HexUtils.downcast(tag, ListTag.TYPE);
            MutableComponent body = Component.empty();
            for (int i = 0; i < listTag.size(); i++) {
                var csub = HexUtils.downcast(listTag.get(i), CompoundTag.TYPE);
                body.append(IotaType.getDisplay(csub));
                if (i < listTag.size() - 1) {
                    body.append(", ");
                }
            }
            return Component.translatable("hexcasting.tooltip.list_contents", body)
                    .withStyle(ChatFormatting.DARK_PURPLE);
        }

        /** 与 switch 的蓝色区分开的紫色。 */
        @Override
        public int color() {
            return 0xff_9b59b6;
        }
    };
}
