# 模块 4 移植记录：装甲改装台 / 背包改装台 / 配方编辑器

基线：上游 `origin/main`，本地 Forge 47.4.28 / 1.20.1 / Java 17；资源命名空间统一为 `tkgun`。

## 装甲模块系统

- `item/ArmorPartItem`：C1/C2/C3/T1/T2/T3/F1/F2/F3 九类槽位；当前实际物品为
  `undying_totem`(C1)、`pauldorn_left`(C2)、`soul_chain`(C3)、`soul_anchor`(T1)。
- `item/ArmorUpgradeData`：模块 id、完整模块栈、护盾充能/冷却、图腾冷却、头盔展开状态，
  全部存于对应护甲物品的 NBT（`tkgun_armor_part_*` / `tkgun_shield_*`）。
- `ArmorAbilities`：C2 左肩盾（伤害抵消 + 充能恢复 + 击杀回充）、C1 守护图腾（免死 + 冷却）；
  每 10 tick 广播 `ArmorAbilityPayload`，客户端 `ArmorAbilityIconHud` 绘制护甲条/生命条图标。
- `SoulChainAbility`（C3）：命中后有 40% 概率（已标记目标必定触发）在 5/10 格内依次链接
  最多 3/6 个目标，按 70%/50% 递减传递伤害；`SoulChainPayload` + `SoulChainWorldRenderer`
  绘制世界空间锁链带，30 tick 淡出。
- 灵魂锚解锁改为官方判定：胸甲 C3 + 护腿 T1。

## 装甲改装台

- `block/ArmorModificationTableBlock`：与背包台相同的朝向/碰撞箱/右键打开逻辑。
- `screen/ArmorModificationTableScreenHandler`：三个只读护甲镜像槽 + 九个模块槽；
  模块写入护甲 NBT，替换/拆下时把旧模块退回槽位；关闭界面时再次同步。
- `client/screen/ArmorModificationTableScreen`：作者 GUI 贴图 + 可旋转玩家预览。
- 资源：方块状态/模型/物品模型/方块与 GUI 贴图、`gui_chest.png`、护甲模块物品贴图。
- 护甲模型：`MechanicianArmorGeometry` 升级为上游的多贴图/模块骨骼版本——
  `soul_chain`(C3)、`pauldorn_left`(C2)、`undying_totem`(C1)、`灵魂之锚`(T1)
  只在对应模块安装后绘制；C3 使用动画自发光贴图，头盔模型带展开/折叠动画接口。
  已同步上游 `armor_models/*.bbmodel` 与 `mechanician_{chestplate,leggings,boots,helmet,undersuit}.png`。

## 背包改装台重写

- 新增 `GOLEM_CAPSULE` 模块与 `golem_dispatch_capsule` 物品（创造模式可直接生成熔岩傀儡）。
- 第三附加槽现在按发射器类型校验：跳跃推进器 → 导弹舱；召唤天线 → 魔像空投舱。
- `BackpackUpgradeData` 增加魔像舱标记/槽位与 `canSummonGolems`；切换发射器时清理不匹配模块。
- 召唤天线只有在安装魔像空投舱时才允许召唤；否则按 X 仅刷新目标标记。

## 配方编辑器

- `/tkgun recipeeditor`（权限 2）打开三页编辑器：工作台有序/无序、熔炉、酿造。
- `screen/RecipeEditorScreenHandler`：把槽位内容写成数据包 JSON 到
  `<world>/datapacks/tkgun_live/data/tkgun_custom/{recipe|brewing}/*.json`，
  覆盖前备份到 `backups/`，保存后自动启用数据包并 `reloadResources`。
- `item/MechanicianBrewingRecipes` + `MechanicianBrewingStandMixin`：
  酿造台支持“输入物品 + 试剂 → 输出物品”的自定义配方（400 tick），
  并把可放入槽位的判定扩展到自定义输入/试剂。
