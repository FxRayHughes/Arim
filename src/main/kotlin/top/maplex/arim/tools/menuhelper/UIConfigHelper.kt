package top.maplex.arim.tools.menuhelper

import getProperty
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import taboolib.common.io.newFile
import taboolib.common.platform.function.adaptPlayer
import taboolib.common.platform.function.getDataFolder
import taboolib.library.configuration.ConfigurationSection
import taboolib.library.xseries.XItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.chat.colored
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.Type
import taboolib.module.kether.KetherShell
import taboolib.module.kether.ScriptOptions
import taboolib.module.nms.ifAir
import taboolib.module.ui.ClickEvent
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.ItemBuilder
import taboolib.platform.util.buildItem
import taboolib.platform.util.giveItem
import java.io.File

/**
 * UI配置助手类
 * 提供通用的UI配置读取和构建功能，支持自定义配置文件
 *
 * @param configFile 配置文件
 * @param rootPath 配置根路径（如 "intro_ui", "active_ui"）
 */
class UIConfigHelper(val configFile: File, val rootPath: String, val player: Player) {
    lateinit var chest: Chest

    val config: Configuration by lazy {
        Configuration.loadFromFile(configFile, type = Type.YAML)
    }

    /**
     * 获取字符串配置
     * @param path 配置路径（相对于rootPath）
     * @param default 默认值
     * @param placeholders 变量替换映射
     */
    fun getString(
        path: String, default: String? = null, placeholders: Map<String, String> = emptyMap()
    ): String? {
        val fullPath = "$rootPath.$path"
        var value = config.getString(fullPath) ?: default ?: return null

        // 执行变量替换
        placeholders.forEach { (key, replacement) ->
            value = value.replace("{$key}", replacement)
        }

        return value.colored()
    }

    /**
     * 获取字符配置（通常用于槽位标识）
     * @param path 配置路径（相对于rootPath）
     * @param default 默认值
     */
    fun getChar(path: String, default: Char? = null): Char? {
        val fullPath = "$rootPath.$path"
        return config.getString(fullPath)?.firstOrNull() ?: default
    }

    /**
     * 获取字符串列表配置
     */
    fun getStringList(path: String, default: List<String> = emptyList()): List<String> {
        val fullPath = "$rootPath.$path"
        val list = config.getStringList(fullPath).takeIf { it.isNotEmpty() } ?: default
        return list.map { it.colored() }
    }

    /**
     * 获取整数配置
     */
    fun getInt(path: String, default: Int = 0): Int {
        val fullPath = "$rootPath.$path"
        return config.getInt(fullPath, default)
    }

    /**
     * 获取布尔配置
     */
    fun getBoolean(path: String, default: Boolean = false): Boolean {
        val fullPath = "$rootPath.$path"
        return config.getBoolean(fullPath, default)
    }

    /**
     * 获取材料配置
     */
    fun getMaterial(path: String, default: XMaterial = XMaterial.STONE): XMaterial {
        val materialName = getString(path) ?: return default
        return try {
            XMaterial.valueOf(materialName)
        } catch (e: IllegalArgumentException) {
            default
        }
    }

    /**
     * 获取配置节
     */
    fun getConfigurationSection(path: String): ConfigurationSection? {
        val fullPath = "$rootPath.$path"
        return config.getConfigurationSection(fullPath)
    }

