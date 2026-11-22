# 模块 3 移植记录：灵魂锚与动能剑（r955 / r984 / r993）

基线：上游 `origin/main`（Fabric 1.21.1，r993/r994 之后），本地目标 Forge 47.4.28 / 1.20.1 / Java 17。
资源命名空间由 `mechaniciancombat` 统一转换为 `tkgun`；本地不改变既有 `tkgun` 标识。

## 已移植内容

### 灵魂锚

- 实体 `entity/SoulAnchorEntity`：飞行 / 固定 / 回收三态，方块与实体射线、目标牵引、200 tick 超时。
- 服务端 `SoulAnchorSkillService`：READY → THROW → ANCHOR_FLIGHT → ANCHOR_STALL →
  玩家牵引 / 实体牵引 / 持枪滑步 → 收招/回收；含加速窗口暂停与恢复。
- `SoulAnchorMarks`：15 秒目标发光标记与颜色描边同步。
- 网络包：`SoulAnchorInputPayload`(C2S)、`SoulAnchorSkillPayload`(S2C)、`SoulAnchorStatusPayload`(S2C)。
- 客户端：技能阶段镜像、标记镜像、世界空间锁链带 `SoulAnchorRopeRenderer`、
  第一人称相机模型 `SoulAnchorFirstPersonRenderer` + `SoulAnchorFirstPersonMixin`。
- 资源：`soul_anchor_fpa` / `soul_anchor_projectile` 几何与动画、全部锁链帧贴图、
  第一人称与投射物物品模型、`chain.ogg`。
- 玩家身体姿势：向 `bowgun_rig` / `abyssal_ancient_cannon_rig` / `veritas_et_justitia_rig`
  三份动画合并了上游 `chain_*` 片段，空手/持枪时由 `BowgunRig.pick` 接管。

**解锁条件**：与上游一致，需要护甲模块 `C3`（灵魂锁链，胸甲）+ `T1`（灵魂之锚，护腿）。
模块 4 已移植护甲模块系统与装甲改装台，`SoulAnchorSkillService#hasInstalledModules`
使用 `ArmorUpgradeData.hasPart(...)` 判定。

### 动能剑

- `item/KineticSwordItem`：继承 `BowgunItem`，复用枪械状态机/rig/第一人称链路；右键不进入瞄准。
- `TkGunServer`：六段近战 `GS_ATK_1..6`、伤害帧、前冲、连段缓冲、松开后的 `_back-idle` 收势。
- 第三人称：`GunVisual` 增加 `kinetic_sword` 变体，`BowgunRig.pick` 走独立的 `GS_*` 动画；
  能量面 `KineticSwordEnergyLayer`（6 帧自发光，仅在立场开启时显示）。
- 第一人称：`KineticSwordFirstPersonState/Renderer` + `KineticSwordFpaItem` +
  `KineticSwordFirstPersonMixin`，能量面 `KineticSwordEnergyItemLayer`。
- 网络包：`KineticSwordMovementPayload`(C2S)。
- 资源：`kinetic_sword_rig` 几何/动画、剑与能量贴图、物品模型、`great_sword_slash.ogg`。

**完整机制**：已移植上游的「闪避冲刺奖励（dodge boost）」「GS_ATK_2-3 / 4-5 双段 draft」
「三轮加速循环」「冲刺 ATK_5」「跳跃/跑跳专用速度曲线」与落地收势；
`GunStatePayload` 增加 draft / 加速攻击 / 跑跳 / 收势四个同步位，
客户端第三人称与第一人称按同一套状态选动画。

## 验证

```powershell
./gradlew build -x test --offline --no-daemon --console=plain -Dnet.minecraftforge.gradle.check.certs=false
./gradlew verifySoulAnchorKineticAssets --offline --no-daemon --console=plain -Dnet.minecraftforge.gradle.check.certs=false
```

- 编译与构建通过；JAR 已包含全部新增类与资源。
- `SoulAnchorKineticAssetContract`：动能剑六段/收势/移动/锁链片段、灵魂锚 ready/throw、
  投射物、贴图、物品模型与音效齐全。
- 既有 11 个契约任务全部通过。

**未验证**：实机第一人称观感、锁链贴图朝向、牵引手感、多人同步、与 ShoulderSurfing/Combat Roll
的组合行为。以上均需真实客户端与专服联调。

## 2026-11-22 追加：动能剑立场与翻滚连斩

- 弹药环对动能剑整体关闭：滚轮、中键、F 装填、K 弹药环、右下角弹药 HUD 均不再作用于该物品。
- 右键不再瞄准，改为切换“动能立场”：
  - 开启时攻击伤害类型改为法术伤害（`indirectMagic`）；
  - 热量每秒 +1%，每命中一个有效实体额外 +5%；满 100% 强制关闭；
  - 关闭后每秒 −1%，右下角复用火炮的“热量槽”显示；
  - 立场特效复用动能剑能量面，开启期间常亮。
- 空格改为“六连斩 + 前冲”：
  - 依次播放 `GS_ATK_1..6`，整段向前位移 6 格；
  - 动作长度与伤害帧均按 2 倍速缩短；
  - 六段都走正常 `performKineticSwordHit`，立场开启时同样为法术伤害；
  - 翻滚恢复为真实翻滚位移，动画改用持剑奔跑片段；右键开启立场时执行原空格跃迁。
- 空格六连斩期间锁定 WASD 与身体转向，只允许相机 360° 环视；连斩自身的固定前冲保留。
- 左键普通斩击不再清零起手输入/水平速度，允许按近战减速曲线缓慢移动。
- 攻击硬直/技能动作期间按翻滚键不再预转向：只有 `canTriggerDodge` 为真时才发送翻滚方向并对齐身体，
  避免“动画向后、实际仍被攻击前冲带向前”的方向错位。
- 翻滚期间不再锁定 WASD（避免翻滚后卡操作）；后滚判定为“仅位移”，不发速度奖励，
  结束后恢复相机朝向，避免后滚后按 S 被身体朝向反转成向前移动。
- 翻滚后的加速改为原版“速度”药水效果（Speed II，3 秒），移除 `MOVEMENT_SPEED` 属性修饰符和强制疾跑；
  加速窗口内不再执行 A/D 缓慢转向校正，避免左右翻滚后的方向偏移。
- 猎矛右键部署的玩家上半身动作改为 PlayerAnimator 原生 `hunter_spear_deploy` 动画层，
  由库负责原版手臂枢轴/坐标转换；不再手动采样 JSON 写玩家骨骼，修复手臂错位与动作异常。
- `CombatRollAnimationGuardMixin` 只拦截 Combat Roll 的 `playRollAnimation`，保留翻滚位移、音效与粒子；
  动能剑翻滚时第三人称与第一人称均使用 `GS_run` / `idle`。
- 猎矛右键部署改为不触发原版挥手动作，并补上 r1034 的常量数组姿态通道解析。

**未验证**：六连斩的实机手感、命中判定范围、多人同步与立场热量 HUD 的最终观感。

2026-11-22 复核：`compileJava`、`check -x test`、`jar` 均通过；隔离客户端可启动到主菜单，
未出现 `CombatRollAnimationGuardMixin` 应用错误。翻滚动画拦截、空格连斩锁定与左键缓慢移动
仍需进入世界后做实机手感验收。
