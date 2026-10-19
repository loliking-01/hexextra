# hexextra 项目规则

本文件记录本项目的开发约定。它同时会被 AI 编码助手在工作区自动加载
(DSH 的 `dsh-agent-instructions`),所以写成了"给执行者看的指令"的口吻。

以下规则对本项目的一切改动生效,优先级高于一般习惯做法。

---

## 规则 1:先查清 API,再写代码

每次进行代码编辑前,**必须先查明所需 API 的真实签名**,确认无误后再输出代码。

禁止凭记忆、凭类比、或凭网上示例推断 Minecraft / NeoForge / Hex Casting 的
类名、方法名、参数与返回值。

本项目可用的查证手段:

- `javap -cp <jar> <全限定类名>` —— 直接读字节码签名(JDK 21 自带,最可靠)
- `javap -v` 查注解与 `kotlin.Metadata`,判断某个类是 Java 还是 Kotlin 写的
- 直接解压依赖 jar 查看类清单、`neoforge.mods.toml`
- 读源码 jar(咒法学 CI 提供 `-sources.jar`)

**特别警告**:网上流传的咒法学附属开发指南(如 Hexal 的 `addon_guide.md`)是
1.18/1.19 时代的,里面的 `api.spell.*` 包名、`ConstManaOperator`、`SpellDatum`、
`CastingContext`、`HexPattern.fromAngles`、`fg.deobf` 在 1.21 上**全部已变更或不存在**。

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

- 咒法学本体是 Kotlin 写的。本项目**以 Java 为主**:
  玩法逻辑(图案、iota、算术、帧、报错、注册)一律用 Java 写,
- **Kotlin 仅用于"工具扩展"**:即给 Java 侧补一些 Java 写起来啰嗦的东西,
  典型是给 `CastingImage` 这类 Kotlin data class 加扩展函数(Java 侧 `image.copy(...)`
  要按位置传 7 个参数,Kotlin 侧可 `copy(stack = stack)` 只改一个 —— 少写位置参数就少一处出错)。
  这类代码放 `com.llk.hexextra.helper` 包下,**不要在 Kotlin 里写玩法逻辑**。
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

- `src/main/resources/assets/hexextra/lang/en_us.json` —— 语言文件**必须平铺**,
  不能拆成 `lang/<功能名>/en_us.json`
- 贴图可以有子目录(`textures/item/<功能名>/xxx.png`),但入口仍从 `assets/hexextra/` 起算
- 配方、进度、模型同理,都在 `data/hexextra/`、`assets/hexextra/` 的固定骨架下

**目录约定**(以 `com.llk.hexextra` 为根):

```
Hexextra.java              主类,只做装配
Config.java
core/                      基础设施,不按功能细分
  HexextraRegistries.java    所有 DeferredRegister 集中在此
  HexextraIotaTypes.java     自定义 Iota 类型
  HexextraMishaps.java       自定义报错
pattern/                   图案类功能
  <图案名>/                  一个图案一个文件夹
item/                      物品类功能
  <物品名>/                  一个物品一个文件夹
```

---

## 规则 6:新增内容以 1.21.1 为主,完成后再移植

用户提出的**新增内容**(新功能、新图案、新机制、新资源文件等),一律按这个顺序:

1. **先在 1.21.1 工程**(`D:\mcmod\hexextra`)实现并完成 —— 包括验证、构建、手册、语言
2. **完成后再移植**到 1.20.1 双端工程(`D:\mcmod\hexextra1.20.1`)

**理由**:1.21.1 是主版本,API 与咒法学上游一致、只用管一个 loader,迭代成本低;
1.20.1 要同时适配 Forge + Fabric 与 0.11.4 的旧 API,每改一次都得处理两份差异。
先在主版本把设计敲定,移植时只做 API 适配,不会两边同时返工。

**例外 —— 纯 1.20.1 专属的问题直接在该工程处理**,不需要先在 1.21.1 做一遍。
例如:Forge 的资源包/依赖问题、Fabric 的运行时依赖、0.11.4 与 0.12.0 的 API 差异修复、
帕秋莉组件在旧版的差异、mixin 映射问题等。

**禁止**:

- 不要用 `foreach` 循环脚本**同时改两个工程**。只对某一侧生效的修复(比如 Forge 专用的
  `pack.mcmeta`),顺手批量套到另一侧会污染那个工程的意图与提交历史。
- 两个工程是**独立的 git 仓库**,各自有独立的分支与远端分支;改动前先确认自己在哪个工程里。

---

## 规则 7:提交信息只写改了什么

