# Bukkit 版本开发计划

## 目标
在项目中新建独立的 Bukkit 插件模块，实现 Wathe 游戏的核心逻辑，使用原版物品替代自定义物品。

## 配置
- Paper 1.21.4+
- Java 21
- 单游戏实例
- 整个世界作为游戏区域
- 死亡后变为旁观者
- 无大厅系统

## 项目结构
```
bukkit/
├── build.gradle
├── settings.gradle
├── src/main/java/dev/doctor4t/wathe/bukkit/
│   ├── WatheBukkit.java              # 主类
│   ├── api/
│   │   ├── GameMode.java             # 游戏模式基类
│   │   ├── Role.java                 # 角色定义 (record)
│   │   └── WatheRoles.java           # 角色注册
│   ├── game/
│   │   ├── GameManager.java          # 游戏管理器
│   │   ├── GameConstants.java        # 游戏常量
│   │   └── MurderGameMode.java       # 谋杀模式
│   ├── player/
│   │   ├── GamePlayer.java           # 游戏玩家数据
│   │   ├── PlayerManager.java        # 玩家管理
│   │   ├── MoodManager.java          # 心情系统
│   │   ├── PoisonManager.java        # 毒药系统
│   │   ├── WallhackManager.java      # 透视系统
│   │   ├── CorpseManager.java        # 尸体系统
│   │   └── ShopManager.java          # 商店系统
│   ├── task/
│   │   ├── MoodTask.java             # 心情任务接口
│   │   ├── TaskType.java             # 任务类型枚举
│   │   ├── SleepTask.java            # 睡觉任务
│   │   ├── OutsideTask.java          # 外出任务
│   │   ├── EatTask.java              # 吃饭任务
│   │   └── DrinkTask.java            # 喝酒任务
│   ├── item/
│   │   ├── GameItems.java            # 游戏物品定义
│   │   └── ItemHandler.java          # 物品使用处理
│   ├── integration/
│   │   └── WathePlaceholderExpansion.java  # PlaceholderAPI支持
│   ├── command/
│   │   └── WatheCommand.java         # 游戏命令
│   └── listener/
│       ├── PlayerListener.java       # 玩家事件
│       └── GameListener.java         # 游戏事件
└── src/main/resources/
    └── plugin.yml
```

## 物品映射 (CraftEngine自定义物品)
| 游戏物品 | CraftEngine ID |
|---------|----------------|
| 刀 | wathe:knife |
| 左轮手枪 | wathe:revolver |
| 手榴弹 | FIRE_CHARGE (原版) |
| 撬锁器 | TRIPWIRE_HOOK (原版) |
| 撬棍 | IRON_HOE (原版) |
| 尸袋 | BLACK_WOOL (原版) |
| 毒药瓶 | POTION (原版) |
| 鞭炮 | FIREWORK_ROCKET (原版) |
| 笔记 | PAPER (原版) |

## 开发进度

### 阶段1: 基础框架 ✅
- [x] 创建 Bukkit 项目结构 (Gradle)
- [x] 实现主类和配置加载
- [x] 实现角色系统 (Role record, WatheRoles)
- [x] 实现游戏模式基类 (GameMode)

### 阶段2: 玩家系统 ✅
- [x] 实现 GamePlayer 数据类
- [x] 实现 PlayerManager 玩家管理
- [x] 实现心情系统基础 (MoodManager + BossBar)
- [x] 实现商店系统 (ShopManager + GUI)

### 阶段3: 游戏逻辑 ✅
- [x] 实现 GameManager 游戏管理
- [x] 实现 MurderGameMode 谋杀模式
- [ ] 实现 DiscoveryGameMode 探索模式 (待定)
- [ ] 实现 LooseEndsGameMode 残局模式 (待定)

### 阶段4: 物品和交互 ✅
- [x] 实现 GameItems 物品定义 (Paper DataComponent API)
- [x] 实现 ItemHandler 物品使用逻辑
- [x] 实现武器伤害和冷却
- [x] 集成 CraftEngine 自定义物品

### 阶段5: 命令和事件 ✅
- [x] 实现游戏命令 (start/stop/role/money/timer/shop)
- [x] 实现玩家事件监听
- [x] 实现游戏事件监听

### 阶段6: 扩展功能 🔄
- [x] 毒药系统 (PoisonManager)
- [x] 透视系统 (WallhackManager)
- [x] 尸体系统 (CorpseManager)
- [x] PlaceholderAPI 支持
- [x] MiniMessage 支持
- [x] BetterHud 依赖引入
- [x] **心情任务系统集成** - 任务生成、完成检测、BossBar显示
- [x] **精神病幻觉系统** (PsychosisManager) - 低心情时看到假物品
- [ ] 窗外山景移动效果 (Display Entity)

### 阶段7: 待完善 📋
- [x] 心情任务生成逻辑（权重随机、冷却时间）
- [x] 任务完成检测和奖励
- [x] 任务UI显示（BossBar）
- [x] 心情PAPI变量（用于CE自定义图片）
- [ ] 配置文件完善

## 命令列表
- `/wathe start` - 开始游戏
- `/wathe stop` - 停止游戏
- `/wathe role <玩家> <角色>` - 设置玩家角色
- `/wathe money <玩家> <数量>` - 设置玩家金币
- `/wathe timer <分钟> [秒]` - 设置游戏时间
- `/wathe shop` - 打开商店

## 依赖插件
- packetevents (软依赖)
- CraftEngine (软依赖) - 自定义物品
- PlaceholderAPI (软依赖) - 变量支持
- BetterHud (软依赖) - HUD显示

## 技术特性
- 使用 Paper 1.21.4 现代化 API
- Adventure Component API 替代 ChatColor
- MiniMessage 格式支持
- DataComponentTypes 替代 ItemMeta
- Record 类型用于不可变数据
- ConcurrentHashMap 用于线程安全
- GlobalRegionScheduler 用于 Folia 兼容
