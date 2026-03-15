# Wathe 到 CraftEngine 迁移计划

## 概述
将 Wathe Fabric 模组中的所有方块和物品迁移到 CraftEngine 插件配置包中。

## 源数据分析

### 方块分类 (来自 WatheBlocks.java)

#### 1. 金属方块 (Metallic blocks)
- **Tarnished Gold 系列**: tarnished_gold, tarnished_gold_stairs, tarnished_gold_slab, tarnished_gold_wall, tarnished_gold_pillar
- **Gold 系列**: gold, gold_stairs, gold_slab, gold_wall, gold_pillar
- **Pristine Gold 系列**: pristine_gold, pristine_gold_stairs, pristine_gold_slab, pristine_gold_wall, pristine_gold_pillar
- **White Hull 系列**: white_hull, white_hull_stairs, white_hull_slab, white_hull_wall, culling_white_hull
- **Black Hull 系列**: black_hull, black_hull_stairs, black_hull_slab, black_hull_wall, culling_black_hull, black_hull_sheets, black_hull_sheet_stairs, black_hull_sheet_slab, black_hull_sheet_wall
- **Gold Bar/Ledge**: gold_bar, gold_ledge
- **Metal Sheet 系列**: metal_sheet, metal_sheet_stairs, metal_sheet_slab, metal_sheet_wall, metal_sheet_walkway, metal_sheet_door, cockpit_door
- **Stainless Steel 系列**: stainless_steel, stainless_steel_stairs, stainless_steel_slab, stainless_steel_wall, stainless_steel_walkway, stainless_steel_branch, stainless_steel_pillar, stainless_steel_bar, rail_beam
- **Dark Steel 系列**: dark_steel, dark_steel_stairs, dark_steel_slab, dark_steel_wall, dark_steel_walkway, dark_steel_branch, dark_steel_pillar

#### 2. 门 (Doors)
- small_glass_door, small_wood_door

#### 3. 花式钢材 (Fancy steel)
- **Anthracite Steel**: anthracite_steel, anthracite_steel_panel, anthracite_steel_tiles, anthracite_steel_tiles_panel, smooth_anthracite_steel, smooth_anthracite_steel_stairs, smooth_anthracite_steel_slab, smooth_anthracite_steel_panel, smooth_anthracite_steel_wall, anthracite_steel_door
- **Khaki Steel**: khaki_steel, khaki_steel_panel, khaki_steel_tiles, khaki_steel_tiles_panel, smooth_khaki_steel, smooth_khaki_steel_stairs, smooth_khaki_steel_slab, smooth_khaki_steel_panel, smooth_khaki_steel_wall, khaki_steel_door
- **Maroon Steel**: maroon_steel, maroon_steel_panel, maroon_steel_tiles, maroon_steel_tiles_panel, smooth_maroon_steel, smooth_maroon_steel_stairs, smooth_maroon_steel_slab, smooth_maroon_steel_panel, smooth_maroon_steel_wall, maroon_steel_door
- **Muntz Steel**: muntz_steel, muntz_steel_panel, muntz_steel_tiles, muntz_steel_tiles_panel, smooth_muntz_steel, smooth_muntz_steel_stairs, smooth_muntz_steel_slab, smooth_muntz_steel_panel, smooth_muntz_steel_wall, muntz_steel_door
- **Navy Steel**: navy_steel, navy_steel_panel, navy_steel_tiles, navy_steel_tiles_panel, smooth_navy_steel, smooth_navy_steel_stairs, smooth_navy_steel_slab, smooth_navy_steel_panel, smooth_navy_steel_wall, navy_steel_door

#### 4. 玻璃 (Glass)
- hull_glass, rhombus_hull_glass, rhombus_glass, golden_glass_panel, privacy_glass_panel, culling_glass

#### 5. 石材 (Stones)
- **Marble 系列**: marble, marble_stairs, marble_slab, marble_wall, marble_mosaic, marble_tiles, marble_tile_stairs, marble_tile_slab, marble_tile_wall
- **Dark Marble 系列**: dark_marble, dark_marble_stairs, dark_marble_slab, dark_marble_wall

#### 6. 地毯 (Carpets)
- red_moquette, brown_moquette, blue_moquette

