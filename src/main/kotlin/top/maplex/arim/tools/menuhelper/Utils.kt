package top.maplex.arim.tools.menuhelper

import org.bukkit.event.inventory.InventoryAction.*
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import taboolib.module.ui.ClickEvent
import taboolib.module.ui.ClickType.*
import taboolib.platform.util.giveItem

/**
 * 过度一下，等待TabooLib更新后替换回
 * @author 纸杯
 */


/**
 * 在页面关闭时返还物品
 */
fun InventoryCloseEvent.returnItems(slots: List<Int>) = slots.forEach { player.giveItem(inventory.getItem(it)) }

/**
 * 创建点击事件条件格
 *
 * 用于创造物品的放入和取出条件
 *
 * @param rawSlot 原始格子
 * @param condition 条件
 * @param failedCallback 条件检测失败后执行回调
 * */
fun ClickEvent.conditionSlot(rawSlot: Int, condition: (put: ItemStack?, out: ItemStack?) -> Boolean, failedCallback: () -> Unit = {}): Boolean {
    if (isCancelled) return false
    when(clickType) {
        CLICK -> {
            val event = clickEvent()
            when(event.action) {
                SWAP_WITH_CURSOR, PICKUP_ALL, PLACE_ALL -> {
                    if (rawSlot == event.rawSlot) {
                        val put = event.cursor
                        val out = event.clickedInventory?.getItem(event.slot)
                        if (!condition(put, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                PICKUP_HALF -> {
                    if (rawSlot == event.rawSlot) {
                        val put = null
                        val old = event.clickedInventory?.getItem(event.slot)
                        val out = old?.clone()?.apply { amount = old.amount/2 }

                        if (!condition(put, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                PICKUP_ONE -> {
                    if (rawSlot == event.rawSlot) {
                        val put = null
                        val out = event.clickedInventory?.getItem(event.slot)?.clone()?.apply { amount = 1 }
                        if (!condition(put, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                PICKUP_SOME -> {
                    // 暂时不清楚原理
                    if (rawSlot == event.rawSlot) {
                        event.isCancelled = true
                        failedCallback()
                        return false
                    }
                }
                PLACE_SOME -> {
                    if (rawSlot == event.rawSlot) {
                        val old = event.clickedInventory?.getItem(event.slot)
                        val put = old?.clone()?.apply { amount = old.maxStackSize - old.amount }
                        val out = null
                        if (!condition(put, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                PLACE_ONE -> {
                    if (rawSlot == event.rawSlot) {
                        val put = event.cursor?.clone()?.apply { amount = 1 }
                        val out = null
                        if (!condition(put, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                MOVE_TO_OTHER_INVENTORY -> {
                    event.isCancelled = true
                    return false
                }
                COLLECT_TO_CURSOR -> {
                    if (event.cursor?.isSimilar(event.view.getItem(rawSlot)) == true) {
                        val put = null
                        // 此处数量无法确定
                        val out = event.view.getItem(rawSlot)
                        if (!condition(put, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                HOTBAR_SWAP -> {
                    if (rawSlot == event.rawSlot) {
                        val put = event.clickedInventory?.getItem(event.hotbarButton)
                        val out = event.clickedInventory?.getItem(event.slot)
                        if (!condition(put, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                else -> {
                    if (rawSlot == event.rawSlot) {
                        event.isCancelled = true
                        failedCallback()
                        return false
                    }
                }
            }
        }
        DRAG -> {
            val event = dragEvent()
            if (rawSlot in event.rawSlots) {
                val put = event.newItems[rawSlot]
                if (!condition(put, null)) {
                    event.isCancelled = true
                    failedCallback()
                    return false
                }
            }
        }
        VIRTUAL -> {}
    }
    return true
}

/**
 * 限制槽位最大物品堆叠数量
 * */
fun ClickEvent.amountCondition(rawSlot: Int, amount: Int, failedCallback: () -> Unit = {}): Boolean {
    if (isCancelled) return false
    when(clickType) {
        CLICK -> {
            val event = clickEvent()
            when(event.action) {
                SWAP_WITH_CURSOR, PLACE_ALL -> {
                    if (rawSlot == event.rawSlot) {
                        val put = event.cursor
                        if ((put?.amount ?: 0) > amount) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY, COLLECT_TO_CURSOR -> {}
                PLACE_SOME -> {
                    if (rawSlot == event.rawSlot) {
                        val old = event.clickedInventory?.getItem(event.slot)
                        if ((old?.maxStackSize ?: 0) > amount) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                PLACE_ONE -> {
                    if (rawSlot == event.rawSlot) {
                        val old = event.clickedInventory?.getItem(event.slot)
                        if ((old?.amount ?: 0) + 1 > amount) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                HOTBAR_SWAP -> {
                    if (rawSlot == event.rawSlot) {
                        val put = event.clickedInventory?.getItem(event.hotbarButton)
                        if ((put?.amount ?: 0) > amount) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                else -> {
                    if (rawSlot == event.rawSlot) {
                        event.isCancelled = true
                        failedCallback()
                        return false
                    }
                }
            }
        }
        DRAG -> {
            val event = dragEvent()
            if (rawSlot in event.rawSlots) {
                val put = event.newItems[rawSlot]
                if ((put?.amount ?: 0) > amount) {
                    event.isCancelled = true
                    failedCallback()
                    return false
                }
            }
        }
        VIRTUAL -> {}
    }
    return true
}

/**
 * 锁定 [rawSlots] 格子的交互
 *
 * @param rawSlots 原始格子列表
 * @param reverse 反向锁定，仅保留 rawSlots 格子可交互
 * */
fun ClickEvent.lockSlots(rawSlots: List<Int>, reverse: Boolean = false) {
    if (isCancelled) return
    when(clickType) {
        CLICK -> {
            val event = clickEvent()
            when(event.action) {
                MOVE_TO_OTHER_INVENTORY -> {
                    event.isCancelled = true
                }
                COLLECT_TO_CURSOR -> {
                    event.isCancelled = true
                }
                else -> {
                    if ((reverse && event.rawSlot !in rawSlots) || (!reverse && event.rawSlot in rawSlots)) {
                        event.isCancelled = true
                    }
                }
            }
        }
        DRAG -> {
            val event = dragEvent()
            val check = if (reverse) {
                event.rawSlots.all { it in rawSlots }
            } else {
                event.rawSlots.intersect(rawSlots).isEmpty()
            }
            if (!check) {
                event.isCancelled = true
            }
        }
        VIRTUAL -> {}
    }
}
