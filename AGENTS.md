# hexextra 项目规则(1.20.1 双端工程)

本文件记录本项目的开发约定。它同时会被 AI 编码助手在工作区自动加载
(DSH 的 `dsh-agent-instructions`),所以写成了"给执行者看的指令"的口吻。

以下规则对本项目的一切改动生效,优先级高于一般习惯做法。

---

## 规则 1:先查清 API,再写代码

每次进行代码编辑前,**必须先查明所需 API 的真实签名**,确认无误后再输出代码。

禁止凭记忆、凭类比、或凭网上示例推断 Minecraft / Forge / Fabric / Hex Casting 的
类名、方法名、参数与返回值。

本项目可用的查证手段:

- `javap -cp <jar> <全限定类名>` —— 直接读字节码签名(JDK 自带,最可靠)
- `javap -v` 查注解与 `kotlin.Metadata`,判断某个类是 Java 还是 Kotlin 写的
- 直接解压依赖 jar 查看类清单、`mods.toml` / `fabric.mod.json`
- 读上游源码:Hex Casting 的 1.20.1 线对应标签 **`v0.11.4`**
  (本机已浅克隆在 `lootwork/upstream-v0.11.4/`,可直接读)

**特别警告**:两个大版本的 API 差异极大,**不能互相照搬**:

| | 1.20.1(`0.11.x`,本工程) | 1.21.1(`0.12.x`) |
|---|---|---|
| `Iota` 构造 | `Iota(IotaType<?>, Object payload)` | `Iota(Supplier<IotaType<?>>)` |
| 序列化 | 手写 `serialize(): Tag` | Codec / `StreamCodec` |
| `IotaType` | `deserialize(Tag, ServerLevel)` + `display(Tag)` + `color()` | `codec(): MapCodec` + `streamCodec()` + `color()` |
| 帧序列化 | `serializeToNBT(): CompoundTag` | Codec 驱动 |

本工程 `legacy-1.21-src/` 里放的是 1.21.1 的实现,**只作移植参考,不参与编译**;
照抄那里的 API 调用一定会编译失败。

---

## 规则 2:先问清设计意图,再动手实现

每次实现功能前,**必须先向用户询问清楚具体的设计意图**,得到明确答复后再实现。
需要问清楚的至少包括:

- 这个功能具体做什么、在什么条件下触发
- 边界情况与失败时的表现(是否抛 `Mishap`、报什么错)
- 与咒法学原有机制的交互(是否消耗媒质、是否受施法距离限制、是否需要 enlightenment)
- 是否影响存档兼容、是否需要客户端与服务端两侧配合

**不要自行替用户决定玩法设计。**

---

## 规则 3:用 Java 完整替代 Kotlin 能力,并写中文注释

- 咒法学本体是 Kotlin 写的。本项目要求**全部使用 Java 实现**,
  并且必须**完整、正确地替代 Kotlin 所实现的能力**,不能因为语言差异而丢功能或走样。
- 每个功能都要写**中文注释**,说明它**实际起什么作用**,
  而不是把代码逐字翻译成中文。
- 遇到 Kotlin 专有构造时,用 Java 等价写法落地,并在注释里点明对应关系:
  - 伴生对象(`Companion`)→ Java 的 `static` 字段/方法
  - 扩展函数 → Java 的静态工具方法
  - 默认参数 → Java 的重载方法
  - 协变 `List<Iota>` → Java 的 `List<? extends Iota>`
  - `data class` / `object` → Java 的 `record` / 单例
  - Kotlin 接口的 `$DefaultImpls` → Java 直接依赖 `default` 方法

---

## 规则 4:思考过程使用中文

内部推理、分析、计划、权衡一律使用中文。对外输出同样使用中文。

---

## 规则 5:每个功能单独一个文件夹(仅代码工程)

**适用对象 —— 有独立内容的玩法单元**:一个图案、一个物品(以及它所配套的功能)
各占一个**独立文件夹**,方便分类处理。

**不适用对象 —— 基础设施 / 框架类**:自定义 Iota 类型、注册表集中管理、通用工具、
配置、报错类型等**不做细分**,统一放在共享的基础包里。

**资源文件的限制**:Java 包可以自由按功能分文件夹,但 Minecraft 的资源必须遵循固定结构,
**不能跟着功能文件夹走**:

