# hexextra(1.20.1)

[咒法学](https://github.com/gamma-delta/Hex-Mod)(Hex Casting)附属 —— Minecraft **1.20.1** 的
**Forge + Fabric 双端**版本。

给咒术加上几枚**像编程语言那样**的图案:循环、条件分支、多路分派。
全部使用 **Java** 实现(咒法学本体是 Kotlin 写的),配完整中文注释。

> **当前状态:骨架阶段**
> 三模块工程与依赖已跑通、`./gradlew build` 通过,但**三个功能尚未移植**。
> 1.20.1 对应的咒法学是 `0.11.x`,其 iota 层与 1.21 的 `0.12.x` 是**两套不同架构**
> (手写 `Tag` 序列化 vs Codec 驱动),移植时相关类需重写。

## 工程结构

```
common/     只依赖原版 Minecraft 的公共代码 —— 保证天然双端
forge/      Forge 端(ForgeGradle 6,含 reobfJar)
fabric/     Fabric 端(fabric-loom,含 remapJar)
```

`common` 的源码通过 `compileJava { source(project(':common').sourceSets.main.allSource) }`
直接编进两个 loader 模块 —— 与上游咒法学自己的做法一致,不引入 Architectury 等额外插件。

`legacy-1.21-src/` 是 1.21.1 版的实现,作为移植时的对照参考,**不参与编译**。

## 目标版本

| 项 | 版本 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.1.47 |
| Fabric Loader | 0.19.2 |
| Fabric API | 0.92.8+1.20.1 |
| 咒法学(Hex Casting) | 0.11.4 |
| Gradle | 8.14 |
| JDK | **17** |

> 全部版本号都取自上游 Hex Casting `v0.11.4` 自己的构建配置,是一套确认可用的组合。

## 构建

```bash
./gradlew build
```

需要 **JDK 17**(1.20.1 不能用 21+)。产物在 `build/libs/`:

- `common/build/libs/common-<版本>.jar`
- `forge/build/libs/hexextra-forge-<版本>.jar`
- `fabric/build/libs/hexextra-fabric-<版本>.jar`

## 计划移植的功能

三个功能与 1.21.1 版**设计完全一致**(语义、图案角度、算术重载都相同),只是要按 0.11.4 的 API 重写:

| 功能 | 图案 | 说明 |
|---|---|---|
| 佛尔之策略(for 循环) | `aeqqqqqawwd` | 取「次数 + 代码块」,重复执行 |
| 伊芙艾儿丝之策略(if/elseif/else) | `aedaqda`、`aedaqdaaw` | 条件链,条件块须恰好留一个布尔 |
| 斯维奇之策略(switch) | `dawwaqw` | 消费栈顶值查表,O(1) 派发,不贯穿 |

三者都配有算术重载(`add` / `index` / `abs` / `remove_from` / `replace`)。

## 开发约定

见 [AGENTS.md](AGENTS.md)。要点:

- **先查清 API 真实签名再写代码** —— 不凭记忆推断(两个版本的 API 差异很大,不能照搬)
- **全部 Java + 中文注释**,注释说明「实际起什么作用」
- **一个功能一个文件夹**,基础设施放 `core/`

## 许可

[MIT](LICENSE)
