# 猎矛部署玩家动作修复（2026-11-24）

## 现象

右键 `tkgun:hunter_spear` 部署炮台时，玩家手臂位置错位、上半身动作异常；
手持猎矛模型本身的动画正常。

## 根因

- 旧实现手工采样 `player_animations/hunter_spear_deploy.json` 后直接写
  `torso` / `rightArm` / `leftArm`，缺少 GeckoLib 到 PlayerAnimator 的坐标转换：
  作者 JSON 的骨骼 `position.y` 需要取反后再叠加到原版玩家骨骼。
- 直接改用 `PlayerAnimationRegistry` 也会失败：本地 PlayerAnimator
  `1.0.2-rc1` 的注册表按 `player_animation`（单数）扫描资源，
  而作者资源目录是 `player_animations`（复数），注册表得到空结果，
  表现为“只有物品动画、没有玩家动作”。

## 修复

- `HunterSpearPlayerAnimation` 改为读取资源文件后交给
  `AnimationSerializing`（内部 `GeckoLibSerializer`）解析，再由
  `KeyframeAnimationPlayer` 驱动原生 `ModifierLayer`。
- 保留库的度数→弧度、GeckoLib 轴向/坐标转换和缓动，不再手写骨骼采样。
- 动画层只输出 `torso` / `rightArm` / `leftArm`，头部保留原版视线朝向。
- `HunterSpearAssetContract` 新增解析契约，断言片段名、23 tick 长度及
  三段骨骼通道存在。

## 验证

- `compileJava`：通过。
- `verifyHunterSpearAssets`：通过。
- `check -x test`：全部离线契约通过。
- 离线解析结果：`name=hunter_spear_deploy endTick=23 returnToTick=23`，
  `torso` / `rightArm` / `leftArm` 通道齐全。
- 未验证：真实客户端第三人称手臂观感与多人延迟下的动作同步。
