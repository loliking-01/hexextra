# mixin-pending

这里放的是**尚未启用**的 Mixin 源码,它**不参与任何模块的编译**(不在 `src/main/java` 下)。

## 为什么没启用

这两个 Mixin 的目标是**咒法学自己的类**:

- `at.petrak.hexcasting.common.casting.actions.lists.OpSplat`
- `at.petrak.hexcasting.common.casting.actions.lists.OpLastNToList`

混入"别的 mod 的类"时,Fabric 的 loom 与 Forge 的 mixingradle 都会在注解处理器阶段报:

```
Unable to locate obfuscation mapping for @Inject target execute / operate
```

已尝试无效:完整描述符写法、`mixin.useLegacyMixinAp = false`(Fabric 过了但 Forge 不过)。

## 现在的替代实现

改走**算术重载**:给 `SPLAT` / `LAST_N_LIST` 这两枚图案注册自己的 `Arithmetic` 算子
(`IfElseExtraArithmetic`),按参数类型分流。不需要 Mixin,也不需要混淆映射。

## 如果以后想启用 Mixin

1. 把两个 `.java` 移回 `common/src/main/java/com/llk/hexextra/mixin/`
2. 在 `common/src/main/resources/` 建 `hexextra.mixins.json`(见提交 `91a6691` 里的那份)
3. `fabric.mod.json` 加 `"mixins": [ "hexextra.mixins.json" ]`
4. `forge/build.gradle` 加 mixingradle 的 `mixin { add sourceSets.main, "hexextra.mixins.refmap.json"; config "hexextra.mixins.json" }`
5. **先解决上面那个映射问题**,否则构建必红