- `client/screen/RecipeEditorScreen`：作者三页 GUI（工作台/熔炉/炼药台贴图），
  含配方 ID、有序模式、经验、烧炼时间、保存/删除按钮。
- 网络：`RecipeEditorActionPayload`（ID 30，C2S）。

## 验证

```powershell
./gradlew build -x test --offline --no-daemon --console=plain -Dnet.minecraftforge.gradle.check.certs=false
./gradlew check -x test --offline --no-daemon --console=plain -Dnet.minecraftforge.gradle.check.certs=false
```

- 编译/构建通过；mixin refmap 已包含 `serverTick` / `canPlaceItem` 映射。
- 新增契约：`verifyArmorModificationAssets`、`verifyRecipeEditor`、`verifyGunVariantAnimations`
  （三把枪蹲射片段、动能剑 draft/跑跳、头盔展开/收起动画名）。
- 既有契约全部通过。

**未验证**：GUI 实机布局/预览旋转、模块安装与拆下、护盾/图腾实战、配方保存后数据包热重载、
自定义酿造在真实酿造台中的时序。以上需要真实客户端与专服联调。

**运行期冒烟**：专用服已实际启动到 `Done (1.879s)`（日志
`validation/forge-47.4.28/dev-server/run-kinetic.log`），注册表、方块/菜单、Mixin
（含酿造台 `serverTick`/`canPlaceItem`）与资源加载均无异常。

**GameTest**：`runGameTestServer` 运行 53 个测试全部通过（日志
`validation/forge-47.4.28/game-test/run-recipe-reload.log`），其中新增用例覆盖：
动能剑 ATK_1→ATK_2 连段、装甲台写入/移除 C1 模块、配方编辑器 15+36 槽位构建、
灵魂锁链网络包往返、头盔展开/折叠、T1 写入护腿、背包附加槽类型校验、
有序/熔炉配方 JSON 生成、灵魂锚 C3+T1 解锁、**配方写盘 + 数据包热重载**
（文件落盘、`pack.mcmeta` 生成、`file/tkgun_live` 被启用）；
原召唤天线用例已按“需要魔像空投舱”更新并通过。

**客户端冒烟**：`runClient` 已实际进入标题界面（Sound engine/TextureAtlas 就绪，
日志 `validation/forge-47.4.28/dev-run/run-client-helmet.log`），客户端 Mixin、
GUI/渲染器注册与资源加载无异常；同时修复了 `sounds.json` 中 10 条仍指向
`mechaniciancombat:` 命名空间的音效路径，启动时不再报 “sound does not exist”。

**实机界面验收**（快速进入 `player-animation-acceptance` 存档）：
- `/tkgun recipeeditor` 三页编辑器正常打开，合成格/输出格/背包/ID/保存删除按钮均渲染。
- 装甲改装台放置在世界中可正常打开，C/T/F 护甲槽与 9 个模块槽渲染正确；
  实机发现玩家预览按“中心点”传参会把人物裁掉，已改为 1.20.1 的脚底锚点
  （`feetY = 预览底边 - 6`），复测玩家预览正常显示。
- 动能剑第一人称模型可见，第三人称 rig 日志出现 `GS_walk_*` / `idle`，说明
  `GunVisual` 变体与 `GS_*` 动画选择生效。
- 背包改装台 GUI 贴图、背包槽与四个模块槽渲染正常；背包预览区的 3D 模型
  已实机确认可正常渲染（Maintenance 姿态）。此前"预览为空"是测试方式问题：
  `/data merge block` 不会刷新已加载方块实体的内存库存，需用带 NBT 的
  `/setblock` 重建方块，背包才会真正进入 `BackpackModificationTableBlockEntity`。

**头盔控制器**：已移植 `ArmorHelmetController`（G 键展开/折叠、15 tick 过门、三段时间音效、
每 20 tick 广播 `HelmetStatePayload`）与 `HelmetClientState`；头盔模型动画按 `展开/收起` 播放，
头发第二层在展开 0.25s / 折叠 0.38s 节点切换。
