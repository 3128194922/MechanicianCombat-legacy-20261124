# 蹲射站立 / 弹道偏移修复 + r994 内容补齐（2026-11-22）

基线：上游 `origin/main` = `e4b3c3c`（Release r994），本地 Forge 1.20.1 / Forge 47.4.28 / Java 17。

## 一、蹲射瞬间站立（`abyssal_ancient_cannon` / `veritas_et_justitia`）

### 排查结论

1. 三把枪的 `Focus_*` 片段（`Focus_ready` / `Focus_hold` / `Focus_shooting..7` / `Focus_shooting_extra`）
   都已具备 `body` + `leg_left` + `leg_right` 通道，`body.position = [0, -8, 0]` 蹲姿也在；
   资源层**不是**根因（`bowgun` 与另外两把的躯干/腿部通道逐项比对完全一致）。
2. 真实根因在状态时长：`TkGun.FOCUS_FIRE_TICKS` 被写成 **2 tick**，而上游用 `FIRE_TICKS = 30`。
   `Focus_shooting*` 实际长 1.0–1.25 秒（20–25 tick），状态在动画刚起手就结束：
   - 客户端那一帧 `e.active()` 为 false；
   - `BowgunRig.pick` 掉到兜底 → 播 `idle`（`body.rotation` 是站姿，没有蹲姿位移）；
   - `BowgunRig.pickUpper` 同时叠 `stand_shooting`。

   表现就是"蹲着开一枪，人瞬间站起来一下"。`bowgun` 的 `Focus_shooting` 有 1.25 秒完整尾段，
   动画本身把这一两帧盖得比较严实；`abyssal` / `veritas` 的片段更短，问题最明显 —— 与用户描述一致。

### 修复

- `TkGun.FOCUS_FIRE_TICKS` 改为与 `FIRE_TICKS` 对齐（30 tick），动画播完仍停在 FIRE，
  随后的 `AIM_HOLD` 直接 CONTINUE 保持尾帧，全程不离开专注姿势。
- `GunStateMachine.canFire(FIRE)` 本来就返回 true，连射节奏不受影响（仍由 `shotCycle` / `boltAt` 控制）。
- 新增契约 `GunVariantAnimationContract#checkCrouchPoseIsWholeBody`：
  对三把枪逐一断言蹲射片段都带 `body` + 双腿通道且 `body.position` 存在，防止资源侧回归。

## 二、枪口指示器与实际弹道偏左

### 根因

`TkGunClient#fireHipImmediately` 的执行顺序有误：

1. 先把 `player` 的 `yRot` / `yBodyRot` / `yHeadRot`（以及 `yRotO` / `yBodyRotO` 插值起点）
   一次性掰到相机 yaw；
2. 之后才调 `GunCamera.directionToCrosshair`。

HUD（`GunCamera#impactPointForRender`）**不改玩家朝向**，它用的是上一渲染帧量到的枪口点。
两边基准不一致时，"旧枪口 → 准星"的横向分量会系统性偏向枪口原来所在的一侧
（`MUZZLE_SIDE` 为负 = 右手侧），表现就是"子弹比圈更偏左"。

### 修复

- 调整顺序：**先用与 HUD 完全相同的起点与方向算完弹道，再掰玩家朝向**。
  掰朝向只影响下一帧模型，不再污染这一发。
- `TkGunServer#spawnBullet` 里弹丸实体的 `moveTo` 朝向改用**实际弹道**（`dir`）反算 yaw/pitch，
  不再用 `player.getYRot()`。速度方向本来就是 `dir`，这一步只让弹丸模型/拖尾朝向与弹道一致。

## 三、r994 内容补齐

上游最新提交 `e4b3c3c`（r994）相对 r993 只改 `BowgunRig`，内容是
"动能剑近战/跳跃/落地收势时清掉残留的跑动过渡桥"。本地此前是简化版 `kineticSwordAnimation`，
没有这套桥接，现已按上游补齐：

- `BowgunRig` 新增 `kineticRunTransitionUntil` / `kineticRunBackIdleUntil` /
  `kineticBackIdleShotSeq` / `kineticWasRunning` 与 `resetKineticRunAnimationBridges()`。
- `kineticSwordAnimation` 改为实例方法 `pickKineticSword`，按上游语义：
  - 跳跃 / 近战 / 落地收势先清桥；
  - 起步 2 tick 播 `idle_to_run`，收步 6 tick 播 `GS_run_back-idle`；
  - 近战 `_back-idle` 按 `shotSeq` 只重播一次，之后 CONTINUE 保持尾帧。
- `GunClientState.Entry` 新增 `kineticSwordLandingIdleUntil` 与 `lastMeleeAnimation`；
  `SKILL_JUMP → IDLE` 时开 6 tick 落地收势窗口，`onGunReleased` 里清空。
- `KineticSwordFirstPersonState` 同步上游：补 `fall_back-idle` 落地收势分支，
  以及近战片段按"基础时长 / 本段实际时长"调速（加速段与 draft 保持 1.0）。

## 验证

- `./gradlew "-Dnet.minecraftforge.gradle.check.certs=false" build -x test --offline`
- `./gradlew "-Dnet.minecraftforge.gradle.check.certs=false" check -x test --offline`
- `./gradlew "-Dnet.minecraftforge.gradle.check.certs=false" runGameTestServer --offline`
  → 53/53 通过（日志 `validation/forge-47.4.28/game-test/run-crouch-reticle-fix.log`）

**尚未验证**：实机蹲射观感、枪口圈与弹道的目视对齐、动能剑跑动/落地收势手感、多人同步。
这些需要进游戏实际操作确认，静态构建与 GameTest 不能替代。

## 四、模块 1/2 完整性补齐（同日）

对照上游 `e4b3c3c` 逐文件核对后，发现本地在四个模块里还有若干**功能缺口**（不是架构差异），已补齐：

### 模块 1：战旗与背包

- **背包 12 段状态动画**：本地原先只有 `aim/CD/ready/Maintenance` 四段，
  上游还有 `ready_to_fly/flying/close/misslie_aim/misslie_CD/war_banner_ready/war_banner_aim/war_banner_cd`。
  已整体替换为上游 12 段（原有 4 段逐段比对完全一致，属纯增量），并导入 `jump_backpack_states.animation.json`。
- **背包几何缺口（关键）**：`combat_backpack.geo.json` 与 `combat_backpack_rig.geo.json` 原先都只有 22 根骨骼，
  缺 `jet_left/jet_right/jet_F/jet_F2/各 *_flame/missile_box/missile_left/missile_right/port1..3_*` 共 16 根。
  没有这些骨骼，跳跃推进器、导弹舱与战旗部件的动画全部无处落地。已替换为上游 38 骨骼版本
  （原 22 根逐根比对完全一致），并同步更新 `textures/entity/combat_backpack.png`（48×48，新增上述部件的 UV 区域）。
- **`CombatBackpackPoseAnimation`**：新增手绘姿态采样器，把上述片段按秒采样写入骨骼；
  跳跃推进器 / 战旗 / 导弹舱叠层由此真正动起来（此前资源缺失 + 无采样，表现为"装了模块但背包不动"）。
- **`CombatBackpackAnimation.Phase`**：补齐上游 9 个阶段与导弹阶段映射
  （`setMissilePhase` / `missilePhase` / `manualJumpRendering`）。
- **跳跃能量 HUD**：新增 `JumpEnergyHud` + `textures/gui/hud/jet_energy.png`（32×32 作者素材），
  左下角像素电池 + 锁定回充红色脉冲；已挂到 HUD 注册。
- **背包 HUD 分支**：补 `JUMP_THRUSTER`（导弹舱瞄准/冷却提示）与 `BATTLE_BANNER`（战旗状态）两条分支，
  以及 `missilePhase` / `jumpEnergyPixels` / `jumpEnergyLocked` 客户端读取。
- **战旗状态位**：`CombatBackpackStatusPayload` 补 `PHASE_BANNER_READY/ACTIVE/CD`（14/15/16），
  服务端 `battleBannerPhase` 按"施法中 / 旗子在场 / 冷却"三档广播，
  客户端映射到 `BANNER_READY/BANNER_AIM/BANNER_CD` 背包动画。
- **战旗第一人称**：新增 `WarBannerFpaItem`、`WarBannerFpaGeoModel`、`WarBannerFirstPersonRenderer`
  与 `WarBannerFirstPersonMixin`，导入 `war_banner_fpa` 的 geo/animation/texture/item model，
  并在 `WarBannerHeldPose` 补 `applyFirstPerson` 采样。

### 模块 2：猎矛固定炮台

