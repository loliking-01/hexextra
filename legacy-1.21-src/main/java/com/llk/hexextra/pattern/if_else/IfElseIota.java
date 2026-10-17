package com.llk.hexextra.pattern.if_else;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.FrameEvaluate;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.HexextraIotaTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 「if / elseif / else」条件链 iota —— 仿照 Java 的 if-else 链,但可以像数据一样在栈上传递、
 * 拆解、重新组装。
 *
 * <h2>结构</h2>
 * <ul>
 *   <li>{@link #branches} —— 至少 0 个分支。第 0 个是 {@code if},其后依次是 {@code elseif}。
 *       每个分支由「条件代码块」和「代码块」两部分组成。</li>
 *   <li>{@link #elseBody} —— 可选的最终 {@code else} 代码块,不做条件判断直接执行。</li>
 * </ul>
 * 允许 {@code branches} 为空(此时若 {@code elseBody} 非空,就是「仅 else 链」),
 * 这样「单参动作」与「remove_from 删掉最后一个分支」都不会产生非法状态。
 *
 * <h2>为什么分支在栈上用「单分支链」表示,而不是二元列表</h2>
 * 因为算术引擎的算子谓词<b>只能依据 IotaType 判断</b>(引擎会按类型元组缓存解析结果)。
 * 若用「长度为 2 的列表」表示分支,就没法把它和玩家普通的两元素列表区分开,
 * 谓词必须依赖值、从而破坏引擎缓存。统一用本类型表示分支,判定就退化成
 * {@code ofType(IF_ELSE)},既精确又和引擎契合。
 *
 * <h2>执行</h2>
 * 本 iota <b>必须被执行才有效果</b>({@link #executable()} 返回 true)。
 * 执行时依次求值各分支的条件代码块,第一个产出 {@code true} 的分支执行其代码块;都不满足则执行 else。
 * 详见 {@link #execute}。
 */
public class IfElseIota extends Iota {

    /**
     * 一个分支:条件代码块 + 代码块。
     *
     * @param condition 判断用的代码块,执行后必须<b>恰好</b>在栈上留下 1 个布尔值
     * @param body      条件为真时执行的代码块
     */
    public record Branch(TreeList<Iota> condition, TreeList<Iota> body) {
        // 编解码器刻意不放在这里,而是集中到 IfElseCodecs。
        // 原因见 IfElseCodecs 的类注释:放在这里会形成循环静态初始化,导致服务端起不来。
    }

    private final List<Branch> branches;
    @Nullable
    private final TreeList<Iota> elseBody;

    /**
     * @param branches 分支列表,可为空;元素顺序即 if → elseif → ...
     * @param elseBody 最终 else 代码块,可为 null 表示没有 else
     */
    public IfElseIota(List<Branch> branches, @Nullable TreeList<Iota> elseBody) {
        // 送进来的 IotaType Supplier 必须是本模组注册的那个,否则游戏无法按类型分派到我们的编解码器
        super(HexextraIotaTypes.IF_ELSE::get);
        this.branches = List.copyOf(branches);
        this.elseBody = elseBody;
    }

    /** 分支列表(不可变)。 */
    public List<Branch> getBranches() {
        return this.branches;
    }

    /** 最终 else 代码块,可为 null。 */
    @Nullable
    public TreeList<Iota> getElseBody() {
        return this.elseBody;
    }

    /** 供编解码器使用的 else 包装形式(null → Optional.empty)。 */
    public Optional<TreeList<Iota>> getElseBodyOptional() {
        return Optional.ofNullable(this.elseBody);
    }

    /**
     * 真假:只要"有东西可做"就算真 —— 即存在分支或有 else 块。
     * (对照 {@code ListIota}:非空为真。)
     */
    @Override
    public boolean isTruthy(@Nullable CastingEnvironment env) {
        return !this.branches.isEmpty() || this.elseBody != null;
    }

    /**
     * 容差相等:类型相同且结构逐层相等。
     *
     * <p>{@code TreeList.equals} 走的是 {@code AbstractList} 的逐元素比较,
     * 而元素的 {@code equals} 又是 {@code Iota} 提供的容差比较,所以这里天然是"模糊相等",
     * 与咒法学其它容器型 iota(如 {@code ListIota})保持一致。
     */
    @Override
    protected boolean toleratesOther(Iota that) {
        return typesMatch(this, that)
                && that instanceof IfElseIota other
                && this.branches.equals(other.branches)
                && Objects.equals(this.elseBody, other.elseBody);
    }

    /**
     * 与 {@link #toleratesOther} 保持一致的散列。
     *
     * <p>注意:元素级的容差比较意味着"近似相等的双精度数"会被判相等,但散列可能不同。
     * 这是继承自咒法学 {@code Iota.equals} 语义的既有特性({@code ListIota} 同样如此),
     * 本项目不单独处理。
     */
    @Override
    public int hashCode() {
        return Objects.hash(this.branches, this.elseBody);
    }

    /**
     * 悬停/聊天栏里的多行显示。
     *
     * <p>格式(每行一条):
     * <pre>
     *   if(条件){代码块}
     *   elseif(条件){代码块}
     *   else{代码块}
     * </pre>
     *
     * <p>换行用 {@code "\n"} 嵌入 Component —— 原版 tooltip 走
     * {@code Font.split → StringSplitter.splitLines},其中会识别 {@code '\n'} 字符,
     * 所以这样就能真的显示成多行。
     *
     * <p>代码块的渲染<b>直接复用原版列表 iota 的渲染</b>:把 {@link TreeList} 包成
     * {@link ListIota} 调它的 {@code display()},于是自动得到原版那套
     * {@code [a, b, c]} 紫色文案,不用自己拼。
     */
    @Override
    public Component display() {
        MutableComponent out = Component.empty();

        for (int i = 0; i < this.branches.size(); i++) {
            if (i > 0) {
                out.append(Component.literal("\n"));
            }
            Branch branch = this.branches.get(i);
            out.append(Component.literal(i == 0 ? "if(" : "elseif(").withStyle(ChatFormatting.GOLD));
            out.append(renderCodeBlock(branch.condition()));
            out.append(Component.literal(")").withStyle(ChatFormatting.GOLD));
            out.append(Component.literal("{").withStyle(ChatFormatting.GOLD));
            out.append(renderCodeBlock(branch.body()));
            out.append(Component.literal("}").withStyle(ChatFormatting.GOLD));
        }

        if (this.elseBody != null) {
            if (!this.branches.isEmpty()) {
                out.append(Component.literal("\n"));
            }
            out.append(Component.literal("else").withStyle(ChatFormatting.GOLD));
            out.append(Component.literal("{").withStyle(ChatFormatting.GOLD));
            out.append(renderCodeBlock(this.elseBody));
            out.append(Component.literal("}").withStyle(ChatFormatting.GOLD));
        }

        return out;
    }

    /** 照搬原版列表 iota 的渲染方式。 */
    private static Component renderCodeBlock(TreeList<Iota> block) {
        return new ListIota(block).display();
    }

    /**
     * 声明本 iota 可以被执行。
     *
     * <p>这个方法的<b>唯一</b>调用点是 {@code OperatorUtils.getEvaluatable} ——
     * 也就是「赫尔墨斯之书」「考虑再会」以及我们自己的 for 循环在判断
     * "能不能把栈上这个东西当代码跑"时用的地方。返回 false 的话,
     * 玩家把 if 链交给那些图案会被判为「不可求值」而报错。
     */
    @Override
    public boolean executable() {
        return true;
    }

    /**
     * 执行这条条件链:依次求值各分支的条件,第一个产出 {@code true} 的分支执行其代码块;
     * 都不满足则执行 else。
     *
     * <p>本方法只负责「把第一阶段挂上续延」,真正的判断与跳转在 {@link FrameIfElse} 里完成 ——
     * 因为求值一个条件要跨两次 VM 步(先跑条件、再看结果),没法在一步之内做完。
     */
    @Override
    public CastResult execute(CastingVM vm, ServerLevel world, SpellContinuation continuation) {
        IfElseFeature.trace("链被执行:分支数={},有 else={},当前栈深={}",
                this.branches.size(), this.elseBody != null, vm.getImage().getStack().size());

        // 注意:分支可能为空(「仅 else 链」,或 remove_from 删光了分支)。
        // 此时不能去取 branches.get(0),必须单独处理 —— 否则会下标越界。
        if (this.branches.isEmpty()) {
            SpellContinuation next = continuation;
            if (this.elseBody != null) {
                // 没有条件可判,直接跑 else
                next = next.pushFrame(new FrameEvaluate(this.elseBody, true));
            }
            // 若连 else 都没有,这条链就是个空操作,什么都不压
            return new CastResult(
                    this,
                    next,
                    vm.getImage().withUsedOp(),
                    List.<OperatorSideEffect>of(),
                    ResolvedPatternType.EVALUATED,
                    HexEvalSounds.HERMES.get());
        }

        SpellContinuation next = continuation.pushFrame(
                new FrameIfElse(this.branches, 0, false, this.elseBody, -1));

        return new CastResult(
                this,
                next,
                vm.getImage().withUsedOp(),
                List.<OperatorSideEffect>of(),
                ResolvedPatternType.EVALUATED,
                HexEvalSounds.HERMES.get());
    }

    /**
     * 内存占用:与 {@code ListIota} 同一口径 —— 自身 1,再加上全部代码块里所有 iota 的 size。
     * 咒法学用它判断续延/栈是否还允许序列化(上限 {@code MAX_SERIALIZATION_TOTAL} = 1024)。
     */
    @Override
    public int size() {
        int total = 1;
        for (Branch branch : this.branches) {
            total += sizeOf(branch.condition());
            total += sizeOf(branch.body());
        }
        if (this.elseBody != null) {
            total += sizeOf(this.elseBody);
        }
        return total;
    }

    private static int sizeOf(TreeList<Iota> block) {
        int total = 1;
        for (Iota iota : block) {
            total += iota.size();
        }
        return total;
    }

    /** 嵌套深度,同样用于序列化保护(上限 {@code MAX_SERIALIZATION_DEPTH} = 256)。 */
    @Override
    public int depth() {
        int maxChild = 0;
        for (Branch branch : this.branches) {
            maxChild = Math.max(maxChild, depthOf(branch.condition()));
            maxChild = Math.max(maxChild, depthOf(branch.body()));
        }
        if (this.elseBody != null) {
            maxChild = Math.max(maxChild, depthOf(this.elseBody));
        }
        return maxChild + 1;
    }

    private static int depthOf(TreeList<Iota> block) {
        int maxChild = 0;
        for (Iota iota : block) {
            maxChild = Math.max(maxChild, iota.depth());
        }
        return maxChild + 1;
    }

    /**
     * 直接子 iota —— 把各代码块里的元素摊平列出。
     *
     * <p>用途只有一个:咒法学的 {@code MishapOthersName} 会递归扫描 iota 树来查找"别人的真名",
     * 我们需要把自己的内容暴露给它,否则藏在链里的真名会漏检。
     */
    @Override
    public @Nullable Iterable<Iota> subIotas() {
        List<Iota> out = new ArrayList<>();
        for (Branch branch : this.branches) {
            branch.condition().forEach(out::add);
            branch.body().forEach(out::add);
        }
        if (this.elseBody != null) {
            this.elseBody.forEach(out::add);
        }
        return out;
    }

    /** 本 iota 的类型对象:必须实现 codec / streamCodec / color 三项。 */
    public static final IotaType<IfElseIota> TYPE = new IotaType<>() {

        /** NBT 侧:分支列表 + 可选 else。 */
        private final MapCodec<IfElseIota> codec = RecordCodecBuilder.mapCodec(inst -> inst.group(
                IfElseCodecs.BRANCH.listOf().fieldOf("branches").forGetter(IfElseIota::getBranches),
                IfElseCodecs.CODE_BLOCK.optionalFieldOf("else").forGetter(IfElseIota::getElseBodyOptional)
        ).apply(inst, (branches, els) -> new IfElseIota(branches, els.orElse(null))));

        /** 网络侧:必须与 NBT 侧字段一一对应,否则字节流会错位。 */
        private final StreamCodec<RegistryFriendlyByteBuf, IfElseIota> streamCodec = StreamCodec.composite(
                IfElseCodecs.BRANCH_STREAM.apply(ByteBufCodecs.list()), IfElseIota::getBranches,
                ByteBufCodecs.optional(IfElseCodecs.CODE_BLOCK_STREAM), IfElseIota::getElseBodyOptional,
                (branches, els) -> new IfElseIota(branches, els.orElse(null)));

        @Override
        public MapCodec<IfElseIota> codec() {
            return this.codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, IfElseIota> streamCodec() {
            return this.streamCodec;
        }

        /** 物品光效/图标颜色,取一个与"条件分支"相称的青色。 */
        @Override
        public int color() {
            return 0xff_33cccc;
        }
    };
}
