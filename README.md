# XY2407 NSUK Addition

**NeoForge 1.21.1** — New:Sim-U-Kraft 拓展模组，为 Sim-U-Kraft 添加养殖、采矿、城市升级、自动补货、矿脉系统等丰富功能。

| 属性        | 值                      |
| --------- | ---------------------- |
| **模组 ID** | `xy2407_nsuk_addition` |
| **平台**    | NeoForge 1.21.1        |
| **作者**    | xingying2407           |
| **许可**    | GPL-3.0                |

***

## 功能特性

### 养殖系统 (Breeding)

- **养殖控制箱** — 管理动物/鱼类繁殖的多方块控制器
- **鱼类繁殖支持** — 兼容 LetFishLove Reborn、Tide、Aquaculture 的鱼类繁殖
- **鱼卵方块** — 动态鱼卵方块，支持多种鱼类
- **繁殖定义加载器** — 通过 JSON 配置文件自定义繁殖配方
- **建筑部署** — 自动部署鱼塘/养殖场建筑包

### 采矿系统 (Mining)

- **采矿控制箱** — 多方块采矿控制器
- **采矿菜单** — 完整的 GUI 菜单（含 LDLib2 UI）
- **玩家手动采矿** — 手动采矿功能支持

### 矿脉系统 (Ore Vein)

- **矿脉分布** — 世界生成时自然分布矿脉
- **矿脉发现** — 玩家可发现并登记矿脉
- **矿脉高亮** — Xaero's World Map 兼容的矿脉高亮显示
- **富铁矿石渲染** — 富铁矿 overlays 渲染
- **掉落处理** — 矿脉特有的掉落物处理

### 城市升级 (City Upgrade)

- **城市等级系统** — 支持多级城市升级
- **升级需求** — 人口、资源、资金等条件检查
- **升级面板** — 替换原版 Sim-U-Kraft 升级界面
- **核心迁移** — 支持移动城市核心位置

### 自动补货 (Auto Restock)

- **工业控制箱自动补货** — 自动补充工业控制箱的原料/燃料
- **商业控制箱自动补货** — 自动补货商业控制箱的商品
- **SQLite 持久化** — 补货配置持久化存储

### 农田/作物扩展 (Farmland & Crops)

- **高杆作物支持** — 支持甘蔗、竹子等高杆作物
- **藤蔓/灌木** — 葡萄藤、葡萄灌木等
- **水稻** — 水稻种植支持
- **右键收获** — 一键右键收割兼容作物
- **多方块作物支持** — Farmers Delight、Farm & Charm、Cultural Delights、Kaleidoscope Cookery 等模组作物兼容

### 侧边栏 HUD (Sidebar)

- **信息侧边栏** — 可配置的屏幕侧边信息显示
- **城市数据面板** — 显示城市人口、资金等统计数据
- **建筑统计** — 各类建筑的统计数据
- **弹出动画** — 平滑的 UI 弹出动画

### 市民装备 (Citizen Equipment)

- **市民装备系统** — 为市民分配装备/工具
- **装备 GUI** — 专用的装备管理界面
- **装备命令** — 服务端装备管理命令

### 容器角色 (Container Role)

- **容器角色查询** — 查询容器角色信息
- **HUD 显示** — 角色信息 HUD 叠加层

### 旅游与移民 (Tourism & Immigration)

- **村庄旅游** — 旅游系统支持
- **小镇移民** — 移民管理服务
- **移民 GUI** — 移民操作界面

### 建筑系统 (Building)

- **建筑包部署服务** — 更灵活的建筑包部署机制
- **建筑任务管理** — 建筑任务跟踪与控制

### 酿酒厂 (Winery)

- **多方块酿酒厂** — 工业控制箱酿造多种酒类
- **多品种酒类** — 葡萄酒、冰酒、蜂蜜酒、梅酒、甜浆果酒、朗姆酒、樱花葡萄酒等
- **Kaleidoscope Tavern 兼容** — 使用 KT 酒类物品作为产物

### 兼容性整合

- **Xaero's World Map** — 矿脉高亮、世界地图标记
- **LetFishLove Reborn** — 鱼类繁殖、鱼卵孵化
- **Tide** — Tide 鱼类生态兼容
- **Aquaculture** — 水产养殖兼容
- **LDLib2** — 现代化 GUI 框架
- **Vinery** — 葡萄作物兼容（红/白/热带/针叶林葡萄）
- **Kaleidoscope Tavern** — 酒类物品、葡萄藤作物兼容
- **多种农业模组** — Farmers Delight、Farm & Charm、Cultural Delights 等

