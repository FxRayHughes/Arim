# UIConfigHelper 开发者文档

## 概述

UIConfigHelper 是一个基于 TabooLib 的 UI 配置助手类，提供声明式配置方式构建 Minecraft 箱子 UI，支持变量替换、Kether 脚本集成和动态内容更新。

## 核心概念

### 配置结构

```yaml
<rootPath>:          # 配置根路径
  layout: []         # UI布局定义
  button: {}         # 静态按钮配置（自动加载）
  <custom_icon>: {}  # 自定义图标配置
```

### 初始化

```kotlin
val helper = UIConfigHelper(
    configFile = File,      // 配置文件
    rootPath = String,      // 配置根路径
    player = Player         // 目标玩家（用于Kether上下文）
)
```

**快捷方式：**
```kotlin
UIConfigHelper.helper(player, file, "ui_root") { player ->
    // 在此上下文中 this 为 UIConfigHelper 实例
}
```

## API 参考

### 配置读取方法

| 方法 | 返回值 | 说明 |
|-----|--------|------|
| `getString(path, default?, placeholders?)` | `String?` | 获取字符串，支持颜色代码和变量替换 |
| `getChar(path, default?)` | `Char?` | 获取字符（常用于槽位标识） |
| `getStringList(path, default?)` | `List<String>` | 获取字符串列表，自动着色 |
| `getInt(path, default?)` | `Int` | 获取整数 |
| `getBoolean(path, default?)` | `Boolean` | 获取布尔值 |
| `getMaterial(path, default?)` | `XMaterial` | 获取材料，非法值返回默认值 |
| `getConfigurationSection(path)` | `ConfigurationSection?` | 获取配置节 |
| `getLayout()` | `List<String>` | 获取布局配置 |
| `getIconSlot(sectionPath)` | `Char?` | 获取图标的槽位标识字符 |

**路径规则：** 所有 `path` 参数均相对于 `rootPath`，自动拼接为 `rootPath.path`

### 槽位交互规则方法（ClickEvent 扩展）

| 方法 | 返回值 | 说明 |
|-----|--------|------|
| `ClickEvent.ruleConditionSlot(slotChar, condition, onFailed?)` | `Boolean` | 条件槽位：验证物品放入/取出条件 |
| `ClickEvent.ruleLimitAmount(slotChar, maxAmount, onFailed?)` | `Boolean` | 限制槽位最大堆叠数量 |
| `ClickEvent.ruleLockSlots(slotChars, reverse?)` | `Unit` | 锁定/解锁槽位交互 |
| `Inventory.returnItems(slotChars, delete?)` | `Unit` | 返还槽位物品到玩家背包 |

**参数说明：**
- `slotChar`/`slotChars`: 槽位字符标识
- `condition`: lambda `(放入?, 取出?) -> Boolean`
- `onFailed`: ClickEvent 上下文的失败回调 lambda
- `reverse`: true=仅指定槽位可交互，false=锁定指定槽位
- `delete`: 是否删除UI中的物品

### UI 构建方法

#### 1. 初始化菜单

```kotlin
player.openMenu<Chest>("标题") {
    initMenu()  // 必须调用：绑定 Chest、应用布局、加载 button 节点
}
```

**执行流程：**
1. 绑定 `Chest` 实例到 `helper.chest`
2. 应用 `layout` 配置
3. 自动加载 `button` 节点下所有按钮

#### 2. 构建图标

```kotlin
// 基础用法
val icon = buildIcon(
    sectionPath = "icon_name",
    placeholders = mapOf("key" to "value"),
    customizer = { config ->  // ItemBuilder 上下文
        name = config.getString("custom_name")
    }
)

// 自动绑定点击事件
buildIconAction(
    sectionPath = "icon_name",
    placeholders = emptyMap(),
    clickEvent = { section ->  // ClickEvent 上下文
        val custom = section.getString("custom")
        // 自定义处理逻辑
    }
)
```