#### 7. 木材 (Woods)
- **Mahogany 系列**: mahogany_planks, mahogany_stairs, mahogany_slab, mahogany_herringbone, mahogany_herringbone_stairs, mahogany_herringbone_slab, smooth_mahogany, smooth_mahogany_stairs, smooth_mahogany_slab, mahogany_panel, mahogany_cabinet, mahogany_bookshelf
- **Bubinga 系列**: bubinga_planks, bubinga_stairs, bubinga_slab, bubinga_herringbone, bubinga_herringbone_stairs, bubinga_herringbone_slab, smooth_bubinga, smooth_bubinga_stairs, smooth_bubinga_slab, bubinga_panel, bubinga_cabinet, bubinga_bookshelf
- **Ebony 系列**: ebony_planks, ebony_stairs, ebony_slab, ebony_herringbone, ebony_herringbone_stairs, ebony_herringbone_slab, smooth_ebony, smooth_ebony_stairs, smooth_ebony_slab, ebony_panel, ebony_cabinet, trimmed_ebony_stairs, ebony_bookshelf

#### 8. 通风管道 (Vents)
- stainless_steel_vent_shaft, stainless_steel_vent_hatch, dark_steel_vent_hatch, tarnished_gold_vent_hatch, dark_steel_vent_shaft, tarnished_gold_vent_shaft

#### 9. 家具/装饰 (Furniture/Decor)
- stainless_steel_ladder
- **树枝系列**: oak_branch, spruce_branch, birch_branch, jungle_branch, acacia_branch, dark_oak_branch, mangrove_branch, cherry_branch, bamboo_pole, crimson_stipe, warped_stipe (及其stripped版本)
- **栏杆**: trimmed_railing_post, diagonal_trimmed_railing, trimmed_railing
- panel_stripes, cargo_box
- **沙发**: white_lounge_couch, white_ottoman, blue_lounge_couch, green_lounge_couch, red_leather_couch, brown_leather_couch, beige_leather_couch
- coffee_table, bar_table, bar_stool
- **床**: white_trimmed_bed, red_trimmed_bed
- horn

#### 10. 灯具 (Lamps)
- trimmed_lantern, wall_lamp, neon_pillar, neon_tube

#### 11. 按钮/装饰
- small_button, elevator_button, stainless_steel_sprinkler, gold_sprinkler, gold_ornament

#### 12. 轮子 (Wheels)
- wheel, rusted_wheel

#### 13. 餐具 (Platters)
- food_platter, drink_tray, chimney

#### 14. 操作员方块 (Op)
- barrier_panel, light_barrier

### 物品分类 (来自 WatheItems.java)
- key, lockpick, knife, bat, crowbar, grenade, thrown_grenade, firecracker
- revolver, derringer, body_bag, letter, blackout, psycho_mode
- poison_vial, scorpion
- **鸡尾酒**: old_fashioned, mojito, martini, cosmopolitan, champagne
- note

## 迁移策略

### 目标结构
```
LeavesMC 1.21.4/plugins/CraftEngine/resources/wathe/
├── pack.yml
├── configuration/
│   ├── categories.yml
│   ├── lang.yml
│   ├── templates/
│   │   ├── block_settings.yml
│   │   └── block_states.yml
│   ├── blocks/
│   │   ├── metallic/
│   │   ├── glass/
│   │   ├── stone/
│   │   ├── wood/
│   │   ├── furniture/
│   │   └── ...
│   └── items/
│       └── equipment.yml
└── resourcepack/
    └── assets/
        └── minecraft/
            ├── models/
            │   ├── block/custom/wathe/
            │   └── item/custom/wathe/
            └── textures/
                ├── block/custom/wathe/
                └── item/custom/wathe/
```

## 待确认问题

1. **纹理迁移**: 是否需要将wathe的纹理文件复制到CE的resourcepack目录？
2. **模型生成**: 是否使用CE的模型生成功能还是直接复制wathe的模型文件？
3. **方块行为**: 一些特殊方块（如门、沙发、通风管道）在CE中如何实现其特殊行为？
4. **物品行为**: 武器、工具等物品的特殊功能是否需要迁移？还是只迁移外观？
5. **命名空间**: 使用 `wathe` 还是其他命名空间？

## 实施步骤