    /**
     * 构建物品栈 - XItemStack
     * XItemStack: https://taboolib.feishu.cn/wiki/SXCIwINmsiubTJkJJu1cfh2on5b
     * @param sectionPath 配置节路径
     * @param placeholders 变量替换映射（key 格式：$xxx）
     * @param customizer 自定义构建器
     */
    fun buildIcon(
        sectionPath: String,
        placeholders: Map<String, String> = emptyMap(),
        customizer: (ItemBuilder.(config: ConfigurationSection) -> Unit) = {}
    ): ItemStack {
        val section = getConfigurationSection(sectionPath) ?: return XMaterial.AIR.parseItem()!!

        // 将 ConfigurationSection 转为 Map
        val itemMap = section.getValues(true).toMutableMap()

        // 按键长度降序排序，从长到短替换（避免短占位符先被替换导致长占位符匹配失败）
        val sortedPlaceholders = placeholders.entries.sortedByDescending { it.key.length }

        // 替换 material 字段的占位符
        val materialValue = itemMap["material"] as? String ?: "STONE"
        val replacedMaterial = sortedPlaceholders.fold(materialValue) { acc, (key, value) ->
            acc.replace(key, value)
        }
        itemMap["material"] = replacedMaterial

        // 使用 XItemStack.deserialize(Map, translator)
        val itemStack = XItemStack.deserialize(itemMap) {
            // 处理其他字段的占位符（name, lore 等），从长到短替换
            sortedPlaceholders.fold(it) { acc, (key, value) ->
                acc.replace(key, value)
            }
        }

        return buildItem(itemStack) {
            if (section.getBoolean("shiny", false)) {
                shiny()
            }
            customizer.invoke(this, section)
            colored()
        }
    }

    /**
     * 构建图标并绑定点击事件
     * @param sectionPath 配置节路径（相对于rootPath）
     * @param placeholders 变量替换映射
     * @param clickEvent 点击事件回调，接收配置节作为参数
     */
    fun Chest.buildIconAction(
        sectionPath: String,
        placeholders: Map<String, String> = emptyMap(),
        clickEvent: ClickEvent.(root: ConfigurationSection) -> Unit = {}
    ) {
        set(getChar("$sectionPath.slot", 'O')!!, buildIcon(sectionPath, placeholders)) {
            val section = getConfigurationSection(sectionPath) ?: return@set
            runIcon(clickEvent().click, sectionPath, placeholders)
            clickEvent.invoke(this, section)
        }
    }

    /**
     * 运行图标的Kether脚本回调
     * @param clickType 点击类型（左键/右键/中键等）
     * @param sectionPath 配置节路径
     * @param placeholders 变量替换映射，会注入到Kether脚本上下文
     */
    fun runIcon(clickType: ClickType, sectionPath: String, placeholders: Map<String, String> = emptyMap()) {
        val section = getConfigurationSection(sectionPath) ?: return
        val action = section.getConfigurationSection("action")
        val script = when (clickType) {
            ClickType.LEFT -> action?.getString("left_click")
            ClickType.RIGHT -> action?.getString("right_click")
            ClickType.SHIFT_LEFT -> action?.getString("shift_left_click")
            ClickType.SHIFT_RIGHT -> action?.getString("shift_right_click")
            ClickType.MIDDLE -> action?.getString("middle_click")
            else -> action?.getString("other_click")
        } ?: return
        val build = ScriptOptions.builder().sender(sender = adaptPlayer(player)).vars(placeholders).build()
        KetherShell.eval(script, build)
    }

    /**
     * 在页面打开后更新图标
     * @param sectionPath 配置节路径
     * @param placeholders 变量替换映射（key 格式：$xxx）
     * @param customizer 自定义构建器，接收配置节用于动态调整物品属性
     */
    fun Inventory.updateIcon(
        sectionPath: String,
        placeholders: Map<String, String> = emptyMap(),
        customizer: (ItemBuilder.(config: ConfigurationSection) -> Unit) = {}
    ) {
        val section = getConfigurationSection(sectionPath) ?: return

        // 将 ConfigurationSection 转为 Map
        val itemMap = section.getValues(true).toMutableMap()

        // 按键长度降序排序，从长到短替换（避免短占位符先被替换导致长占位符匹配失败）
        val sortedPlaceholders = placeholders.entries.sortedByDescending { it.key.length }

        // 替换 material 字段的占位符
        val materialValue = itemMap["material"] as? String ?: "STONE"
        val replacedMaterial = sortedPlaceholders.fold(materialValue) { acc, (key, value) ->
            acc.replace(key, value)
        }
        itemMap["material"] = replacedMaterial

        // 使用 XItemStack.deserialize(Map, translator)
        val itemStack = XItemStack.deserialize(itemMap) {
            // 处理其他字段的占位符（name, lore 等），从长到短替换
            sortedPlaceholders.fold(it) { acc, (key, value) ->
                acc.replace(key, value)
            }
        }

        val icon = buildItem(itemStack) {
            if (section.getBoolean("shiny", false)) {
                shiny()
            }
            customizer.invoke(this, section)
            colored()
        }
        val slot = getChar("$sectionPath.slot", 'O')!!
        chest.getSlots(slot).forEach {
            setItem(it, icon)
        }
    }

