# 窗外山景移动效果 - Display Entity 方案

## 需求
使用 Display Entity 实现列车窗外山景移动的视觉效果，模拟列车行驶。

## 技术方案

### 核心原理
1. 从世界中指定区域读取方块，转换为 Block Display Entity
2. 通过 Bukkit 的 Transformation API 实现平滑移动
3. 循环滚动：当一组山景移出视野后，传送回起点继续循环

### 实现架构

```
SceneryManager
├── 从世界区域加载方块为 Block Display 组
├── 控制移动速度和方向
├── 管理循环滚动逻辑
└── 与游戏状态联动（游戏开始/结束时启动/停止）
```

### 关键类

1. **SceneryManager** - 主管理器
   - `loadFromRegion(World, pos1, pos2, displayPos)` - 从世界区域加载山景
   - `startMoving(double speed)` - 开始移动
   - `stopMoving()` - 停止移动
   - `cleanup()` - 清理所有 Display Entity

2. **ScenerySegment** - 山景片段
   - 存储一组 Block Display Entity
   - 管理单个片段的位置和移动
   - 支持克隆用于循环

### 配置项
- `scenery.enabled` - 是否启用山景效果
- `scenery.speed` - 移动速度（格/tick）
- `scenery.template-region.world` - 模板区域所在世界
- `scenery.template-region.pos1` - 模板区域角1
- `scenery.template-region.pos2` - 模板区域角2
- `scenery.display-position` - Display Entity 生成位置
- `scenery.move-axis` - 移动轴向（X/Z）
- `scenery.segment-count` - 循环片段数量

### Display Entity 特性利用
- `interpolationDuration` - 设置插值时长实现平滑移动
- `Transformation` - 设置位置偏移
- 客户端自动补间，服务端只需定期更新目标位置

## 工作流程
1. 管理员在世界中建造山景模板
2. 配置模板区域坐标和显示位置
3. 游戏开始时，插件读取模板区域，生成 Block Display
4. 定时任务更新 Display Entity 位置，实现移动效果
5. 游戏结束时清理所有 Display Entity

## 依赖
- Paper 1.19.4+（Display Entity API）
- 无需 WorldEdit