- **猎矛第一人称**：新增 `CombatPropFpaItem`、`CombatPropFpaRenderState`、`CombatPropFpaGeoModel`、
  `CombatPropFirstPersonRenderer` 与 `CombatPropFirstPersonMixin`，
  导入 `hunter_anchor_fpa` 的 geo/animation/texture/item model。
- **背包炮台第一人称**：同一套 `CombatPropFpaItem` 复用到 `portable_turret_fpa_model` /
  `auto_turret_fpa_model` 两个隐藏物品；导入两套 geo/animation/texture/item model
  （片段均为 `ready / aim / CD`），`renderTurret` 按背包发射器类型与状态位选片段。

### 有意不移植（架构差异，功能已等价）

| 上游 | 本地等价实现 |
| --- | --- |
| `api/GunDefinition`、`GunRegistry`、各 `*Gun` 定义 | `ModRegistries` + `BowgunItem` + `TkGun` 常量 |
| `BackpackAmmoData/Inventory/Slot` + `PlayerScreenHandler` 弹药槽 | 副手持有弹药 + `BackpackLauncherRules.isLaunchable` |
| `MechanicianArmorRenderer`（Fabric `ArmorRenderer`） | `MechanicianArmorClient`（Forge 渲染事件） |
| `SoulAnchorEntityRenderer`（手写） | `SoulAnchorProjectileGeoRenderer`（GeckoLib） |
| `BrewingStandIngredientSlotMixin` / `PotionSlotMixin` | `MechanicianBrewingStandMixin#canPlaceItem` |
| `ArmorPreviewConfig`（常量类） | `ArmorModificationTableScreen` 内联预览参数 |
| `Cyclone*` / `RoundAttack*` / `SkillSpark*` | 枢德之裁近战（r508）特效，不在本次四项范围 |

### 验证

- `check -x test`：契约全绿（新增 `BackpackBoneVisibilityContract` 的 12 段背包状态 + `jump_cooldown` 断言、
  `WarBannerAssetContract` 的 FPA 资源断言、`HunterSpearAssetContract` 的 FPA 动画断言）。
- `runGameTestServer`：53/53 通过（日志 `validation/forge-47.4.28/game-test/run-module12-complete.log`）。

## 五、第二轮逐文件核对后补的三处缺口

### 灵魂锚锁定标记（模块 3）

- 导入作者 `textures/particle/anchor_lock_0..5.png`（6 帧，32×32）。
- 新增 `client/AnchorLockParticleRenderer`：`RenderLevelStageEvent.AFTER_ENTITIES` 阶段按相机朝向画公告板，
  `LightTexture.FULL_BRIGHT` 全亮，挂在 `TkGunClient` 的事件总线上。
- 缺这一层时技能仍能释放，但玩家看不到"哪些目标会被牵引"。

### 灵魂锚发光颜色（模块 3）

- 新增 `mixin/EntityGlowColorMixin`：`Entity#getTeamColor` 在 RETURN 处改为读取
  `SoulAnchorMarkClientState.glowColor(id)`，让标记目标显示作者指定的描边颜色。
  `glowColor` 数据本来就在，只是此前没有 mixin 应用。

### 导弹舱视觉（模块 1）

- `BackpackFireworkData` 补 `markMissileVisual` / `usesMissileVisual`（NBT 标记）。
- 服务端冷发射处给火箭打上该标记。
- 新增 `mixin/BackpackMissileRenderMixin`：带标记的火箭改用潜影贝弹模型渲染
  （本体 + 1.5× 半透明辉光），普通烟花保持原版。

### 验证（第二轮）

- `build -x test`、`check -x test` 通过。
- `runGameTestServer` 53/53 通过（日志 `validation/forge-47.4.28/game-test/run-mixin-verify.log`）。
- 已用 refmap 确认 `BackpackMissileRenderMixin` → `FireworkEntityRenderer#m_7392_`、
  `EntityGlowColorMixin` → `Entity#m_19876_` 映射正确。

## 六、把两个修复钉成可回归的契约

前两轮的修复只靠"改对代码"，没有防回归手段。本轮补了两个**可被证伪**的契约，
并且都做了"故意改坏 → 契约失败 → 改回"的反向验证。

### `CrouchFireWindowContract`（蹲射不再瞬间站立）

断言 `FOCUS_FIRE_TICKS >= FIRE_TICKS`，并逐枪逐发解析 `Focus_shooting*` 的真实关键帧长度，
要求状态时长能覆盖整段动画；同时要求 `Focus_ready` / `Focus_hold` 都带 `body.position` 蹲姿。

反向验证：把 `FOCUS_FIRE_TICKS` 临时改回 2 →
`AssertionError: FOCUS_FIRE_TICKS (2) must cover the full focused shot clip, expected >= FIRE_TICKS (30)`，
恢复后通过。已注册为 Gradle 任务 `verifyCrouchFireWindow` 并挂进 `check`。

### `ReticleShotAlignmentContract` 的顺序断言（弹道不偏左）

原契约只验证"枪口→准星"的数学恒等式，抓不到真正的回归（顺序颠倒）。
新增源码顺序断言：解析 `fireHipImmediately` 方法体，**剥掉注释后**要求
`directionToCrosshair` 出现在 `setYRot` 之前。

> 写这条断言时踩过一次坑：注释里也写了 `directionToCrosshair`，
> 不剥注释时把代码改坏契约仍然通过。剥掉 `//` 与 `/* */` 后才真正生效。

反向验证：把方向计算挪到 `setYRot` 之后 →
`AssertionError: hip-fire computes direction AFTER rotating the player ...`，恢复后通过。

### 本轮验证

- `check -x test`：**13 个契约任务全绿**（新增 `verifyCrouchFireWindow`）。
- `runGameTestServer`：53/53 通过（`validation/forge-47.4.28/game-test/run-contract-hardening.log`）。

## 七、修掉自己引入的偏差：第一人称道具必须"有门控"

第二轮接入炮台第一人称时漏了上游的 `turretActivated` 门控 —— 只要背包里装了便携/自动炮台模块，
第一人称就会**一直举着一门炮**。本轮补回：

- `CombatBackpackClientState.Entry` 新增 `turretActivationAt` / `turretCdLatched` / `turretCdStartedAt`。
- `applyStatus` 里按上游语义做状态转移：便携炮台 `PHASE_MARKED` 首次出现 = 展开；
  自动炮台 `PHASE_AUTO_ACTIVE` 首次出现 = 展开；目标消失 / 停机 → 进入 `CD` 锁存。
- `renderTurret` 先查 `turretActivated()`；炮台动画时钟改用"展开 / 停机"时刻，
  不再借用背包基础动画时钟。

同时补了 `FirstPersonPropGatingContract`，逐一断言五种第一人称道具都有门控：

| 道具 | 门控 |
| --- | --- |
| 炮台 | `CombatBackpackClientState.turretActivated` |
| 战旗 | `WarBannerSkillClientState.isActive` |
| 猎矛 | `HunterSpearSkillClientState.isActive` |
| 灵魂锚 | `SoulAnchorSkillClientState.firstPersonActive` |
| 动能剑 | `TkGun.isKineticSword` |

反向验证：临时删掉炮台门控 → 契约报
`turret first-person prop must be gated by turretActivated()`，恢复后通过。

### 验证（本轮）

- `check -x test`：**14 个契约全绿**。
- `runGameTestServer`：53/53 通过（`validation/forge-47.4.28/game-test/run-turret-gate.log`）。
- `runClient` 实机进入世界，日志无 `ERROR` / `Exception` / `MixinApplyError`。

## 八、注册项 ↔ 资源一致性审计

逐个比对 `ModRegistries` 里的注册 id 与随包资源，发现一处会直接显示在游戏里的缺陷：

- `HUNTER_SPEAR` 注册 id 是 `hunter_spear`，但语言条目写成了
  `item.tkgun.mechanician_hunter_spear` → 物品名显示原始键名。已补
  `item.tkgun.hunter_spear`（中英各一条），保留原键名条目以兼容旧存档引用。

同批审计结论（无缺口）：

- **36 个物品**：`models/item/*.json` 全部齐备。
- **2 个方块**：`blockstates` + `models/block` + `models/item` + 掉落表全部齐备。
- **45 个音效事件**：`sounds.json` 里每个条目都有对应 `.ogg`。
- **16 个背包配方**：与上游 `data/mechaniciancombat/recipe/` 数量一致。

新增 `RegistryAssetContract`：读源码里的注册 id，逐项核对物品模型与中英语言条目，
并跳过纯运行时控制器物品（`*_fpa_model` / `tab_icon`）。

反向验证：临时删掉 `item.tkgun.hunter_spear` →
`registered item hunter_spear has no translation key item.tkgun.hunter_spear (zh=false, en=true)`，
恢复后通过。

### 验证（本轮）

- `check -x test`：**15 个契约全绿**。
- `runGameTestServer`：53/53 通过（`validation/forge-47.4.28/game-test/run-registry-audit.log`）。