    /**
     * 在页面打开后获取指定槽位标识的所有非空物品
     * @param char 槽位标识字符
     * @return 非空物品列表（自动过滤空气方块）
     */
    fun Inventory.getSlotItems(char: Char): List<ItemStack> {
        return chest.getSlots(char).mapNotNull {
            getItem(it)?.ifAir()
        }
    }

    /**
     * 在页面打开后获取指定槽位标识的首个槽位物品
     * @param char 槽位标识字符
     * @return 首个槽位的物品（可能为空气方块）
     */
    fun Inventory.getSlotItemsFirst(char: Char): ItemStack? {
        return getItem(chest.getFirstSlot(char))
    }

    /**
     * 在页面打开后获取指定槽位标识的首个非空物品
     * @param char 槽位标识字符
     * @return 首个非空物品（自动过滤空气方块）
     */
    fun Inventory.getSlotItem(char: Char): ItemStack? {
        return getSlotItems(char).firstOrNull()
    }

    /**
     * 获取UI布局配置
     * @return 布局字符串列表，用于定义箱子UI的槽位排列
     */
    fun getLayout(): List<String> {
        return config.getStringList("$rootPath.layout")
    }

    /**
     * 获取图标槽位标识
     * @param sectionPath 配置节路径
     * @return 槽位标识字符
     */
    fun getIconSlot(sectionPath: String): Char? {
        return getChar("$sectionPath.slot", null)
    }

    /**
     * 快捷创建翻页按钮
     * 自动读取配置中的 next_page 和 previous_page 节点
     * @param page 分页箱子实例
     */
    fun putPagination(page: PageableChest<*>) {
        // 下一页按钮
        page.setNextPage(page.getFirstSlot(getChar("next_page.slot", 'N')!!)) { _, hasNextPage ->
            val sectionPath = if (hasNextPage) "next_page.has" else "next_page.normal"
            buildIcon(sectionPath, mapOf("current_page" to "${page.page}", "" to "${page.getProperty<Int>("maxPage")}"))
        }

        // 上一页按钮
        page.setPreviousPage(page.getFirstSlot(getChar("previous_page.slot", 'P')!!)) { _, hasPreviousPage ->
            val sectionPath = if (hasPreviousPage) "previous_page.has" else "previous_page.normal"
            buildIcon(sectionPath, mapOf("current_page" to "${page.page}", "" to "${page.getProperty<Int>("maxPage")}"))
        }
    }

    /**
     * 初始化菜单页面
     * 1. 绑定Chest实例到Helper
     * 2. 应用布局配置
     * 3. 自动加载配置中 button 节点下的所有按钮
     */
    fun Chest.initMenu() {
        chest = this
        map(*getLayout().toTypedArray())
        // 添加button节点下的所有按钮与回调
        for (sectionPath in getConfigurationSection("button")?.getKeys(false) ?: emptySet()) {
            buildIconAction("button.${sectionPath}")
        }
    }

    /**
     * 重新加载配置
     */
    fun reload() {
        config.reload()
    }

    /**
     * 返还指定槽位的物品
     * @param slotChars 槽位字符列表
     * @param delete 是否删除UI里的物品
     */
    fun Inventory.returnItems(slotChars: List<Char>, delete: Boolean = false) {
        val slots = slotChars.flatMap { chest.getSlots(it) }
        slots.forEach {
            player.giveItem(getItem(it))
            if (delete) {
                setItem(it, null)
            }
        }
    }

    /**
     * 规则：创建条件槽位 - 控制物品的放入和取出
     * @param slotChar 槽位字符
     * @param condition 条件判断 (放入物品?, 取出物品?) -> 是否允许
     * @param onFailed 失败时执行的Kether脚本
     */
    fun ClickEvent.ruleConditionSlot(
        slotChar: Char,
        condition: (put: ItemStack?, out: ItemStack?) -> Boolean,
        onFailed: ClickEvent.() -> Unit = {}
    ): Boolean {
        val rawSlots = chest.getSlots(slotChar)
        for (rawSlot in rawSlots) {
            val result = conditionSlot(rawSlot, condition) {
                onFailed.invoke(this)
            }
            if (!result) return false
        }
        return true
    }

