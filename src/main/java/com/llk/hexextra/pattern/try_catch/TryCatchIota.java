package com.llk.hexextra.pattern.try_catch;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.utils.TreeList;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.llk.hexextra.core.HexextraIotaTypes;
import com.llk.hexextra.pattern.if_else.IfElseCodecs;
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
 * 「try / catch / finally」 —— 三段(第三段可省)的可执行 iota。
 *
 * <h2>它做什么</h2>
 * 依次跑 try 块;若 try 块里抛了 {@code Mishap},不中断整个法术,而是把那段报错
 * 包成 {@link ExceptionIota} 交给 catch 块;无论走哪条路,最后都跑 finally(若给了)。
 *
 * <h2>它不做什么(重要)</h2>
 * <b>不回滚代价</b> —— try 块里已经花掉的媒质、已经改动的方块、已经造成的伤害都保留。
 * 它只防止"法术因为一次报错就整个停掉、玩家还不知道为什么"。
 *
 * <h2>怎么接住异常</h2>
 * 咒法学 VM 本身没有异常处理:报错会被 {@code CastingVM.executeInner} 折进
 * {@code CastResult} 的 {@code DoMishap} 副作用里。真正"接住"发生在
 * {@code MixinCastingVM} —— 它在 {@code executeInner} 出口检查续延上有没有本帧,
 * 有就改写成"进入 catch"。所以本类的 {@link #execute} 只负责压帧。
 */
public class TryCatchIota extends Iota {

    private final TreeList<Iota> tryBody;
    private final TreeList<Iota> catchBody;

    @Nullable
    private final TreeList<Iota> finallyBody;

    public TryCatchIota(TreeList<Iota> tryBody, TreeList<Iota> catchBody, @Nullable TreeList<Iota> finallyBody) {
        super(HexextraIotaTypes.TRY_CATCH::get);
        this.tryBody = tryBody;
        this.catchBody = catchBody;
        this.finallyBody = finallyBody;
    }

    public TreeList<Iota> getTryBody() {
        return this.tryBody;
    }

    public TreeList<Iota> getCatchBody() {
        return this.catchBody;
    }

    public Optional<TreeList<Iota>> getFinallyBody() {
        return Optional.ofNullable(this.finallyBody);
    }

    @Override
    public boolean isTruthy(@Nullable CastingEnvironment env) {
        return true;
    }

    @Override
    protected boolean toleratesOther(Iota that) {
        return typesMatch(this, that)
                && that instanceof TryCatchIota other
                && this.tryBody.equals(other.tryBody)
                && this.catchBody.equals(other.catchBody)
                && Objects.equals(this.finallyBody, other.finallyBody);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.tryBody, this.catchBody, this.finallyBody);
    }

    /** 悬停显示:{@code try {…} catch {…} finally {…}}。 */
    @Override
    public Component display() {
        MutableComponent out = Component.empty();
        out.append(Component.literal("try ").withStyle(ChatFormatting.GOLD));
        out.append(new ListIota(this.tryBody).display());
        out.append(Component.literal(" catch ").withStyle(ChatFormatting.GOLD));
        out.append(new ListIota(this.catchBody).display());
        if (this.finallyBody != null) {
            out.append(Component.literal(" finally ").withStyle(ChatFormatting.GOLD));
            out.append(new ListIota(this.finallyBody).display());
        }
        return out;
    }

    @Override
    public boolean executable() {
        return true;
    }

    /** 压出起始帧;真正的执行由 {@link FrameTryCatch} 分阶段完成。 */
    @Override
    public CastResult execute(CastingVM vm, ServerLevel world, SpellContinuation continuation) {
        SpellContinuation next = continuation.pushFrame(
                new FrameTryCatch(this.tryBody, this.catchBody, this.finallyBody, 0, null));

        return new CastResult(
                this,
                next,
                vm.getImage().withUsedOp(),
                List.<OperatorSideEffect>of(),
                ResolvedPatternType.EVALUATED,
                HexEvalSounds.HERMES.get());
    }

    @Override
    public int size() {
        int total = 1 + this.tryBody.size() + this.catchBody.size();
        if (this.finallyBody != null) {
            total += this.finallyBody.size();
        }
        return total;
    }

    @Override
    public int depth() {
        int maxChild = depthOf(this.tryBody);
        maxChild = Math.max(maxChild, depthOf(this.catchBody));
        if (this.finallyBody != null) {
            maxChild = Math.max(maxChild, depthOf(this.finallyBody));
        }
        return maxChild + 1;
    }

    /** 代码块的深度:块内所有 iota 里最深的那个(TreeList 本身没有 depth())。 */
    private static int depthOf(TreeList<Iota> block) {
        int max = 0;
        for (Iota iota : block) {
            max = Math.max(max, iota.depth());
        }
        return max;
    }

    @Override
    public @Nullable Iterable<Iota> subIotas() {
        List<Iota> out = new ArrayList<>();
        this.tryBody.forEach(out::add);
        this.catchBody.forEach(out::add);
        if (this.finallyBody != null) {
            this.finallyBody.forEach(out::add);
        }
        return out;
    }

    public static final IotaType<TryCatchIota> TYPE = new IotaType<>() {

        private final MapCodec<TryCatchIota> codec = RecordCodecBuilder.mapCodec(inst -> inst.group(
                IfElseCodecs.CODE_BLOCK.fieldOf("try").forGetter(TryCatchIota::getTryBody),
                IfElseCodecs.CODE_BLOCK.fieldOf("catch").forGetter(TryCatchIota::getCatchBody),
                IfElseCodecs.CODE_BLOCK.optionalFieldOf("finally").forGetter(TryCatchIota::getFinallyBody)
        ).apply(inst, (tryBody, catchBody, fin) ->
                new TryCatchIota(tryBody, catchBody, fin.orElse(null))));

        private final StreamCodec<RegistryFriendlyByteBuf, TryCatchIota> streamCodec = StreamCodec.composite(
                IfElseCodecs.CODE_BLOCK_STREAM, TryCatchIota::getTryBody,
                IfElseCodecs.CODE_BLOCK_STREAM, TryCatchIota::getCatchBody,
                ByteBufCodecs.optional(IfElseCodecs.CODE_BLOCK_STREAM), TryCatchIota::getFinallyBody,
                (tryBody, catchBody, fin) -> new TryCatchIota(tryBody, catchBody, fin.orElse(null)));

        @Override
        public MapCodec<TryCatchIota> codec() {
            return this.codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TryCatchIota> streamCodec() {
            return this.streamCodec;
        }

        /** 橙金色,和 if/switch 的金、case 的青区分开。 */
        @Override
        public int color() {
            return 0xff_cc8833;
        }
    };
}