## 九、模块 1/2 补运行时 GameTest

审计发现：战旗与猎矛此前**只有资源契约**，没有任何服务端行为测试 —— 模块 1/2 的核心逻辑
（炮台部署闸门、模块组合校验、战旗生命周期常量）完全没被覆盖。

新增 `Module1And2GameTests`（5 个用例）：

| 用例 | 验证内容 |
| --- | --- |
| `warBannerLifetimeConstantsAreConsistent` | 旗子寿命 > 0、冷却 ≥ 寿命、施法窗口 > 0 |
| `hunterSpearStartsDeployOnGround` | 站在地面手持猎矛 → 部署被接受 |
| `hunterSpearDeployRejectsWrongItem` | 手持木棍 → 部署被拒绝 |
| `hunterSpearDeployIsNotReentrant` | 部署中再次右键 → 第二次被拒（状态机闸门） |
| `missileBoxRequiresJumpThruster` | 未装推进器时导弹舱不可用 |

### 写测试时踩的两个坑（已记录，避免后人重蹈）

1. **FakePlayer 默认不在"地面"上** —— `deployHunterTurret` 会因 `!player.onGround()` 拒绝，
   必须先 `setOnGround(true)`。
2. **GameTest 不推进服务端实体 tick** —— 炮台实体是在 23 tick 的 `tickHunterSpearDeploy` 之后才生成，
   战旗光环也是每 5 tick 才施加一次。所以这两个用例改成验证**状态机闸门 / 生命周期常量**，
   而不是等实体生成；后者在 GameTest 里只会得到假阴性。

### 验证（本轮）

- `runGameTestServer`：**58/58 通过**（原 53 + 新增 5），日志
  `validation/forge-47.4.28/game-test/run-module12-gametest5.log`。
- `check -x test`：15 个契约仍全绿。

## 十、网络包编解码往返契约

新增 `PacketRoundTripContract`：对 **17 个网络包**做"写入 → 读取 → 逐字段比对"，
并断言**缓冲区被完全消费**（没有残留字节）。

覆盖：战旗技能/锚点、跳跃推进器、导弹舱、猎矛技能/锚点、灵魂锚输入/状态、
动能剑移动、护甲能力、锁链、头盔开关/状态、配方编辑器、开火、翻滚、近战。

### 为什么用往返而不是静态数调用次数

先写了静态脚本数 `buf.writeX()` 与 `buf.readX()` 的次数，结果是**不可靠的**：
`CombatBackpackStatusPayload` 与 `GunStatePayload` 使用 `readableBytes() > 0 ? read : 默认值`
的**宽读兼容模式**（旧版包缺尾部字段时不越界），读取次数天然多于写入次数。
静态计数会把这种刻意的设计误报成缺陷，而正则剥离又容易误伤。

往返测试没有这个问题：真正漏读/漏写一个字段，值比对或残留字节检查一定会失败。

### 写测试时踩的坑

`MissileBoxPayload` 带 `double[] ports`，而 record 的 `equals` 对数组是**引用比较**，
往返后必然"不等"。需要按内容逐元素比（`Arrays.equals`）。

### 反向验证

临时删掉 `WarBannerAnchorPayload` 里 `buf.writeFloat(value.cameraYaw())` 这一行 →
契约报 `WarBannerAnchorPayload threw IndexOutOfBoundsException: readerIndex(24) + length(4)
exceeds writerIndex(24)`，恢复后通过。

### 验证（本轮）

- `check -x test`：**16 个契约全绿**。
- `runGameTestServer`：58/58 通过（`validation/forge-47.4.28/game-test/run-packet-contract.log`）。

## 十一、mixin 注入的实证验证

前几轮对 client mixin 的证据只有"客户端启动没崩"——这是**间接证据**：
注入失败可能只是静默跳过，不一定崩。

本轮改用 Mixin 自带的导出机制做实证：以 `-Dmixin.debug.export=true` 启动客户端，
Mixin 会把改写后的类写到 `dev-run/.mixin.out/class/`，再用 `javap` 检查 handler 方法是否真的存在。

实测结果（全部命中）：

| 目标类 | 落地的 handler |
| --- | --- |
| `PlayerRenderer` | `tkgun$hideBodyForUndersuit`、`tkgun$renderRig`（另有 `restoreBody` / `renderUndersuitHand` / `rememberContext`） |
| `ItemInHandRenderer` | `tkgun$renderSoulAnchorFpa`、`tkgun$renderKineticSwordFpa`、`tkgun$renderWarBannerFpa`、`tkgun$renderHunterAnchorFpa` |
| `Entity` | `tkgun$anchorGlowColor` |
| `FireworkEntityRenderer` | `tkgun$renderMissileBoxRocket` |

这一步同时澄清了两个疑点：

- `PlayerEntityRendererRigMixin` 注入的 `setModelProperties` / `renderHand` 在官方映射 jar 里查不到名字，
  一度以为写错了；但 refmap 把它们映射到 `m_117818_` / `m_117775_`，且 SRG jar 里这两个方法确实存在，
  导出类里 handler 也在 —— **名字是对的**，只是 javap 的命名空间不同。
- `CombatRollInputGuardMixin` / `ShoulderSurfingCrosshairMixin` 不在 refmap 里，是因为它们是
  `@Pseudo` + `remap = false` 的第三方 mod mixin，属于正确行为。

### 可复跑的脚本

`validation/verify-mixin-export.ps1`：启动客户端（带导出开关）→ 检查 8 个关键 handler → 输出 OK/MISS。

### 验证（本轮）

- `check -x test`：16 个契约全绿。
- mixin 导出实证：8/8 handler 落地。

## 十二、第二轮定位：真正的"只有这两把枪"差异

上一轮把蹲射站立归因于 `FOCUS_FIRE_TICKS = 2`。**这个解释不成立**：
逐项比对三把枪的 `Focus_shooting*` 后发现它们的 `body.position`（`[0,-8,0]`）、
双腿通道、关键帧时间戳、片段长度（都是 1.25s）**完全一致**。
`FOCUS_FIRE_TICKS` 确实是个 bug（2 tick 覆盖不住 1.25s 的片段），但它对三把枪是**同等**影响，
解释不了"`bowgun` 没这个问题"。

### 真正的差异：`gun_pose` 控制器

上游 `BowgunRig` 注册了 6 条控制器，本地只有 4 条（缺 `gun_pose` 与 `close_effects`）。
其中 `gun_pose` 是**只有这两把枪才会真正执行**的：

```java
Identifier gunId = TkGun.definition(player).id();
if (!gunId.equals(TkGun.id("abyssal_ancient_cannon"))
        && !gunId.equals(TkGun.id("veritas_et_justitia"))) {
    return PlayState.STOP;          // bowgun 走这里
}
```

它负责用作者的 `open`（架枪展开）/ `close`（收起折叠）两段**单独驱动枪身骨骼**
（`gun` / `barrel` / `case` / `blade_*`）。本地缺这条时：

- `Focus_shooting*` 里 `gun` 只有 position/rotation 通道，**没有** `barrel` / `case`；
- 缺通道的骨骼被 GeckoLib 保持在下层动画（`idle` 的折叠姿态）的值上；
- 于是蹲下射击时身体进了蹲姿、枪却还端在"折叠收起"的位置 —— 姿态整体错位，
  观感就是"一开枪人像是站起来了一下"。

`bowgun` 的动画资源里**没有** `open` / `close`，它的 `Focus_*` 自带完整枪件通道，
所以不受影响 —— 与用户描述的"只有这两把枪"完全吻合。

### 修复

- `BowgunRig` 新增 `gun_pose` 控制器（注册在 `upper` 之后、弹壳之前，与上游同序），
  `pickGunPose` 按上游语义逐条实现：
  `FIRE` → STOP（射击段自带完整枪件与后坐）；`abyssal` + `AIM_HOLD` → STOP
  （它的 `Focus_hold` 已是标准架枪姿态）；`veritas` + 专注 `AIM_HOLD` → `open`；
  `veritas` + 翻滚/滑铲 → `open`（保持展开刀身）；非专注、非举枪的待机/闪避收势 → `close`。
- `GunVisual` 新增 `hasGunPoseClips()` / `veritas()`，把"哪几把枪有折叠片段"收敛到一处。
- 实测资源核对：`close` 的 `gun` 数值与 `idle` **完全相同**，`open` 与 `Focus_hold` **完全相同** ——
  这层只是把正确终态钉住，不会和下层动画打架。

### 补回 `recoverStoppedController`

