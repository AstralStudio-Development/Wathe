# 游戏开始黑屏过渡动画

## 需求
游戏开始时有一个黑屏的渐入渐出过渡动画。

## 技术方案
参考 ScreenEffects 插件，使用 Title（标题）来显示全屏效果：
1. 使用CE的FontImage创建全屏黑色图片字符
2. 用 `player.sendTitle()` 显示，利用title的fadein/stay/fadeout实现渐变
3. 用假的旁观者模式数据包隐藏玩家HUD

### 时间安排（总计约4秒）
- 渐入（fadein）：20 ticks（1秒）
- 黑屏保持（stay）：40 ticks（2秒）- 这期间显示角色信息
- 渐出（fadeout）：20 ticks（1秒）

### CE资源配置
需要创建一个全屏黑色的FontImage：
- ID: `wathe:fullscreen_black`
- 纹理: 全屏纯黑图片

## 实现步骤

### 1. 创建 BlackoutTransitionManager 类
- 管理黑屏过渡动画
- `startTransition(Player player, int fadein, int stay, int fadeout)` - 开始过渡动画
- `startTransitionForAll(List<Player> players)` - 为所有玩家开始过渡
- `hideHUD(Player player)` - 隐藏HUD（发送假旁观者模式数据包）
- `showHUD(Player player)` - 恢复HUD

### 2. 动画流程
1. 隐藏玩家HUD
2. 发送带有fadein/stay/fadeout的Title
3. 在stay期间显示角色信息
4. fadeout结束后恢复HUD

### 3. 集成到游戏流程
- 在 MurderGameMode.initialize() 中调用
- 角色分配和物品发放在黑屏期间完成

## 文件修改
- 新建: `bukkit/src/main/java/dev/doctor4t/wathe/bukkit/game/BlackoutTransitionManager.java`
- 修改: `WatheBukkit.java` - 注册 BlackoutTransitionManager
- 修改: `MurderGameMode.java` - 游戏开始时调用过渡动画

## 依赖
- CraftEngine FontImage（全屏黑色图片）
- PacketEvents（发送假游戏模式数据包）