    /**
     * 规则：限制槽位最大堆叠数量
     * @param slotChar 槽位字符
     * @param maxAmount 最大堆叠数量
     * @param onFailed 失败时执行的Kether脚本
     */
    fun ClickEvent.ruleLimitAmount(
        slotChar: Char,
        maxAmount: Int,
        onFailed: ClickEvent.() -> Unit = {}
    ): Boolean {
        val rawSlots = chest.getSlots(slotChar)
        for (rawSlot in rawSlots) {
            val result = amountCondition(rawSlot, maxAmount) {
                onFailed.invoke(this)
            }
            if (!result) return false
        }
        return true
    }

    /**
     * 规则：锁定槽位交互
     * @param slotChars 槽位字符列表
     * @param reverse 反向模式：true=仅这些槽位可交互，false=锁定这些槽位
     */
    fun ClickEvent.ruleLockSlots(
        slotChars: List<Char>,
        reverse: Boolean = false
    ) {
        val rawSlots = slotChars.flatMap { chest.getSlots(it) }
        lockSlots(rawSlots, reverse)
    }

    /**
     * 创建UI配置助手实例的便捷方法
     */
    companion object {

        /**
         * 便捷创建UI配置助手并执行操作
         * @param player 目标玩家
         * @param file 配置文件
         * @param rootPath 配置根路径
         * @param action 操作回调，在UIConfigHelper上下文中执行
         */
        fun helper(player: Player, file: File, rootPath: String, action: UIConfigHelper.(player: Player) -> Unit) {
            action.invoke(UIConfigHelper(file, rootPath, player), player)
        }

        /**
         * 测试示例方法
         * 演示UIConfigHelper的两种典型用法：分页箱子和普通箱子
         */
        fun test(player: Player) {
            // 创建UI配置助手实例
            helper(player, newFile(getDataFolder(), "config.yml"), "test_ui") { player ->
                player.openMenu<PageableChest<XMaterial>>("例子") {
                    initMenu() // 初始化页面同步一下布局
                    putPagination(this) // 添加翻页按钮
                    elements {
                        XMaterial.values().toList()
                    }
                    onGenerate { player, element, index, slot ->
                        element.parseItem() ?: buildIcon("null_item")
                    }
                    onClick { event, element ->
                        event.isCancelled = true
                        player.sendMessage("你点击了 $element")
                    }
                    // 简化的Icon创建方式
                    buildIconAction("test_item") {
                        val string = it.getString("custom")
                        if (string != null) {
                            player.sendMessage("这样可以拿到该按钮下的参数: ${string}")
                        }
                    }

                }
            }

            helper(player, newFile(getDataFolder(), "config.yml"), "test_ui") { player ->
                player.openMenu<Chest>("例子") {
                    initMenu() // 一定要initMenu，这一步是让 Helper 和 Menu进行绑定
                    buildIconAction("test_item") {
                        val string = it.getString("custom")
                        if (string != null) {
                            player.sendMessage("这样可以拿到该按钮下的参数: ${string}")
                        }
                    }
                    /**
                     * 想在页面创建完成后 再操作页面也可以
                     * 通常是由 onBuild 或者是 onClick onClose 里面 进行页面操作
                     * 因为 set 方法是预声明，在创建后就无法通过 set 方法再次进行设置了
                     */
                    onBuild { player, inventory ->
                        inventory.updateIcon("test_item", mapOf("{name}" to "Arim"))
                        inventory.updateIcon("test_item", mapOf("{name}" to "Arim")) {
                            name = "${it.getString("name")} ${it.getString("custom")})}"
                        }
                    }

                    onClose {
                        it.inventory.getSlotItem('B') // ->ItemStack 获取Slot B 里面的物品
                        it.inventory.getSlotItems('C') // ->List<ItemStack> 获取Slot C 的所有物品
                    }
                }
            }
        }
    }
}