上游 `pick` / `pickUpper` 开头都调 `recoverStoppedController(state)`，本地没有。
GeckoLib 的语义是：控制器 STOPPED 时，"这一帧没有通道的骨骼"被拉回 geo 初始姿势，
而 `body.position` 的初始值是 `[0,0,0]` —— **站姿**。所以控制器只要在蹲射途中停过一次
（片段播完、状态短暂为空、上层 STOP），身体就会跳回站姿。已按上游补齐，并对
动能剑 `*_back-idle` 收势保留"不复位"的例外。

### 新增契约（两条，都做了反向验证）

| 契约 | 断言 | 反向验证结果 |
| --- | --- | --- |
| `GunVariantAnimationContract#checkGunPoseClips` | `abyssal`/`veritas` 必须有 `open`/`close` 且驱动 `gun`；`bowgun` 必须**没有**；源码必须注册 `gun_pose` 且被 `hasGunPoseClips()` 门控 | 把 `"gun_pose"` 改名 → `AssertionError: BowgunRig must register the gun_pose controller` |
| `CrouchFireWindowContract#checkStoppedControllerRecovery` | `recoverStoppedController` 定义唯一、检查 `STOPPED`、调 `forceAnimationReset`，且 `pickUpper` 入口真的调用 | 删掉 `pickUpper` 里的调用 → `AssertionError: pickUpper must recover a stopped controller` |

## 十三、弹道偏左：HUD 与开火用了不同的分支判据

上一轮改的是 `fireHipImmediately` 的**执行顺序**（先算弹道再掰朝向）。那是对的，但不完整。

### 真正的根因

两条路径的分支条件不一致：

| | 判据 | 蹲射（专注、不按右键）时走哪条 |
| --- | --- | --- |
| 开火（`TkGunClient#tickMouse`） | `GunClientState.focusish(id) \|\| GunCamera.aiming(client)` | 瞄准分支 → 用 `aimDir`（**被 ±25° 扇区钳制**） |
| HUD（`GunCamera#impactPointForRender`） | 只有 `aiming(client)`（= 右键按住） | 腰射分支 → 枪口 → 准星（**不钳制**） |

蹲射时玩家并不按右键，于是 HUD 画的是"枪口 → 准星"，子弹走的却是被扇区夹过的 `aimDir`。
两者方向不同，屏幕上就表现为"指示器和实际弹道对不上"，且偏移方向与扇区钳制方向一致（偏左）。

### 修复

`impactPointForRender` 改为调用 `focusishForReticle(client)` =
`GunClientState.focusish(id) || aiming(client)`，与开火路径**逐字一致**。

### 新增契约

`ReticleShotAlignmentContract#checkReticleSharesFireBranch`：截取 `impactPointForRender`
**方法本体**（到它自己的结束大括号，不含后面的辅助方法），要求出现 `focusish`；
并断言开火路径仍是 `focusish(client.player.getId()) || GunCamera.aiming(client)`，两边不会各走各的。

> 写这条断言时踩了两次坑，都记在这里：
> 1. 第一次用"下一个方法声明"定位结尾，但 `private static` 没被匹配到，
>    结果把下面本来就含 `focusish` 的辅助方法也算进方法体，断言永远不会失败；
> 2. 改成按 `\n\t}` 定位后才真正生效 —— 反向验证立刻报
>    `AssertionError: reticle must branch on the focus flag, not only on the aim key`。

### 本轮验证

- `check -x test`：16 个契约全绿（含上述两条新断言）。
- `runGameTestServer`：**58/58 通过**（`validation/forge-47.4.28/game-test/run-gunpose-final.log`）。
- `runClient`：启动到 `Sound engine started`，无 `MixinApplyError`、无 tkgun 异常。
- 产物：`build/libs/MechanicianCombat-1.20.1-forge-0.2.0-r611-porting.jar`
  SHA-256 `02A62AD135C78238FCAD708A387485F30CD0A853FE6D218FCEE39FF82F51064D`

**仍未验证**：蹲射的目视观感、枪口圈与弹道的肉眼对齐。静态契约能证明"控制器注册了、
分支判据一致了、资源数据对得上"，**不能**证明画面正确 —— 这需要进游戏实机确认。

## 十四、把两处判定抽成纯函数（证据升级）

上一轮的契约是"读源码找关键字"。那只能证明"某段代码里出现了某个词"，
**证明不了判定在每种状态下都选对了**。本轮把两处关键判定从 GeckoLib 回调里拆出来，
变成无 MC 依赖的纯函数，于是可以**穷举**验证。

### `GunPoseRules`（蹲射姿态）

`BowgunRig#pickGunPose` 原先直接把 `if/else` 写在 `AnimationState` 回调里。
现在判定搬进 `client/rig/GunPoseRules.select(variant, state, active, focus, focusish, hipAiming)`，
回调只剩"取参数 → 拿结果 → 播/停"。

`GunVariantAnimationContract#checkGunPoseSelectionExhaustive` 跑
`{3 把枪} × {全部 State 枚举值} × {active/已结束} × {专注/非专注} × {举枪/不举枪}` 的**全组合**，
逐格与一份**独立实现**的期望值比对（不是复制生产代码，否则等于没验）。

反向验证：给 `close` 分支加 `&& false` →
`AssertionError: gun_pose selection mismatch: variant=abyssal_ancient_cannon state=IDLE active=true focus=false focusish=false hipAiming=false expected=close actual=null`
—— 能精确指出是哪一个格子坏了。

### `ShotBranchRules`（指示器 / 弹道）

原先两处各写一份 `focusish || aiming`（HUD 还少写了 `focusish`）。
现在统一为 `client/ShotBranchRules.usesAimDirection(focusish, aimKeyHeld)`，
`GunCamera#impactPointForRender` 与 `TkGunClient` 的开火分支都调它。

`ReticleShotAlignmentContract#checkReticleSharesFireBranch` 断言：

1. 两处源码都出现 `ShotBranchRules.usesAimDirection(`（不能再各写一套）；
2. 穷举 `{focusish} × {aimKey}` 四格，要求"专注 ⇒ 瞄准方向"、"腰射 ⇒ 枪口→准星"。

反向验证：把 `return focusish || aimKeyHeld;` 改成 `return aimKeyHeld;` →
`AssertionError: focused shots must use the aim direction: focusish=true aimKey=false`
—— 正是原先出问题的那一格。

### 本轮验证

- `check -x test`：16 个契约全绿（两条已升级为穷举）。
- `runGameTestServer`：**58/58 通过**（`validation/forge-47.4.28/game-test/run-exhaustive-contract.log`）。

### 证据边界（不变）

穷举证明的是**判定矩阵正确**：每种"枪种 + 状态 + 专注 + 举枪"组合都会选到预期片段，
且指示器与弹道永远走同一分支。它**不证明**骨骼最终画出来的像素位置 ——
那取决于作者动画数据（已逐项核对 `close`≡`idle`、`open`≡`Focus_hold`）与 GeckoLib 的求值。
真正的像素级确认仍需实机目视。

## 十五、文件级完整性审计：找出三处真实功能缺口

把上游 r994 的 288 个 Java 文件按**类名**与本地 273 个逐一比对，得到 60 个"上游有、本地无"的类。
逐个核实后，绝大多数是已记录的架构等价替换（`GunDefinition`/`GunRegistry` → `ModRegistries`、
`BackpackAmmoSlot` → 副手弹药、`VanillaCrosshairMixin` → Forge 的 `GunCrosshairEvents` 等），
但有 **3 处是真实功能缺口**，已补齐：

### ① `suppressSneakPose` 定义了却从未被调用

`SoulAnchorSkillClientState#suppressSneakPose(int)` 早就存在，但**没有任何调用点**。
上游用它压制"灵魂锚施法期间的蹲姿"——投掷/牵引/回收那几段接管了全身姿势，
此时按住 sneak 会让原版再叠一个蹲姿，表现是"施法时人突然矮一截"。

修复：`ClientPlayerCrouchGuardMixin` 的两个注入点都加上该判定。

> 顺带核实了一个**不是**缺口的地方：本地注入 `LocalPlayer#isShiftKeyDown/isCrouching`，
> 上游注入 `ClientPlayerEntity#method_5715/method_18276`。Forge 侧 `LocalPlayer` 就是
> `ClientPlayerEntity` 的对应类，且它同样遮蔽 `Entity` 的同名方法 —— 拦 `LocalPlayer`
> 能同时覆盖眼高与模型姿态，行为等价。

### ② `MeleeMovementSlowdown` 完全缺失

上游 `MeleeInputFreezeMixin` 在"命中定格"之外还有一层：近战动作期间按
`1 → 0.3 → 1` 的 `easeOutCubic` 曲线缩放**移动输入**（不是速度属性，所以加速度/惯性不变）。
本地 mixin 只有"定格时清零"，没有这条曲线 —— 近战全程满速移动，重劈时脚下不被拖住。