**特性：**
- 支持 XItemStack 完整配置（参考 [TabooLib 文档](https://taboolib.feishu.cn/wiki/SXCIwINmsiubTJkJJu1cfh2on5b)）
- `shiny: true` 启用附魔光效
- 自动执行变量替换（`{key}` → `value`）
- 自动触发 `action` 配置的 Kether 脚本

#### 3. 翻页按钮

```kotlin
player.openMenu<PageableChest<T>>("标题") {
    initMenu()
    putPagination(this)  // 自动创建上/下页按钮
    elements { /* 数据源 */ }
}
```

**配置要求：**
```yaml
<root>:
  next_page:
    slot: "N"
    has:
      material: "SPECTRAL_ARROW"
      name: "&e&l下一页 ▶"
      lore:
        - "&7当前: &a{current_page}&7/&a{total_page}" #变量已经自动传递
    normal:
      material: "ARROW"
      name: "&7下一页 ▶"
      lore:
        - "&c已经是最后一页"
  previous_page:
    slot: "P"
    has:
      material: "SPECTRAL_ARROW"
      name: "&e&l◀ 上一页"
      lore:
        - "&7当前: &a{current_page}&7/&a{total_page}" #变量已经自动传递
    normal:
      material: "ARROW"
      name: "&7◀ 上一页"
      lore:
        - "&c已经是第一页"
```

### 动态更新方法

页面创建后通过 `Inventory` 扩展方法更新内容（仅用于 `onBuild`/`onClick`/`onClose` 回调）：

```kotlin
onBuild { player, inventory ->
    // 更新图标
    inventory.updateIcon(
        sectionPath = "player_status",
        placeholders = mapOf("level" to "99"),
        customizer = { config -> /* 自定义调整 */ }
    )

    // 获取槽位物品
    val item = inventory.getSlotItem('B')             // 首个非空物品
    val items = inventory.getSlotItems('C')           // 所有非空物品
    val first = inventory.getSlotItemsFirst('D')      // 首个槽位物品（可能为空气）

    // 返还物品
    inventory.returnItems(listOf('B', 'C'))          // 返还指定槽位物品到玩家背包
}
```

### 槽位交互规则方法

用于控制槽位的物品放入/取出行为，作为 ClickEvent 的扩展方法调用：

```kotlin
onClick { event, element ->
    // 规则：条件槽位 - 只允许钻石放入
    event.ruleConditionSlot('I', { put, out ->
        put == null || put.type == Material.DIAMOND
    }) {
        // onFailed 在 ClickEvent 上下文中执行
        clicker.sendMessage("§c只能放入钻石!")
        isCancelled = true
    }

    // 规则：限制堆叠数量
    event.ruleLimitAmount('I', 16) {
        clicker.sendMessage("§c最多只能放16个!")
    }

    // 规则：锁定槽位（禁止交互）
    event.ruleLockSlots(listOf('A', 'B'), reverse = false)
}
```

## 配置详解

### 布局配置 (layout)

字符映射系统，每个字符代表一个槽位类型：

```yaml
layout:
  - "AAAAAAAAA"  # 第1行（索引 0-8）
  - "ABBBBBBBA"  # 第2行（索引 9-17）
  - "AAAAAAAAA"  # 第3行（索引 18-26）
```

**规则：**
- 9列 × N行（普通箱子3行、大箱子6行）
- 字符与图标的 `slot` 属性对应
- 同一字符映射到多个槽位

### 图标配置 (XItemStack)

基于 TabooLib XItemStack 标准：

```yaml
icon_name:
  slot: 'X'                    # 槽位标识（对应 layout）
  material: DIAMOND            # 材料（XMaterial 枚举）
  amount: 1                    # 数量
  damage: 0                    # 耐久损失
  name: "&e名称"                # 显示名称
  lore: ["行1", "行2"]          # 物品描述
  enchants: ["DAMAGE_ALL:5"]   # 附魔（格式：附魔ID:等级）
  flags: ["HIDE_ENCHANTS"]     # 物品标记
  custom-model-data: 10001     # 自定义模型数据
  color: "255,0,0"             # 皮革装备颜色 (R,G,B)
  shiny: false                 # 附魔光效
  custom: "自定义数据"           # 扩展字段（需代码读取）
```

### 点击事件 (action)

使用 Kether 脚本处理点击事件：

```yaml
action:
  left_click: "tell '左键点击' colored"
  right_click: "actionbar '右键点击' colored"
  shift_left_click: "give diamond 1"
  shift_right_click: "command 'help'"
  middle_click: "tell '中键点击'"
  other_click: "tell '其他点击'"
```

**可用点击类型：** `left_click`, `right_click`, `shift_left_click`, `shift_right_click`, `middle_click`, `other_click`

**变量注入：**
- 配置中的 `{key}` 会被 `placeholders` 替换
- Kether 脚本接收 `player` 作为 sender
- Placeholders 注入到 Kether 变量上下文

### 按钮自动加载 (button)

`button` 节点下的所有子节点会被 `initMenu()` 自动加载：

```yaml
button:
  close_btn: { slot: 'C', material: BARRIER }
  info_btn: { slot: 'I', material: BOOK }
```

**等价代码：**
```kotlin
for (key in ["close_btn", "info_btn"]) {
    buildIconAction("button.$key")
}
```

## 使用场景

### 场景1：静态箱子 UI

```kotlin
UIConfigHelper.helper(player, configFile, "shop_ui") {
    player.openMenu<Chest>("商店") {
        initMenu()  // 自动加载 button 节点的所有商品按钮
    }
}
```

### 场景2：分页列表

```kotlin
UIConfigHelper.helper(player, configFile, "list_ui") {
    player.openMenu<PageableChest<Item>>("物品列表") {
        initMenu()
        putPagination(this)
        elements { itemList }
        onGenerate { _, item, _, _ -> item.toItemStack() }
    }
}
```

### 场景3：动态内容

```kotlin
UIConfigHelper.helper(player, configFile, "status_ui") {
    player.openMenu<Chest>("状态") {
        initMenu()
        buildIconAction("refresh_btn") {
            isCancelled = true
            clicker.closeInventory()
        }
        onBuild { _, inv ->
            inv.updateIcon("player_stats", mapOf(
                "level" to player.level.toString(),
                "exp" to player.exp.toString()
            ))
        }
    }
}
```

### 场景4：访问自定义配置

```kotlin
buildIconAction("custom_item") { section ->
    val customData = section.getString("custom")
    val extraValue = section.getInt("extra")
    // 使用自定义数据处理业务逻辑
}
```

### 场景5：槽位交互规则

```kotlin
UIConfigHelper.helper(player, configFile, "craft_ui") {
    player.openMenu<Chest>("工作台") {
        initMenu()

        onClick { event, _ ->
            // 只允许原材料放入槽位 'I'
            event.ruleConditionSlot('I', { put, _ ->
                put == null || put.type in listOf(Material.DIAMOND, Material.GOLD_INGOT)
            }) {
                clicker.sendMessage("§c只能放入钻石或金锭!")
            }

            // 限制输入槽最多16个
            event.ruleLimitAmount('I', 16) {
                clicker.sendMessage("§c最多放16个!")
            }

            // 锁定结果槽（只能取出，不能放入）
            event.ruleLockSlots(listOf('O'), reverse = false)
        }

        onClose {
            // 返还输入槽物品并清空
            it.inventory.returnItems(listOf('I'), delete = true)
        }
    }
}
```

## 注意事项

1. **初始化顺序：** 必须先调用 `initMenu()` 才能使用 `buildIconAction` 等方法
2. **动态更新限制：** `updateIcon` 等方法仅在页面创建后可用（`onBuild`/`onClick`/`onClose`）
3. **变量替换时机：** 在 `buildIcon` 和 `updateIcon` 时执行，不影响原始配置
4. **配置热重载：** 调用 `helper.reload()` 重新加载配置文件
5. **槽位冲突：** 同一字符映射多个槽位时，所有槽位显示相同图标
6. **Kether 脚本错误：** 脚本执行失败不会抛出异常，检查服务器日志

## 性能优化

- `config` 使用 `lazy` 延迟加载
- 变量替换使用简单字符串替换，避免正则开销

## 扩展阅读

- [TabooLib UI 系统](https://taboolib.feishu.cn/wiki/QcIqwTWudiMrkVkGgftcMUXqnCh)
- [XItemStack 配置](https://taboolib.feishu.cn/wiki/SXCIwINmsiubTJkJJu1cfh2on5b)
- [Kether 脚本语言](https://taboolib.feishu.cn/wiki/UHDGwNLwSiMTMNkJWUYcz4hdnVh)