***

## 依赖

### 必需

| 模组              | 版本         | 链接                                                             |
| --------------- | ---------- | -------------------------------------------------------------- |
| NeoForge | ≥ 21.1.234 | https://neoforged.net/ |
| New:Sim-U-Kraft | ≥ 2.0.1 | https://modrinth.com/mod/new-sim-u-kraft |
| LDLib2 | ≥ 2.2.26 | https://www.curseforge.com/minecraft/mc-mods/ldlib |

### 可选

| 模组                   | 用途     | 链接                                                                  |
| -------------------- | ------ | ------------------------------------------------------------------- |
| Xaero's World Map    | 矿脉高亮显示 | <https://www.curseforge.com/minecraft/mc-mods/xaeros-world-map>     |
| LetFishLove Reborn   | 鱼类繁殖兼容 | <https://www.curseforge.com/minecraft/mc-mods/let-fish-love-reborn>   |
| Tide                 | 鱼类生态兼容 | <https://www.curseforge.com/minecraft/mc-mods/tide>                 |
| Aquaculture          | 水产养殖兼容 | <https://www.curseforge.com/minecraft/mc-mods/aquaculture>          |
| Farmers Delight      | 作物兼容   | <https://www.curseforge.com/minecraft/mc-mods/farmers-delight>      |
| Farm & Charm         | 作物兼容   | <https://www.curseforge.com/minecraft/mc-mods/lets-do-farm-charm>           |
| Cultural Delights    | 作物兼容   | <https://www.curseforge.com/minecraft/mc-mods/cultural-delights>    |
| Vinery               | 葡萄作物兼容 | <https://www.curseforge.com/minecraft/mc-mods/lets-do-vinery>        |
| Kaleidoscope Tavern  | 酿酒厂配方   | <https://www.curseforge.com/minecraft/mc-mods/kaleidoscope-tavern>   |
| Kaleidoscope Cookery | 作物兼容   | <https://www.curseforge.com/minecraft/mc-mods/kaleidoscope-cookery> |

***

## 鸣谢

- **[New:Sim-U-Kraft](https://www.curseforge.com/minecraft/mc-mods/new-sim-u-kraft)** — Sim-U-Kraft 模组团队，为本模组提供 API 和运行平台
- **[NeoForge](https://neoforged.net/)** — Minecraft 模组加载平台
- **[LDLib2](https://www.curseforge.com/minecraft/mc-mods/ldlib)** — GUI 框架支持
- **[Xaero](https://www.curseforge.com/minecraft/mc-mods/xaeros-world-map)** — Xaero's World Map 地图兼容
- **[LetFishLove Reborn](https://www.curseforge.com/minecraft/mc-mods/letfishlove-reborn)** — 鱼类繁殖系统兼容
- **[Tide](https://www.curseforge.com/minecraft/mc-mods/tide)** — 鱼类生态兼容
- **[Aquaculture](https://www.curseforge.com/minecraft/mc-mods/aquaculture)** — 水产养殖兼容
- **[Farmers Delight](https://www.curseforge.com/minecraft/mc-mods/farmers-delight)** — 农业兼容
- **[Farm & Charm](https://www.curseforge.com/minecraft/mc-mods/lets-do-farm-charm)** — 农业兼容
- **[Cultural Delights](https://www.curseforge.com/minecraft/mc-mods/cultural-delights)** — 农业兼容
- **[Vinery](https://www.curseforge.com/minecraft/mc-mods/lets-do-vinery)** — 葡萄作物兼容
- **[Kaleidoscope Tavern](https://www.curseforge.com/minecraft/mc-mods/kaleidoscope-tavern)** — 酿酒厂配方与葡萄藤作物兼容
- **[Kaleidoscope Cookery](https://www.curseforge.com/minecraft/mc-mods/kaleidoscope-cookery)** — 农业兼容
- **[Minecraft Comes Alive (MCA)](https://github.com/Luke100000/minecraft-comes-alive)** — 女性市民胸部渲染模型参考（GPL-3.0）
- **[Minecraft Forge / NeoForged 社区](https://discord.neoforged.net/)** — 技术支持和开发资源
- **[ParchmentMC](https://parchmentmc.org/)** — 混淆映射表（Mojang mappings 参数名补全）
- **[Taffy](https://github.com/vfyjxf/taffy)** — LDLib2 布局引擎
- **[Yoga](https://github.com/AppliedEnergistics/Yoga)** — LDLib2 布局引擎参考实现

***

## 许可

本项目基于 **GNU General Public License v3.0** 开源。