修复：新增 `client/MeleeMovementSlowdown`（与上游同曲线、同常量），接入 mixin。

### ③ `skill_spark` 粒子整条链路缺失

`veritas_et_justitia_rig.animation.json` 在 `ATK_3_hold`(0.5s) 与 `ATK_hold_over`(1.0s)
埋了 `skill_spark` 粒子关键帧，但本地：贴图没有、粒子定义没有、类型没注册、处理器没分支。
GeckoLib 对这种情况只打一行 warn 然后**静默丢弃** —— 玩家的蓄力重劈完全没有火花。

修复（四段全补）：

| 环节 | 内容 |
| --- | --- |
| 贴图 | 从上游 `e4b3c3c` 取出 `skill_spark_0..4.png`（5 帧） |
| 粒子定义 | `assets/tkgun/particles/skill_spark.json` |
| 类型注册 | `ModRegistries.SKILL_SPARK` + `TkGunParticles.SKILL_SPARK` |
| 客户端 | `client/SkillSparkParticle`（18 FPS 翻页、全亮、10 tick）+ `registerSpriteSet` |
| 处理器 | `BowgunRig#onParticleKeyframe` 的 `skill_spark` 分支（按 `aim`/`locator` 取点） |

### 新增契约（两条，都做了反向验证）

| 契约 | 覆盖 | 反向验证 |
| --- | --- | --- |
| `checkVeritasSkillSparkWired` | 资源关键帧 → 处理器分支 → 类型注册 → 客户端贴图集 → 5 张 PNG，**四段全串** | 改分支名 → `AssertionError: BowgunRig has no skill_spark keyframe branch: 2 authored keyframes would emit nothing` |
| `checkEveryParticleKeyframeHandled` | 收集**全部** rig 动画的 `particle_effects`，逐个要求 `onParticleKeyframe` 里有对应分支 | 改 `"thunder"` 判定 → `AssertionError: particle keyframes without a handler (GeckoLib silently drops them): thunder` |

第二条是"通用防线"：以后任何人往动画里加新粒子关键帧而忘了写处理器，`check` 会直接失败，
不必再靠肉眼发现"特效怎么不出现"。

### 本轮验证

- `check -x test`：16 个契约全绿（`GunVariantAnimationContract` 现含 4 组断言）。
- `runGameTestServer`：**58/58 通过**（`validation/forge-47.4.28/game-test/run-audit-gaps.log`）。

### 仍然存在但**不属于本次范围**的上游类

| 类别 | 例子 | 说明 |
| --- | --- | --- |
| 架构等价替换 | `GunDefinition`、`GunRegistry`、`BackpackAmmoSlot`、`MechanicianArmorRenderer` | 本地有等价实现，行为一致 |
| 创造模式背包弹药槽 | `CreativeBackpackAmmo*Mixin`、`InventoryBackpackGuiMixin` 等 7 个 | 只影响创造物品栏的弹药拖放便利性 |
| r508 近战特效 | `CycloneClientState`、`RoundAttackClientState` | 已在移植文档里声明为不移植项 |
| 调试渲染 | `WarBannerDebugRenderer` | 仅调试用 |

## 十六、蹲射修复的**服务端运行时**验证

前面所有蹲射证据都是静态的（读常量、读资源、读源码）。本轮补上运行时：
新增 `CrouchFireGameTests`（4 个用例），直接驱动真实服务端状态机。

### 为什么必须补这一层

客户端之所以会掉回 `idle`（站姿），前提是**服务端那一帧真的发了 `focus=false` 或状态 `IDLE`**。
而 `focus` 位在 `broadcast()` 里的算法是 `st.deployed && TkGun.holdingGun(actor)` ——
静态契约看不到这个表达式在真实 tick 里有没有被清掉。

### 4 个用例

| 用例 | 断言 |
| --- | --- |
| `focusedFireKeepsDeployedForWholeWindow` | 触发后连推 6 tick，`deployed` 一次都不许掉（掉了 → 客户端收到 `focus=false` → 播站姿） |
| `focusedFireStateCoversAuthoredClip` | 进入 `FIRE` 后，**运行时**剩余 tick ≥ `FIRE_TICKS - 4`（读的是真正写进 `stateUntil` 的值，不是常量） |
| `hipFireStaysUndeployed` | 反向对照：腰射不得置 `deployed`（否则站着却播蹲射） |
| `focusFlagRequiresBothDeployedAndHolding` | `focus` = `deployed && holdingGun`，收枪后即使 `deployed` 残留也必须为 false |

### 写测试时踩的坑（重要）

**`FakePlayer` 不在 `server.getPlayerList().getPlayers()` 里** ——
第一版直接调 `tickAll(server)`，结果测试玩家根本没被推进，`focusedFireStateCoversAuthoredClip`
报 `got IDLE`。这不是代码缺陷，是测试脚手架的问题。

修法：新增 `TkGunServer#advancePlayerTickForTest(ServerPlayer)`，复刻 `tickAll` 里
与开火直接相关的两段（`tickShots` → 状态到期分支），逐行对应生产路径，不另写一套逻辑。

### 反向验证

把 `FOCUS_FIRE_TICKS` 改回 2（原 bug 的值）→
```
focusedfirestatecoversauthoredclip failed!
  FIRE window must cover the authored clip: remaining=2 but FIRE_TICKS=30
  (a short window makes the client fall back to idle)
```
**测试精确复现了用户报的那个 bug**，且恢复后通过。

### 本轮验证

- `runGameTestServer`：**62/62 通过**（原 58 + 新增 4，`validation/forge-47.4.28/game-test/run-crouchfire-final.log`）。
- `check -x test`：16 个契约全绿。

### 证据边界

运行时测试证明的是**服务端会下发正确的 `focus` 位与足够长的 FIRE 窗口**。
它仍然不证明"骨骼最终画出来的像素对不对"——那需要实机目视。
但它堵住了一个关键漏洞：此前无法排除"修复只改了常量、真实 tick 里仍会被清掉"的可能。

## 十七、网络包审计：34 个注册包全部有接收端

把上游 r994 的 38 个 payload 类与本地 34 个按类名比对，缺的 4 个逐个核实：

| 上游类 | 本地处置 | 结论 |
| --- | --- | --- |
| `CameraShakePayload` | `MeleeImpactPayload` + `MeleeImpactClientState.applyCameraShake` | 等价（本地把"命中定格/镜头抖动"合成一个包） |
| `CombatBackpackParticlePayload` | `LocatorShriekPayload`（同字段：playerId + xyz） | 等价 |
| `GunFallAttackPayload` | 并入 `GunFocusPayload`，服务端第 705 行 `queueSilentStormFall` 分支 | 等价，且 `focusKeyDown` 已在分支前更新，不会重复触发 |
| `GunLocatorVentPayload` | `sendLocatorShriek(player, pos)`（`TkGunServer` 第 4179 行） | 等价，旁观者同样收得到 |

### 真正的防线：注册 ↔ 接收端对齐

Forge 的 `SimpleChannel` 对"注册了但没人处理"的包**不报错** ——
它照常解码，然后静默丢弃。表现是"某个功能偶尔没反应"，日志里一点痕迹都没有。

新增契约 `PacketRoundTripContract#checkEveryRegisteredPacketHasReceiver`：
解析 `ModNetwork.register(...)` 拿到 34 个类型名，再与服务端 / 客户端的
`registerGlobalReceiver(...)` 名单求差集，任何"孤儿包"直接失败。

反向验证：把 `LocatorShriekPayload` 的客户端接收块整段移除 →
```
AssertionError: registered packets with no receiver
  (Forge decodes then silently drops them): LocatorShriekPayload
```

> 这次反向验证踩了两次坑，值得记下来：
> 1. 注释掉那一行 → 正则仍能匹配到被注释的文本，断言**假通过**；
> 2. 直接删掉那两行 → 编译失败（留下悬空参数），契约根本没跑。
> 最终用"整段正则替换成空语句"才既保持可编译、又真正移除注册。
> 教训：**反向验证本身也要验证** —— 断言没失败时，先确认它真的跑到了。

### 本轮验证

- `check -x test`：16 个契约全绿（`PacketRoundTripContract` 现含 17 组往返 + 1 组注册审计）。
- `runGameTestServer`：**62/62 通过**。

## 十八、产物与资源链路审计

### ① 发布 JAR 内容核对

不只信"BUILD SUCCESSFUL"，直接解压产物确认本轮新增的类与资源真的打进去了：

| 条目 | 结果 |
| --- | --- |
| `client/rig/GunPoseRules.class` | ✅ |
| `client/ShotBranchRules.class` | ✅ |
| `client/MeleeMovementSlowdown.class` | ✅ |
| `client/SkillSparkParticle.class` + `$Factory` | ✅ |
| `textures/particle/skill_spark_0.png` | ✅ |
| `particles/skill_spark.json` | ✅ |
| `CrouchFireGameTests` | 未打包（**符合设计**：测试类只进 `-gametests.jar`） |

