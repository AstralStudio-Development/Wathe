# 滑动门动画实现计划

## 需求
- wathe 命名空间的门右键交互时，门向一侧滑动打开
- 5秒后门自动关闭，恢复原位和碰撞箱
- 动画需要平滑过渡

## 技术方案
使用 Block Display Entity 实现滑动动画

## 实现步骤

### 1. 创建 DoorAnimationManager 类
- 管理所有正在动画中的门
- 存储门的原始位置、状态、Display Entity ID
- 处理动画计时和自动关闭

### 2. 修改门交互逻辑
- 监听 CustomBlockInteractEvent
- 当玩家右键 wathe 门时：
  1. 将门方块设置为空气（移除碰撞箱）
  2. 在原位置生成 Block Display Entity 显示门模型
  3. 使用 transformation 和 interpolation_duration 实现滑动动画
  4. 5秒后恢复门方块，移除 Display Entity

### 3. Display Entity 动画参数
- interpolation_duration: 10 ticks (0.5秒动画)
- translation: 根据门的朝向计算滑动方向和距离

### 4. 需要处理的边界情况
- 玩家在门动画期间退出
- 服务器重启时清理残留实体
- 多个玩家同时交互同一扇门

## 文件修改
- 新建: `bukkit/src/main/java/dev/doctor4t/wathe/bukkit/door/DoorAnimationManager.java`
- 修改: `GameListener.java` - 门交互逻辑
- 修改: `WatheBukkit.java` - 注册 DoorAnimationManager