- `common/src/main/resources/assets/hexextra/lang/en_us.json` —— 语言文件**必须平铺**
- 贴图可以有子目录,但入口仍从 `assets/hexextra/` 起算
- 配方、进度、模型同理,都在 `data/hexextra/`、`assets/hexextra/` 的固定骨架下

**本工程的模块划分**(与功能文件夹是两回事):

```
common/     只依赖原版 Minecraft 的公共代码 —— 保证天然双端
forge/      Forge 端(ForgeGradle 6,含 reobfJar)
fabric/     Fabric 端(fabric-loom,含 remapJar)
```

`common` 的源码通过 `compileJava { source(project(':common').sourceSets.main.allSource) }`
直接编进两个 loader 模块 —— 与上游咒法学自己的做法一致,不引入 Architectury 插件。
**功能代码一律写在 `common` 里**;`forge` / `fabric` 只放加载器入口与注册胶水。

`common` 内部的目录约定(以 `com.llk.hexextra` 为根):

```
HexextraCommon.java        公共入口
core/                      基础设施,不按功能细分
  HexextraRegistries.java    注册表集中管理
  HexextraIotaTypes.java     自定义 Iota 类型
  HexextraMishaps.java       自定义报错
pattern/                   图案类功能
  <图案名>/                  一个图案一个文件夹
```

---

## 项目速览

| 项 | 值 |
|---|---|
| 技术栈 | MC 1.20.1 + Forge 47.1.47 + Fabric loader 0.19.2 + Gradle 8.14 + **JDK 17** |
| mod id | `hexextra`,包名 `com.llk.hexextra` |
| 主前置 | Hex Casting `0.11.4`(**已公开发布**,走常规 Maven) |
| 构建 | `./gradlew build` / `./gradlew compileJava`(必须用 JDK 17,不能用 21+) |
| 参考实现 | `legacy-1.21-src/`(1.21.1 版源码,不参与编译,仅供移植对照) |

### 版本号一律来自上游,不要凭感觉改

`gradle.properties` 里的 `minecraftVersion` / `forgeVersion` / `fabricLoaderVersion` /
`fabricVersion` / `paucalVersion` / `patchouliVersion` 全部取自上游 Hex Casting
`v0.11.4` 自己的构建配置,是一套确认可用的组合。

### 待移植的功能(与 1.21.1 版设计一致,只是要按 0.11.4 的 API 重写)

| 功能 | 图案 | 1.21.1 的入口类(参考) |
|---|---|---|
| for 循环 | `aeqqqqqawwd` | `pattern/for_loop/ForLoopFeature` |
| if / elseif / else 条件链 | `aedaqda`、`aedaqdaaw` | `pattern/if_else/IfElseFeature` |
| switch 多路等值分派 | `dawwaqw` | `pattern/switch_case/SwitchFeature` |

三者都配了算术重载(`add` / `index` / `abs` / `remove_from` / `replace`)。
**算术层几乎可以照搬**(`Arithmetic` / `IotaMultiPredicate` / `OperatorBasic` 三个版本的签名一致),
iota 层与帧的序列化必须重写。

### 本地开发环境的注意事项(非仓库内容)

- 若 Gradle 需要代理,写在用户目录的 `~/.gradle/gradle.properties` 里(不要提交进仓库)。
- 某些环境下 `curl` / `Invoke-WebRequest` 的 HTTPS 可能因系统 TLS 后端损坏而失败,
  这**不影响 Gradle / Java / git**(它们走各自的 TLS 实现)。
- 域名解析到 `28.0.0.x` 这类地址通常是代理的 fake-IP 模式,属正常现象。
- 若遇到 `maven.minecraftforge.net` 报 `Remote host terminated the handshake`,
  多半是代理瞬时抖动,**重试即可**(先确认用 Java 能直连该地址)。

### 本地副产物目录

辅助内容放在工程之外,不纳入版本控制:

- `lootwork/memos/` —— 依赖方案、API 速查、移植评估与实现备忘
- `lootwork/tools/` —— 图案校验、书条目校验、语言键校验等脚本
- `lootwork/upstream-v0.11.4/` —— 上游 0.11.4 源码的浅克隆,查 API 用