1. ✅ 创建wathe包基础结构 (pack.yml)
2. ⏳ 创建模板文件（复用default包的模板）
3. ✅ 迁移简单方块（cube_all类型）- 部分完成
4. ⏳ 迁移楼梯/台阶/墙壁方块
5. ⏳ 迁移柱状方块
6. ⏳ 迁移特殊方块（门、家具等）
7. ✅ 迁移物品 (19/19 完成)
8. ✅ 复制纹理文件
9. ✅ 创建语言文件 (lang.yml)
10. ⏳ 创建分类文件

## 当前进度

### 已完成
- [x] pack.yml 基础配置
- [x] 19个装备物品配置 (equipment.yml) - 包含5个鸡尾酒
- [x] 物品模型 JSON 文件
- [x] 物品纹理 PNG 文件
- [x] 方块纹理 PNG 文件 (全部复制)
- [x] 语言文件 (中英文)
- [x] 简单方块配置 - metallic.yml (9个方块)
- [x] 简单方块配置 - fancy_steel.yml (15个方块)
- [x] 简单方块配置 - stone_carpet.yml (7个方块: marble, dark_marble, marble_tiles, marble_mosaic + 3地毯)
- [x] 简单方块配置 - wood.yml (15个方块: 12原有 + 3柜子)
- [x] 简单方块配置 - glass.yml (4个方块: hull_glass, rhombus_hull_glass, rhombus_glass, culling_glass)
- [x] Panel方块配置 - panels.yml (5个: golden_glass_panel, privacy_glass_panel, mahogany_panel, bubinga_panel, ebony_panel)
  - 使用trapdoor作为base state实现薄碰撞箱
- [x] 灯光方块配置 - neon.yml (neon_pillar, neon_tube) - 简化版本，不支持连锁切换
- [x] 家具方块配置 - furniture.yml (12个方块: 6沙发 + 4其他家具 + 2床)
  - 沙发使用 sofa_block behavior，支持自动连接
  - 床使用 bed_block behavior，支持睡眠功能
  - 床模型已调整尺寸盖过原版床，床头旋转已修正
- [x] GUI图片资源迁移 - images.yml (27个图片)
  - GUI sprites: note, shop_slot系列 (5个)
  - HUD sprites: arrows, bat/knife indicators, crosshair, hotbar, mood系列 (21个)
  - Container: limited_inventory
  - Game UI: game.png (大型精灵图)
- [x] 音效资源迁移 - sounds.json + 43个ogg文件
  - ambient: blackout, psycho_drone, train_horn, train_inside, train_outside
  - block: cargo_box, door, light, privacy_panel, space_button, sprinkler, vent_shaft
  - item: bat, crowbar, derringer, grenade, key, knife, lockpick, psycho, revolver
  - ui: piano系列, riser, shop_buy系列
- [x] 楼梯方块配置 - stairs.yml (26个楼梯) - 完成
  - 木材: ebony, smooth_mahogany, smooth_ebony, bubinga, mahogany, mahogany_herringbone, bubinga_herringbone, smooth_bubinga, ebony_herringbone
  - 金属: gold, tarnished_gold, pristine_gold, white_hull, black_hull, black_hull_sheet, metal_sheet, stainless_steel, dark_steel
  - 花式钢材: smooth_anthracite_steel, smooth_khaki_steel, smooth_maroon_steel, smooth_muntz_steel, smooth_navy_steel
  - 石材: marble, marble_tile, dark_marble
- [x] 台阶方块配置 - slabs.yml (26个台阶) - 完成
  - 木材: marble, mahogany, smooth_mahogany, ebony, smooth_ebony, bubinga, mahogany_herringbone, bubinga_herringbone, smooth_bubinga, ebony_herringbone
  - 金属: gold, tarnished_gold, pristine_gold, white_hull, black_hull, black_hull_sheet, metal_sheet, stainless_steel, dark_steel
  - 花式钢材: smooth_anthracite_steel, smooth_khaki_steel, smooth_maroon_steel, smooth_muntz_steel, smooth_navy_steel
  - 石材: marble_tile, dark_marble
### 进行中
- [ ] 特殊方块迁移

### 待完成
- [x] 门方块 (9个完成)
  - 已完成: metal_sheet_door, cockpit_door (使用door_block behavior，标准门贴图)
  - 已完成: small_glass_door, small_wood_door, 5个fancy_steel_door (使用自定义模型文件，64x64 entity贴图格式，正反面不镜像)