`git commit` 的说明部分**只描述本次实际改动的内容**。没有改动的东西不要提及。

**反例**(本项目的真实提交):

> `fix: get_remaining 少减了一次 1(帧里存的已经是本轮之后剩余);get_index 维持原公式`

后半句什么都没改 ✗ 写进去只会让历史变长、变糊 ✗ 读的人还得自己分辨哪句是真改动。

**正例**:

> `fix: get_remaining 少减了一次 1`

同类要避免的还有:

- "其余文件未改动" / "其它逻辑保持不变" —— 这是默认状态,不需要声明
- 把"计划做但没做"、"考虑过但放弃"写进提交信息 —— 那是备忘或对话该放的地方
- 提交信息里写"顺便修复了 X",而 X 其实在另一个提交里 —— 只写本提交真正包含的内容

---

---

## 规则 8:不要硬编码别人的组件 id / 注册名,用属性调用

引用咒法学(或任何其它模组)的数据组件、注册名、NBT 键时,**不要**写字面量 ——
比如 `"hexcasting:spellbook_pages"`、`"pages"`、`new ResourceLocation("hexcasting", ...)`。

一律通过对方**公开的常量或持有者**取:

| 要拿什么 | 怎么拿 |
|---|---|
| 1.21.1 的数据组件 | `HexDataComponents.SPELLBOOK_PAGES.get()` |
| 0.11.4 的 NBT 键 | `ItemSpellbook.TAG_PAGES` 这类公开常量 |
| 注册表对象 | 对应的 `DeferredHolder` / `RegistryObject` |

**理由**:字面量在对方改名、或我们迁移版本时**不会报错** ✗ 只会静默失效
(读不到 / 写不进 / 注册不上),排查成本极高;而引用常量时对方改了名会**编译失败**,
问题当场暴露。

对方的常量可以在一处集中转发(例如 `ItemSpellbookTagNames`),好处是升级时只改一个文件。

---

## 项目速览

| 项 | 值 |
|---|---|
| 技术栈 | NeoForge 1.21.1 + ModDevGradle 2.0.148 + Gradle 8.8 + JDK 21 |
| mod id | `hexextra`,包名 `com.llk.hexextra` |
| 主前置 | Hex Casting `1.21.1-0.12.0-devel-pre-64`(**仅作者 CI 有产物**) |
| 构建 | `./gradlew build` / `./gradlew compileJava` |
| 开发服务端 | `./gradlew runServer`(端口 25566,避开真实服务端占用的 25565) |
| 开发客户端 | `./gradlew runClient` |

### 前置依赖全部是「链接式」的,不要往 `libs/` 放 jar

- Hex Casting 走 Jenkins artifact 的 Ivy 仓库(`build.gradle` 里的 `BlameJaredCI-Hexcasting`)
- 其余前置走常规 Maven(BlameJared / ModMaven / Architectury / Shedaniel /
  IllusiveSoulworks / KotlinForForge)
- 版本号统一在 `gradle.properties` 管理

### 已实现的功能

| 功能 | 图案 | 入口类 |
|---|---|---|
| for 循环 | `aeqqqqqawwd` | `pattern/for_loop/ForLoopFeature` |
| if / elseif / else 条件链 | `aedaqda`、`aedaqdaaw` | `pattern/if_else/IfElseFeature` |
| switch 多路等值分派 | `dawwaqw` | `pattern/switch_case/SwitchFeature` |

三者都配了算术重载(`add` / `index` / `abs` / `remove_from` / `replace`),
因此可以在栈上自由拆装。

### 本地开发环境的注意事项(非仓库内容)

以下是开发机上的环境特点,新环境一般用不到:

- 若 Gradle 需要代理,写在用户目录的 `~/.gradle/gradle.properties` 里(不要提交进仓库)。
- 某些环境下 `curl` / `Invoke-WebRequest` 的 HTTPS 可能因系统 TLS 后端损坏而失败,
  这**不影响 Gradle / Java / git**(它们走各自的 TLS 实现)。
- 域名解析到 `28.0.0.x` 这类地址通常是代理的 fake-IP 模式,属正常现象。

### 与代码工程并列的本地副产物目录

开发过程中还有若干**不纳入版本控制**的辅助内容,习惯放在与代码工程并列的 `lootwork/` 里:

- `lootwork/memos/` —— 依赖方案、API 速查、各功能的实现备忘
- `lootwork/tools/` —— 图案校验、书条目校验、语言键校验等脚本
- `lootwork/extracted/` —— 从依赖 jar 提取的源码,供查 API 用

这些只在本机存在,不随仓库分发。