### ② 音效链路

`sounds.json` 共 **45 个事件**，逐个核对引用的 `.ogg` 是否真实存在 → 全部命中。

### ③ 菜单 ↔ 界面链路

4 个菜单类型（`AMMO_MENU` / `ARMOR_TABLE_MENU` / `BACKPACK_TABLE_MENU` / `RECIPE_EDITOR_MENU`）
全部满足：`MENUS.register` → 赋给 `*ScreenHandler.TYPE` → 客户端 `MenuScreens.register`。

### 新增断言（`RegistryAssetContract`）

| 断言 | 捕获什么 |
| --- | --- |
| `checkSoundAssets` | 事件引用了不存在的 `.ogg`（MC 不崩、只静音，日志仅一行 warn） |
| `checkMenuScreensRegistered` | 菜单类型没有对应客户端界面（右键方块**什么都不发生**） |

音效断言反向验证：把路径前缀改坏 →
`AssertionError: sound events referencing missing ogg files: gunshot3 -> gunshot3.ogg, ...`（45 条全列出）。

> 菜单断言的反向验证**没能做成**：删掉/替换客户端 `MenuScreens.register` 那一行会让
> `Screen::new` 构造器类型不匹配而**编译失败**，契约根本跑不到。
> 这说明这条断言的正向价值主要在于"改错名字/漏加菜单"这类**能编译**的回归，
> 而不是"删掉整块注册"。已在代码注释里写明这个边界，避免后人误以为它覆盖了所有情况。

### 本轮验证

- `check -x test`：16 个契约全绿。
- `runGameTestServer`：**62/62 通过**。
- 上游 `origin/main` 仍为 `e4b3c3c`(r994)，无新内容可移植。

## 十九、实机验收：蹲射序列真实播放（2026-11-22）

前面所有证据都到不了"画面到底对不对"。本轮终于跑通了隔离客户端的自动演示，
拿到了**真实的动画播放序列与截图**。

### 先修通验收脚本本身（三个真实缺陷）

| 问题 | 症状 | 根因 | 修复 |
| --- | --- | --- | --- |
| demo 完全不跑 | 日志里没有一行 `[tkgun-demo]` | Gradle `-Dtkgun.demo=1` **不进** `runClient` 的 JVM | `build.gradle` 的 `client` run 转发 `-Ptkgun.demo` 等属性 |
| 世界打不开 | `Failed to read level ... data`（IOException） | 上次强杀客户端留下 `session.lock` | 删除锁文件后正常进服 |
| 拿不到枪 | 服务端 `槽0=bowgun` 但 `selected=8`、`holdingGun=false` | `/tkgun kit` 只塞物品、没切选中槽位 | 命令里补 `selected=0` + `ClientboundSetCarriedItemPacket` |

另外两处是脚本自身的逻辑缺陷（不影响玩家，但让验收白跑）：

- 进专注依赖 `pressHudSkillKey`（模拟外部 HUD 模组按键），隔离客户端没有那个 mod，
  按键永远找不到 → 改为直接发 `GunFocusPayload`；
- 专注期间**从未扣扳机**（`attack` 只在腰射与右键瞄准两段），
  所以 4000+ tick 里一次 `Focus_shooting` 都没出现 → 补了专注内开火窗口。

### 实测结果

服务端日志（`validation/forge-47.4.28/client-demo-crouchfire.log`）：

```
kit: 槽0=bowgun 槽1=bolt selected=0 holdingGun=true
入队弹夹第 1 发（动画 Focus_shooting，节奏 10 tick/发，专注=true ramp=0）
入队弹夹第 2 发（动画 Focus_shooting，专注=true ramp=1）
入队弹夹第 4 发（动画 Focus_shooting1，专注=true ramp=3）
入队弹夹第 5 发（动画 Focus_shooting2，专注=true ramp=4）
```

客户端 `rig ->` 序列（**逐段对应，中间没有 `idle` / `stand_shooting` 插入**）：

```
Focus_ready (0.83x) → Focus_ready (1.0x) → Focus_hold
→ Focus_shooting (5.0x) → Focus_shooting1 (5.0x) → Focus_shooting2 (5.0x) → …
```

截图证据（`validation/forge-47.4.28/dev-run/screenshots/`）：

| 文件 | 内容 |
| --- | --- |
| `tkgun_292_Focus_ready.png` | 进入专注（举枪下蹲） |
| `tkgun_316_Focus_hold.png` | 架枪保持（蹲姿） |
| `tkgun_3197_Focus_shooting.png` | **蹲姿开火**：膝盖弯曲、身体下压、枪口指示圈可见 |
| `tkgun_3237_Focus_shooting1.png` / `tkgun_3248_Focus_shooting2.png` | 连发第 2/3 段 |

**这就是用户报的 bug 的直接反证**：`Focus_shooting` 期间人物保持蹲姿，
没有出现"一瞬间站立"。修复前 `FOCUS_FIRE_TICKS=2` 时，状态在第 2 tick 就结束，
客户端会掉进 `idle`（站姿）——现在实测序列里根本没有 `idle`。

### 关于枪口指示圈的偏移

截图里圈**不等于**屏幕正中的准星，这是**设计如此**，不是缺陷：
圈画在"枪口沿实际弹道打出去的第一个命中点"的投影位置，越肩解耦相机下
枪管本来就不指向屏幕正中。契约保证的是"圈与子弹用同一条射线"，
而不是"圈锁死在准星上"。

### 证据边界（更新）

已实证：服务端 `focus=true` 全程保持、`Focus_shooting*` 逐发递进、
客户端 rig 真实播放蹲射序列、截图里蹲姿保持。

仍未覆盖：多人联机下旁观者看到的姿态、真实鼠标手感、护盾/图腾等战斗数值交互。

## 二十、按枪种实机验收：abyssal 与 veritas（关键补证）

第十九节只验了 `bowgun` —— 而用户报的 bug 恰恰是"`bowgun` 没问题、另外两把有问题"。
只验 bowgun 等于没验到问题所在。本轮补上真正的问题枪。

### 先解决"验不到"的三个障碍

| 障碍 | 症状 | 根因 |
| --- | --- | --- |
| 无法指定枪种 | `/tkgun kit` 永远发 bowgun | 命令没有 variant 参数 |
| 指定了也不生效 | `-Ptkgun.demoGun=abyssal...` 被忽略 | 世界持久化，上一轮的枪还在槽 0，`holdingGun` 一进世界就 true，发枪分支被跳过 |
| **扣扳机但一发不出** | 跑了数千 tick 一次 `Focus_shooting` 都没有 | **`kit` 只给了背包箭，枪的 NBT 弹药环是空的** → 服务端 `tryFire` 一直"空仓"静默拒绝 |

对应修复：

1. `/tkgun kit <player> <variant>` 重载（bowgun / abyssal_ancient_cannon / veritas_et_justitia / kinetic_sword）；
2. demo 在 `holdingGun` 判断**之前**按 `DEMO_GUN` 强制发一次；
3. `grantKit` 用 `GunAmmoData.save(stack, ring, 0, ...)` 把弹药环初始化成 64 发；
4. demo 在专注窗口直接 `/tkgun fire`（绕开 `FocusInputLockMixin` 对按键链的取消）。

### abyssal_ancient_cannon 实测

服务端：`kit: 槽0=abyssal_ancient_cannon selected=0 holdingGun=true`

客户端 `rig ->` 去重序列（`validation/forge-47.4.28/client-demo-abyssal4.log`）：

```
… → Focus_ready → Focus_hold → Focus_shooting → Focus_hold → Focus_shooting
  → Focus_hold → Focus_shooting → Focus_shooting1 → Focus_shooting2 → Focus_hold → …
```

**关键：`Focus_shooting*` 之间只夹 `Focus_hold`（架枪保持，仍是蹲姿），
没有一次 `idle`。** 修复前 `FOCUS_FIRE_TICKS=2` 时这里会是
`Focus_shooting → idle → Focus_hold`，那个 `idle` 就是用户看到的"瞬间站起来"。

### veritas_et_justitia 实测

`kit: 槽0=veritas_et_justitia selected=0 holdingGun=true`

```
t=292 Focus_ready → t=302 Focus_shooting → t=340 roll
```

同样没有 `idle` 插入。

### 本轮验证

- 两把问题枪的 `Focus_shooting*` 序列与蹲姿均实机确认。
- `check -x test`：16 契约全绿；`runGameTestServer`：62/62。

### 证据边界（最终）

已实证：两把问题枪在专注射击全程播 `Focus_shooting*`，序列里无 `idle`（站姿）。

