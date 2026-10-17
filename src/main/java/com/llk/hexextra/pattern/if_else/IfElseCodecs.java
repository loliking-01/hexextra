package com.llk.hexextra.pattern.if_else;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.utils.TreeList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * if/elseif/else 相关的全部编解码器,集中在一个<b>中立</b>的持有类里。
 *
 * <h2>⚠ 为什么不能把这些字段放在 {@link IfElseIota} 或 {@link IfElseIota.Branch} 里</h2>
 * 这是一个踩过的坑,值得写清楚 —— 它会让<b>整个服务端起不来</b>,而且报错信息很误导
 * ({@code NullPointerException: ... "IfElseIota$Branch.CODEC" is null})。
 *
 * <p>当时的循环是这样的:
 * <pre>
 *   IfElseIota.Branch 的静态初始化
 *     └─ 需要 IfElseIota.CODE_BLOCK_CODEC(外层类的静态字段)
 *          └─ 触发 IfElseIota 的类初始化
 *               └─ 初始化 IfElseIota.TYPE
 *                    └─ 又要读 IfElseIota.Branch.CODEC  ← 此刻它还没赋值,是 null → NPE
 * </pre>
 *
 * <p>要点在于「<b>嵌套类的初始化不会触发外部类的初始化</b>」—— 循环之所以形成,
 * 只是因为 {@code Branch} 的静态字段引用了<b>外层类</b>的静态字段。
 * 把这些编解码器挪到这个谁都不依赖的中立类,引用方向就变成单向的:
 * {@code IfElseIota.TYPE → IfElseCodecs → Branch},不再回环。
 *
 * <p>对续延帧 {@link FrameIfElse} 也一样:它需要分支与代码块的编解码器,
 * 直接从这里取,不必也不该去碰 {@code IfElseIota} 的静态成员。
 */
public final class IfElseCodecs {

    /**
     * 代码块的 NBT 编解码器:就是一个 iota 序列。
     *
     * <p>写进存档与网络时,条件/代码块两边<b>必须</b>用同一份定义,否则字节流会错位。
     */
    public static final Codec<TreeList<Iota>> CODE_BLOCK = TreeList.codecOf(IotaType.TYPED_CODEC);

    /** 代码块的网络编解码器。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, TreeList<Iota>> CODE_BLOCK_STREAM =
            IotaType.TYPED_STREAM_CODEC.apply(TreeList.streamCodecOp());

    /** 单个分支(条件 + 代码块)的 NBT 编解码器。 */
    public static final Codec<IfElseIota.Branch> BRANCH = RecordCodecBuilder.create(inst -> inst.group(
            CODE_BLOCK.fieldOf("condition").forGetter(IfElseIota.Branch::condition),
            CODE_BLOCK.fieldOf("body").forGetter(IfElseIota.Branch::body)
    ).apply(inst, IfElseIota.Branch::new));

    /** 单个分支的网络编解码器。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, IfElseIota.Branch> BRANCH_STREAM =
            StreamCodec.composite(
                    CODE_BLOCK_STREAM, IfElseIota.Branch::condition,
                    CODE_BLOCK_STREAM, IfElseIota.Branch::body,
                    IfElseIota.Branch::new);

    private IfElseCodecs() {
    }
}
