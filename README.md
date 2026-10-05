# AutoTrader

**Minecraft 26.2 · Fabric · 纯客户端**的村民自动交易模组。

对着村民打开交易界面，选中一笔交易按一个键加进自动列表，之后每次打开交易界面，它就会把列表里的
交易**一口气刷到刷不动**。模组里**不写死任何物品**——付款物和产物都是从你眼前的实时报价里推算出来的，
所以「20 线 → 1 绿宝石」「1 绿宝石 → 1 书架」都适用。

![Minecraft](https://img.shields.io/badge/Minecraft-26.2-informational)
![Loader](https://img.shields.io/badge/Fabric%20Loader-%E2%89%A50.19.0-blueviolet)
![Side](https://img.shields.io/badge/side-client--only-success)
![License](https://img.shields.io/badge/license-MIT-green)

> ⚠️ 本模组是**纯客户端**实现，走原版交易协议。自动化操作可能违反部分服务器的规则，
> 请在允许的服务器 / 存档里使用，风险自负。

---

## 特性

| | 功能 |
|---|---|
| ⚡ | **一键批量交易**：选中一笔交易按 `R` 加入自动列表，之后只要打开村民界面就自动把列表里的交易刷到底（没货 / 没材料 / 放不下才停）。 |
| 🧩 | **不写死物品**：产物与付款物全部由**实时报价 + 自动列表**推算，任何村民交易都能自动化。 |
| 🖱️ | **全程不碰鼠标**：取货与丢产物只用原版容器点击路径，**光标始终为空**，触屏 / 移动端也能正常丢东西。 |
| 🗑️ | **产物处理可切换**：产物**丢在地上**（默认）或**直接收进背包**。 |
| 🩹 | **付款格自愈**：自动清掉卡在付款格里的残留物，腾位继续交易。 |
| 🏷️ | **涨价刷新**（可开关）：交易被涨价卡住时，关闭并重开村民界面以刷新价格。*仅部分服务端有效，见下。* |
| 🚪 | **进服自动开界面**（可开关）：进服 10 秒内自动打开**最近且够得着**的那只村民的交易界面。 |
| 🎥 | **视角控制**：打开界面时可微调视角，或平滑漂移到设定的固定角度。 |
| ⚙️ | **图形化设置界面**：带滚动条，所有开关与快捷键都能在游戏内修改。 |

## 环境要求

| 项目 | 版本 |
|---|---|
| Minecraft | **26.2** |
| Fabric Loader | **≥ 0.19.0**（已在 0.19.4 实测） |
| Fabric API | **0.161.0+26.2** 或更高（必需） |
| Java | **25** |
| Mod Menu（可选） | 20.0.3+（装了就能从模组列表点进设置） |

## 安装

1. 安装 Fabric Loader（≥ 0.19.0）与 Java 25。
2. 把下面两个文件放进 `.minecraft/mods/`：
   - `autotrader-1.0.0.jar`（本模组）
   - `fabric-api-0.161.0+26.2.jar` 或更高（必需依赖）
3. （可选）再放入 **Mod Menu**，即可从模组列表进入设置界面；不装也能用 `P` 键打开。

## 快速上手

1. 右键村民，打开交易界面。
2. 点一下想自动化的那笔交易（选中它）。
3. 按 `R`，聊天栏提示「已加入自动列表：20× 线 → 1× 绿宝石」。
   - 对同一笔交易再按一次 `R` 即为移出。
4. 只要「自动交易」开着、列表非空，交易界面一打开就会**把列表里的报价一次性刷到不能再刷**。
   - 若某笔报价因**涨价**卡住，会自动关掉再重开村民尝试刷新价格（见下「涨价刷新」）。
5. 按 `P` 打开设置界面，可调整所有开关、视角与快捷键。

### 默认快捷键

| 按键 | 作用 |
|------|------|
| `R` | 在村民界面中：把当前选中的交易加入 / 移出自动列表 |
| `G` | 开关自动交易 |
| `P` | 打开模组设置界面 |
| `L` | 开关「打开界面时平滑转视角」 |

快捷键都能在设置界面里改键，也会出现在原版「选项 → 控制」中。

## 设置界面

按 `P`（或通过 Mod Menu）打开，包含：

- 自动交易总开关
- 丢弃产物开关（开：丢地上；关：收进背包）
- **涨价时关开刷新价格**开关
- **自动打开村民交易gui**开关
- 空闲重试间隔（毫秒）
- 打开界面时调整视角 + 角度
- 打开界面时平滑转视角 + 目标水平角 / 俯仰角 / 漂移时长（刻）
- 四个快捷键
- 自动交易列表的查看 / 删除 / 清空
- 一行实时显示的**目标朝向**（读法与 F3 的 Facing 一致：水平角 0=南 90=西 180=北 -90=东；俯仰角 0=平视 90=仰视 -90=俯视）

## 配置文件

路径：`config/autotrader.json`。设置界面每次改动都会立即写盘；手改后下次启动生效。

```json
{
  "enabled": true,              // 自动交易总开关（= G 键）
  "discardProducts": true,      // true=产物丢地上；false=产物收进背包。都不碰光标
  "refreshPriceOnRaise": true,  // 涨价卡住时关闭并重开村民刷新价格（仅部分服务端有效）
  "autoOpenVillagerGui": false, // 进服 10 秒内自动打开最近够得着的村民（默认关）
  "tradeIntervalMs": 500,       // 空闲时重试间隔（毫秒），不影响批量成交速度
  "rotateViewOnOpen": false,    // 打开村民界面时是否偏转视角
  "viewYawDegrees": 0.0,        // 偏转多少度（可负）
  "driftViewOnOpen": false,     // 打开村民界面时是否平滑漂移到固定视角
  "driftViewYaw": 0.0,          // 漂移目标水平角
  "driftViewPitch": 0.0,        // 漂移目标俯仰角
  "driftViewTicks": 10,         // 漂移时长（刻）
  "hotkeys": {                  // 快捷键（与原版 options.txt 双向同步）
    "addTrade": "key.keyboard.r",
    "toggle": "key.keyboard.g",
    "openConfig": "key.keyboard.p",
    "driftView": "key.keyboard.l"
  },
  "trades": [                   // 自动交易列表，物品用注册名
    {
      "costA": "minecraft:string", "countA": 20,
      "costB": "", "countB": 0,
      "result": "minecraft:emerald", "resultCount": 1
    }
  ]
}
```

说明：

- **匹配只看物品种类**（不比对数量），所以村民因供需涨价 / 降价后仍能匹配上。
- 若界面上有多笔报价种类完全相同，会命中第一笔有货的。
- 单笔报价最多两种付款物品，`costB` 为空表示只有一种。
- 配置文件损坏（非法 JSON / 类型不对）时**不会**被悄悄重置为默认值：原文件会保留为
  `autotrader.json.broken`，设置界面也会给出提示。

## 从源码构建

```bash
./gradlew build        # 产物：build/libs/autotrader-1.0.0.jar
./gradlew runClient    # 在开发环境启动客户端
```

- 需要 **JDK 25**（`JAVA_HOME` 指向 JDK 25，或在 `gradle.properties` 里设置 `org.gradle.java.home`）。
- 依赖版本见 `gradle.properties`（Loom `1.18.2`、Loader `0.19.4`、Fabric API `0.161.0+26.2`）。
- Minecraft 26.2 的 jar 不再混淆，因此**不需要 mappings 配置**，Loom 也不对产物做 remap。

## 实现说明

<details>
<summary>展开（给想改这个模组的人）</summary>

### 成交只认 PICKUP / THROW

- 26.2 里 `MerchantResultSlot.onTake` 才是真正扣款、记账、给经验的地方，
  而 `AbstractContainerMenu.doClick` 只有 `PICKUP` 与 `THROW` 分支会（经 `Slot.safeTake`）调用 `onTake`；
  **`QUICK_MOVE`（shift 点击）分支只反复 `quickMoveStack`，不会触发成交**。
  - 丢产物：对结果格发 `THROW`（`button=1` = 整摞，等价于 Ctrl+Q）。
  - 收产物：对结果格发 `QUICK_MOVE`，它在把产物移进背包的过程中会对源槽调用 `onTake`，交易同样成立。
- **`THROW` 是「不依赖鼠标」的正解**：`doClick` 的 THROW 分支直接 `slot.safeTake(...)` + `player.drop(...)`，
  光标始终为空。因此「把背包里所有同种产物丢出去」= 对那些格子逐个 `THROW`。
- **一 tick 连发多个点击是安全的**：`ServerGamePacketListenerImpl.handleContainerClick` 在 stateId
  不匹配时只标记「需要重同步」，仍照常执行 `menu.clicked(...)`。
- **补付款格**：`MerchantScreen.postButtonClick()` 做原版那三件事（`setSelectionHint` → `tryMoveItems` →
  发 `ServerboundSelectTradePacket`），服务端用**它自己那份权威背包**补满付款格。
- **结果格非空 ⟺ 这笔能成交**：`MerchantContainer.updateSellItem()` 在没货 / 无匹配报价时会清空结果格。
- **判断是否成交看付款格，不看结果格**：`onTake` 后 `updateSellItem` 会立刻重新填上结果格；
  只有付款格会因 `onTake` 缩小。

### 不硬编码物品

- **产物**：看物品是不是**某笔要跑的交易的 `result`**（从 `menu.getOffers()` 现场读）。
- **付款物保护**：若某物品还是「某笔要跑的交易的 `costA/costB`」就不丢；保护集合同时来自配置列表
  与**当前选中的报价**，因此即便列表过期 / 手改，也不会误扔正在被交易掉的东西。
- 判定只用到 `MerchantOffer` 的 `getResult()` / `getItemCostA()` / `getItemCostB()`，代码里没有任何物品 id。

### 卡槽自愈 / 涨价刷新

- **输入槽卡物**：`MerchantMenu.tryMoveItems` 只会把付款格补到所需数量，多出的残留物必须能退回背包
  才腾得开；背包满时退不回，格子被占住，下一笔就补不进料。`clearForeignPayment` 把「付款格里、
  当前报价用不上的东西」`THROW` 掉，然后重发选交易包。
- **涨价刷新**：`MerchantOffer.getCostA()` 含供需调整，`getBaseCostA()` 是基础量，前者更大即被顶了价。
  客户端的报价列表只在服务端重发时更新，所以可能一直用旧价。当某笔想要的报价**卡住**
  （结果格补不上、被顶到付不起）且当前价 > 基础价时，模组调用 `Screen.onClose()`（= 按 Esc：关容器 + 清屏）
  再对**同一个村民**发一次 `interact`，服务端就会重新 `startTrading` / `openMenu` 并重发报价——
  在**关闭界面会重置涨价**的服务端上，这一下即可把价格刷回来。
- 刷新节流：最多连刷 `3` 次，两次间隔至少 `40` 刻，等界面回来最多 `80` 刻；仍能出货的报价只在涨到
  `2×` 基础价时才打断，且同一报价同一价格只重开一次（`priceSignature`）。

### 其它

- 光标上有物品、或鼠标按键正被按住（你在手动拖东西）时，自动交易整个让开；按键一直按住最多让 3 秒，
  防止触屏客户端把按键状态卡死。光标上东西超过 3 秒未放下会提示一句。
- 背包格按**容器身份**定位（`slot.container == player.getInventory()`），不写死下标。
- `MerchantScreenAccessor`（Mixin）暴露了原版私有的 `shopItem`（当前选中报价下标）并 `@Invoker`
  了私有的 `postButtonClick()`。
- `MultiPlayerGameModeMixin` 只在 `interact` 的 `HEAD` 记一笔「玩家右键了一个 `Merchant`」，不改动交互本身。
- **进服自动开界面**（`VillagerAutoOpen`）：`ClientPlayConnectionEvents.JOIN` 上开启一个 200 刻（10 秒）窗口，
  每 10 刻扫一次 `level.entitiesForRendering()`，取所有 `AbstractVillager`（村民 + 流浪商人）中
  `player.distanceToSqr` 最小且 `player.isWithinEntityInteractionRange(e, 0.0)` 为真的一只并发交互包。
  有任何界面开着时窗口**不走表**（用来躲开进服时的「加载地形」画面）；一见 `MerchantScreen` 即结束窗口。
- 快捷键必须在**游戏 Options 构建之前**注册，因此客户端入口点第一件事就是引用 `KeyBindings` 触发其静态初始化。

</details>

## 兼容性与已知限制

- **纯客户端**：`fabric.mod.json` 里 `"environment": "client"`，不含服务端代码 / 注册表。
- **不会自动走到村民旁边**，也不会替你关界面；界面默认需要你自己开着。两个例外：
  ① 开启「自动打开村民交易gui」后，进服 10 秒内会替你右键**最近够得着**的那只村民；
  ② 涨价刷新会「关掉再重开」**当前这个**村民。
- **涨价刷新只在部分服务端有效**（如关闭界面会重置涨价的 Leaf 类服务端）。原版等不会重置的服务端上，
  重开也刷不回来；连刷 3 次后会安静地继续按现状交易。它依赖「你右键过这个村民」。
- **产物是直接丢在地上的**，会连同**背包里原本就有的**同种产物一起丢掉。若某物品既是产物、又是另一笔
  **要跑的交易**的付款物，它会被保留；确实想连它一起扔，先把那笔用它付款的交易从列表里移除。
- **未实现**：把「这家货刷完了就换下一个村民」之类的跨村民逻辑；自动交易列表上限 32 条。
- 自动化操作可能违反部分服务器的规则，请在允许的环境中使用。

## 更新日志

### 1.0.0
首次公开发布。

- 一键批量交易：把选中的报价加入自动列表，之后打开村民界面即自动刷到底。
- 不写死任何物品，产物与付款物全部由实时报价推算。
- 全程不碰鼠标（成交走 `PICKUP` / `THROW`），触屏 / 移动端也能正常丢产物。
- 产物处理可切换：丢在地上（默认）或收进背包。
- 付款格卡物自愈。
- 涨价时关闭并重开村民界面刷新价格（可开关，仅部分服务端有效）。
- 进服 10 秒内自动打开最近且够得着的村民（可开关）。
- 打开界面时的视角偏转 / 平滑漂移。
- 带滚动条的图形化设置界面，快捷键可在游戏内修改。

## 许可

本项目基于 [MIT License](LICENSE) 发布。