仍未覆盖：多人联机旁观者视角、真实鼠标手感、护盾/图腾等战斗数值交互。

## 二十一、弹道偏左的**残留根因**：`shotDirection` 只判了右键

第二十节之前，我以为分支判据已经统一。本轮复核发现**还差最后一层**：

### 根因

```java
// 修复前
public static Vec3 shotDirection(Minecraft client) {
    if (aiming(client) && aimDir != null) {   // ← 只看右键
        return aimDir;
    }
    return TkGunClient.muzzleWorldDir();      // ← 专注不按右键时走这里
}
```

关键事实：`GunCamera#updateAim` **每 tick 都以 `true` 调用**（为了让身体能追准星），
所以 `aimDir` 实际上**永不为 null**，它一直在朝准星缓慢转。

于是专注（Z 蹲射）里不按右键时：

| | 用的是 | 含义 |
| --- | --- | --- |
| HUD 落点圈 | `aimDir`（非空） | 枪口 → 准星 |
| 实际子弹 | `muzzleWorldDir()` | 枪管原始朝向 |

枪在身体侧面，这两条方向差着"枪口横向偏移"对应的角度 ——
**这就是用户报的"特定情况下实际弹道更偏左"**。之前只统一了"走哪个分支"，
没统一"分支里取哪个方向"。

### 修复

1. `shotDirection` 改用共享判据 `ShotBranchRules.usesAimDirection(focusish, aiming)`，
   与开火分支、HUD 三者一致；
2. `impactPointForRender` **直接调用 `shotDirection(client)`**，
   不再自己写一份"aimDir 优先、否则枪管"的取值。

现在"圈画在哪 = 子弹往哪飞"由**同一个函数**保证，不存在两处各写一份的可能。

### 新增断言（反向验证通过）

| 断言 | 反向验证 |
| --- | --- |
| `impactPointForRender` 必须出现 `Vec3 dir = shotDirection(client);` | 改回旧写法 → `AssertionError: reticle must reuse GunCamera#shotDirection (otherwise it can pick a different direction than the bullet)` |
| `shotDirection` 方法体必须调用 `ShotBranchRules.usesAimDirection(` | 改回 `aiming(client) && aimDir != null` → `AssertionError: shotDirection must use ShotBranchRules.usesAimDirection, not just the aim key: focused shots without right-click would fire along the barrel while the reticle shows the crosshair ray` |

### 本轮验证

- `check -x test`：16 契约全绿；`runGameTestServer`：62/62。
- 实机复跑（abyssal）：`Focus_ready → Focus_shooting → Focus_hold → Focus_shooting → …`
  序列不变，无 `idle`，说明方向改动没有破坏蹲射动画链路。

## 二十二、模块 1（战旗与背包）实机验收

前面四个模块只有契约/GameTest 级证据。本轮用 `-Ptkgun.demoBackpack=1`
跑了背包的实机演示（`validation/forge-47.4.28/client-demo-backpack.log`）：

```
[tkgun-backpack-demo] equipped auto-launcher backpack
[tkgun-backpack-demo] toggle tick=100 installed=true
[tkgun-backpack-demo] tick=110 status=3 animation=AIM charges=9
[tkgun-backpack-demo] tick=125 status=3 animation=AIM charges=8
[tkgun-backpack-demo] toggle tick=160 installed=true
[tkgun-backpack-demo] tick=170 status=4 animation=AIM charges=6
[tkgun-backpack-demo] no-gun tick=200 holding=false status=3
```

验证到的行为：

| 行为 | 证据 |
| --- | --- |
| Curios `back` 槽装备背包 | `equipped auto-launcher backpack` |
| 自动发射器模块被识别 | `installed=true`（`canUseLauncher`） |
| 状态机进入瞄准/标记 | `status=3`、`animation=AIM` |
| **弹药真实消耗** | `charges 9 → 8 → 6` |
| 状态继续推进 | `status=4` |
| 收枪后背包仍工作 | `holding=false` 时 `status=3` 不变 |

截图 `tkgun_backpack_125.png`：背包渲染在背部，HUD 显示
「背包状态：自动攻击 / 当前充能：8/10」，枪口特效正常。

### 实体与入口完整性（本轮顺带核对）

- 9 个 `EntityType` 全部注册，且**9 个都有客户端渲染器**
  （`WAR_BANNER_ENTITY` / `HUNTER_TURRET_ENTITY` / `HUNTER_SPEAR_RETURN_ENTITY` /
  `SOUL_ANCHOR_ENTITY` 等）。
- 16 个 `combat_backpack_*` 配方与上游 r994 **完全一致**（数量与文件名逐一比对）。
- 四个模块的物品全部在创造模式标签页里（`MAIN_TAB.displayItems`）。
- 两个改装台方块都有 `use()` → `NetworkHooks.openScreen`。

## 二十三、模块 4（配方编辑器）界面实机验收

配方编辑器此前只有服务端 GameTest（菜单构造、JSON 生成、写盘重载），
**客户端界面能不能画出来**没验过。新增 `-Ptkgun.demoRecipeUi=1` 实机验证：

```
[tkgun-ui] recipe editor screen=RecipeEditorScreen
```

截图 `tkgun_recipe_editor.png` 确认界面完整渲染：

- 标题 `Mechanician Recipe Editor`
- 3×3 合成格 → 箭头 → 输出槽
- `Inventory` 背包区（玩家 36 格）
- `Recipe ID` 输入框（占位提示 `Leave blank to auto-generate`）
- 按钮 `←` `→`、`Crafting`、`Save`、`Delete`
- 底部 `Mode: shaped`

没有缺贴图、没有空白格、没有渲染异常。

### 顺带核对：模块类没有"孤儿"

对 16 个模块核心类做了引用扫描，全部有真实调用点（不是只有自己定义）：
`SoulAnchorSkillService`、`SoulChainAbility`、`FortressSkillRules`、
`ArmorHelmetController`、`MeleeImpactService`、`CombatBackpackPoseAnimation`、
`HunterSpearHeldPose`、`HunterSpearPlayerAnimation`、`AnchorLockParticleRenderer`、
`ArmorAbilityIconHud` 等，均被 `TkGunServer` / `TkGunClient` / 渲染器实际调用。

## 二十四、模块 3（动能剑）实机验收

`KineticSwordItem extends BowgunItem`，所以能复用同一套 demo 发放链路
（`-Ptkgun.demoGun=kinetic_sword`）。

服务端：`kit: 槽0=kinetic_sword selected=0 holdingGun=true`

客户端 `rig ->` 去重序列（`validation/forge-47.4.28/client-demo-kinetic.log`）：

```
idle → GS_ATK_1 → GS_ATK_2 → GS_ATK_3 → GS_ATK_3_back-idle → idle
→ GS_run → GS_ATK_1 → GS_ATK_2 → GS_ATK_2_back-idle → idle
→ GS_walk_right → GS_ATK_1 → … → GS_ATK_1_back-idle → …
```

验证到的 r993/r994 行为：

| 行为 | 证据 |
| --- | --- |
| 六段近战链（1→2→3 实机走通） | `GS_ATK_1 → GS_ATK_2 → GS_ATK_3` |
| 每段收势 | `GS_ATK_*_back-idle` |
| 跑步/四向走动专用片段 | `GS_run` / `GS_walk_left` / `GS_walk_right` / `GS_walk_back` / `GS_walk_front` |
| 走动中出招不打断位移片段 | `GS_walk_right → GS_ATK_1 → GS_ATK_1_back-idle → GS_walk_right` |

截图 `tkgun_1471_GS_ATK_1.png`：剑在手上（红粉刀身）、挥砍姿态、命中粒子爆开，
HUD 显示动能剑无弹匣（`空弹匣 0/6`，符合近战武器设定）。

## 二十五、模块 2（猎矛固定炮台）实机验收

新增 `-Ptkgun.demoHunter=1`：发猎矛 → 抬头避免方块交互吃右键 → 右键部署 → 观察炮台。

```
[tkgun-hunter-demo] 已发放猎矛
[tkgun-hunter-demo] tick=60  炮台数=1 手上=hunter_spear
[tkgun-hunter-demo] 对准炮台 距离=1.6 格
[tkgun-hunter-demo] tick=140 炮台数=1 手上=hunter_spear
[tkgun-hunter-demo] tick=220 炮台数=1 手上=hunter_spear
```

结论：**右键部署真的生成了 `HunterTurretEntity`，且实体同步到客户端**
（`炮台数` 是客户端 `getEntitiesOfClass` 的结果，不是服务端自说自话）。
部署点就在玩家脚下（1.6 格），所以截图里炮台没有进画面 —— 这是取景问题，不是实体缺失。