- [ ] Branch方块 (24个) - 六向连接方块
  - 木材: oak_branch, spruce_branch, birch_branch, jungle_branch, acacia_branch, dark_oak_branch, mangrove_branch, cherry_branch, bamboo_pole, crimson_stipe, warped_stipe
  - 金属: stainless_steel_branch, dark_steel_branch
  - Stripped版本: 11个
- [x] Vent方块 (6个) - 通风管道
  - Shaft: stainless_steel_vent_shaft, dark_steel_vent_shaft, tarnished_gold_vent_shaft (使用原木作为base state，3向放置)
  - Hatch: stainless_steel_vent_hatch, dark_steel_vent_hatch, tarnished_gold_vent_hatch (使用trapdoor_block behavior，像原版活板门一样开关)
- [ ] 其他特殊方块
  - rail_beam (轨道梁) - horizontal_axis属性
  - panel_stripes (条纹面板) - horizontal_axis属性
  - cargo_box (货箱) - facing + open属性
  - trimmed_railing (栏杆) - 复杂多方块系统
  - small_button, elevator_button (按钮) - wall_mounted + powered属性
  - stainless_steel_sprinkler, gold_sprinkler (喷头) - wall_mounted + powered属性
  - gold_ornament (装饰) - facing + shape属性
  - trimmed_lantern, wall_lamp (灯具) - facing + lit + active属性
  - wheel, rusted_wheel (轮子) - 实体渲染方块
  - food_platter, drink_tray, chimney (餐具/烟囱)
  - horn (喇叭) - 实体渲染方块
  - culling_white_hull, culling_black_hull (剔除方块)
  - barrier_panel, light_barrier (OP方块)

### 已完成
- [x] 分类文件 (categories.yml) - 8个分类
  - wathe:wathe (主分类)
  - wathe:metallic (金属方块)
  - wathe:fancy_steel (花式钢材)
  - wathe:glass (玻璃)
  - wathe:stone (石材与地毯)
  - wathe:wood (木材)
  - wathe:furniture (家具)
  - wathe:lighting (灯具)
  - wathe:equipment (装备)

### 已完成
- [x] trimmed_ebony_stairs (特殊楼梯，简化版本 - 仅支持旋转，不支持自动连接)
- [x] 柱状方块 - pillars.yml (5个: tarnished_gold_pillar, gold_pillar, pristine_gold_pillar, stainless_steel_pillar, dark_steel_pillar)
- [x] Panel方块 (薄板) - 使用trapdoor作为base state
  - panels.yml: golden_glass_panel, privacy_glass_panel, mahogany_panel, bubinga_panel, ebony_panel (5个)
  - panels_fancy_steel.yml: 花式钢材panel (15个)
    - Anthracite系列: anthracite_steel_panel, anthracite_steel_tiles_panel, smooth_anthracite_steel_panel
    - Khaki系列: khaki_steel_panel, khaki_steel_tiles_panel, smooth_khaki_steel_panel
    - Maroon系列: maroon_steel_panel, maroon_steel_tiles_panel, smooth_maroon_steel_panel
    - Muntz系列: muntz_steel_panel, muntz_steel_tiles_panel, smooth_muntz_steel_panel
    - Navy系列: navy_steel_panel, navy_steel_tiles_panel, smooth_navy_steel_panel
- [x] 墙壁方块 - 使用fence_block behavior (17个) - 注意：自动连接功能暂不工作
  - walls_metal.yml: tarnished_gold_wall, gold_wall, pristine_gold_wall, white_hull_wall, black_hull_wall, black_hull_sheet_wall, metal_sheet_wall, stainless_steel_wall, dark_steel_wall (9个)
  - walls_fancy_steel.yml: smooth_anthracite_steel_wall, smooth_khaki_steel_wall, smooth_maroon_steel_wall, smooth_muntz_steel_wall, smooth_navy_steel_wall (5个)
  - walls_stone.yml: marble_wall, marble_tile_wall, dark_marble_wall (3个)
- [x] Walkway方块 - walkways.yml (3个: metal_sheet_walkway, stainless_steel_walkway, dark_steel_walkway)
- [x] Bar方块 - bars.yml (2个: gold_bar, stainless_steel_bar)
- [x] Ledge方块 - ledge.yml (1个: gold_ledge)
- [x] Ladder方块 - ladder.yml (1个: stainless_steel_ladder)
