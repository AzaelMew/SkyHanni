package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryOpenEvent
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object HighlightOnHoverSlot {
    val map = mutableMapOf<Pair<Int, Int>, List<Int>>()

    @SubscribeEvent
    fun onInventoryOpen(event: InventoryOpenEvent) {
        map.clear()
    }

    @SubscribeEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        map.clear()
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onDrawBackground(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (!LorenzUtils.inSkyBlock) return
        for ((_, indexes) in map) {
            for (slot in InventoryUtils.getItemsInOpenChest()) {
                if (indexes.contains(slot.slotIndex)) {
                    slot highlight LorenzColor.GREEN
                }
            }
        }
    }
}