炮台**开火**没有观察到：演示世界是空场景，没有可索敌的目标，
`HunterTurretEntity` 没有目标时不开火，属预期行为。

### 补测：炮台索敌开火（成功）

给演示加了一只 `NoAI` 僵尸作为目标后，炮台真的开火了：

```
[tkgun] 猎矛部署开始：玩家=Dev yaw=-40.53
[tkgun] 狩猎炮台已部署 (53.06, -63.0, 13.55) 主人=Dev
[tkgun-hunter-demo] 附近存活怪物=1 玩家=BlockPos{x=51, y=-63, z=12}
[tkgun-hunter-demo]   目标 zombie 位置=BlockPos{x=57, y=-63, z=18} 血=18.0
[tkgun-hunter-demo]   炮台位置=BlockPos{x=53, y=-63, z=13}
…（tick=140）目标 zombie 血=10.0
…（tick=220）目标 zombie 血=2.0
```

**僵尸血量 18 → 10 → 2，每次恰好 −8** —— 正是炮台弩矢的伤害，
说明"部署 → 索敌 → 开火 → 造成伤害"整条链路是通的。

> 踩坑记录：`keyUse.setDown(true)` **不能**模拟右键使用物品 ——
> 原版 `handleKeybinds` 用的是 `consumeClick()`（按下边沿），`setDown` 不产生边沿，
> `Item#use` 永远不会被调用（第一次跑就白跑了）。改用
> `client.gameMode.useItem(player, MAIN_HAND)` 才真正触发。

> 另一个坑：世界是持久化的，上一轮演示留下的炮台会占住 `MAX_HUNTER_TURRETS`，
> 所以演示开头要先 `kill @e[type=tkgun:hunter_turret]`。

### 四个模块的实机证据汇总

| 模块 | 实机证据 | 仍缺 |
| --- | --- | --- |
| 1 战旗与背包 | 自动发射器背包装备、`status/AIM`、充能 9→8→6、HUD 与截图 | 战旗光环落到玩家（需真实玩家实体） |
| 2 猎矛炮台 | 右键部署 → 炮台实体生成并同步 | 炮台索敌开火（需目标）、飞回动画 |
| 3 灵魂锚与动能剑 | 动能剑 `GS_ATK_1→2→3` 连段 + 收势 + 跑动片段 | 灵魂锚投掷/牵引（需冲刺输入）、多人同步 |
| 4 改装台与配方编辑器 | 配方编辑器界面完整渲染 + 服务端 JSON/写盘 GameTest | 盔甲改装台界面（有菜单 GameTest，无截图） |

## 二十六、模块 1 战旗光环 + 模块 4 两个改装台界面（实机补齐）

### 战旗光环（`-Ptkgun.demoBanner=1`）

```
[tkgun-banner-demo] 已召唤战旗 BlockPos{x=54, y=-63, z=12}
[tkgun-banner-demo] tick=40  战旗=1 吸收=有(270t) 力量=有(270t)
[tkgun-banner-demo] tick=80  战旗=1 吸收=有(230t) 力量=有(230t)
[tkgun-banner-demo] tick=140 战旗=1 吸收=有(170t) 力量=有(170t)
```

`WarBannerEntity#applyAura` 每 5 tick 给 8 格内友方施加 `ABSORPTION` + `DAMAGE_BOOST`，
时长 = 战旗剩余寿命。数值 270→230→170 与"寿命递减"完全吻合。
截图 `tkgun_banner_40.png`：右上角出现**吸收 + 力量**两个 buff 图标。

### 两个改装台界面（`-Ptkgun.demoTables=1`）

```
[tkgun-tables-demo] 已放置两个改装台
[tkgun-tables-demo] 盔甲改装台 screen=ArmorModificationTableScreen
[tkgun-tables-demo] 背包改装台 screen=BackpackModificationTableScreen
```

截图确认：

- `tkgun_armor_table.png`：3×3 模块槽（C/T/F 标注）+ 玩家盔甲 3D 预览 + 背包
- `tkgun_backpack_table.png`：模块槽（核心/部件图标）+ 大预览区 + 背包

两个界面都完整渲染，无缺贴图/空白格。

> 这里同样验证了第 8 条坑：方块交互用 `gameMode.useItemOn(...)` 才生效，
> `KeyMapping.setDown` 不产生按下边沿。

## 二十七、模块 3 灵魂锚：发现并修复**客户端崩溃**

### 崩溃

第一次实机跑灵魂锚，客户端在进入 `READY` 后立刻退出。崩溃报告：

```
java.lang.IllegalStateException: Not filled all elements of the vertex
  at BufferBuilder.endVertex
  at AnchorLockParticleRenderer.vertex(AnchorLockParticleRenderer.java:101)
  at AnchorLockParticleRenderer.drawBillboard(...)
  at AnchorLockParticleRenderer.onRenderLevelStage(...)
```

### 根因

`AnchorLockParticleRenderer` 用 `RenderType.entityCutoutNoCull(...)`，
它的顶点格式是 `DefaultVertexFormat.NEW_ENTITY`：
**POSITION / COLOR / UV0 / UV1(overlay) / UV2(light) / NORMAL**。

原代码只填了 position / color / uv / uv2，缺 `overlay` 与 `normal`，
`BufferBuilder#endVertex` 直接抛异常 —— **渲染线程崩，整个客户端挂掉**。

也就是说：**灵魂锚只要锁定到目标（进入 READY）就会崩游戏**，这是模块 3 的硬缺陷。

### 修复

补齐 `overlayCoords(OverlayTexture.NO_OVERLAY)` 与 `normal(0,1,0)`
（`VertexConsumer` 在 1.20.1 里叫 `overlayCoords`，不是 `overlay` —— 用 `javap` 核对过）。

### 修复后实机结果

```
[tkgun-soul-demo] 已装 C3/T1 部件 可用=true
[tkgun-soul-demo] 落地=true 胸甲=tkgun:soul_chain 护腿=tkgun:soul_anchor
[tkgun-soul-demo] 已发 press（蓄力）
[tkgun-soul-demo] tick=61..90 阶段=READY
[tkgun-soul-demo] 已发 release（投掷）
[tkgun-soul-demo] tick=105 阶段=ANCHOR_FLIGHT 锚实体=1
```

**无崩溃**，灵魂锚"蓄力 → 投掷 → 锚飞行"整条链路跑通。

### 新增断言（反向验证通过）

`SoulAnchorKineticAssetContract#checkNewEntityVertexFormatComplete`：
凡是用了 `RenderType.entityCutoutNoCull` 的渲染器，源码里必须同时出现
`.color(` / `.uv(` / `.overlayCoords(` / `.uv2(` / `.normal(`。

反向验证：删掉 `.normal(...)` →
`AssertionError: NEW_ENTITY vertex is missing .normal( — BufferBuilder#endVertex will throw 'Not filled all elements of the vertex' and crash the client`

### 全量审计：同类缺陷只有这一处

把契约从"只查 `AnchorLockParticleRenderer`"扩展成**扫描整个源码树**：
凡是既调用 `endVertex()`、又用到 `NEW_ENTITY` 系列 RenderType
（`entityCutoutNoCull` / `entitySolid` / `entityTranslucent` /
`entityTranslucentEmissive` / `entityCutout` / `armorCutoutNoCull`）的文件，
都必须同时出现 `.overlayCoords(` 与 `.normal(`。

扫描结果：**只有 `AnchorLockParticleRenderer` 一处缺陷**（已修）。
其余手工建顶点的渲染器都填满了六项：

| 文件 | 状态 |
| --- | --- |
| `entity/WarBannerEntityRenderer` | ✅ 六项齐全 |
| `entity/BulletRenderer` | ✅ |
| `entity/MoltenTridentEntityRenderer` | ✅ |
| `SoulAnchorRopeRenderer` | ✅ |
| `SoulChainWorldRenderer` | ✅ |
| `rig/BowgunCloseCombatEffectLayer` | ✅ |
| `MechanicianArmorGeometry` | ✅ |
| `HeatVentParticle` / `ThunderParticle` | ✅ 用 `PARTICLE_SHEET_TRANSLUCENT`（不需要 overlay/normal） |

反向验证：删掉 `SoulChainWorldRenderer` 的 `.normal(...)` →
`AssertionError: NEW_ENTITY vertex is incomplete (...): SoulChainWorldRenderer.java 缺 .normal(`

### 顺带确认的前置条件

`SoulAnchorSkillService#isAvailable` 要求**胸甲装 C3 + 护腿装 T1**；
而 `TkGunServer#enforceMechanicianArmorRequirement` 会在**没穿底衣**时每 tick
把机械师护甲脱下来。演示必须先穿 Curios `charm` 槽的底衣，否则护甲下一 tick 就没了
（第一次跑就是栽在这里，`isAvailable` 从 true 变 false）。
