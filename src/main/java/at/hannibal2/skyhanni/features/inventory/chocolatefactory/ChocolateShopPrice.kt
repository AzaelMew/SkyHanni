package at.hannibal2.skyhanni.features.inventory.chocolatefactory

import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.LorenzChatEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.utils.DisplayTableEntry
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.ItemUtils.itemName
import at.hannibal2.skyhanni.utils.ItemUtils.loreCosts
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NEUInternalName
import at.hannibal2.skyhanni.utils.NEUItems.getPrice
import at.hannibal2.skyhanni.utils.NEUItems.getPriceOrNull
import at.hannibal2.skyhanni.utils.NumberUtil
import at.hannibal2.skyhanni.utils.NumberUtil.million
import at.hannibal2.skyhanni.utils.RegexUtils.groupOrNull
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.UtilsPatterns
import at.hannibal2.skyhanni.utils.renderables.Renderable
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object ChocolateShopPrice {
    private val config get() = ChocolateFactoryAPI.config.chocolateShopPrice

    private var display = emptyList<Renderable>()
    private var products = emptyList<Product>()

    private val menuNamePattern by ChocolateFactoryAPI.patternGroup.pattern(
        "shop.title",
        "Chocolate Shop"
    )
    private val itemBoughtPattern by ChocolateFactoryAPI.patternGroup.pattern(
        "shop.bought",
        "§aYou bought §r§.(?<item>[\\w ]+)§r(?:§8 x(?<amount>\\d+)§r)?§a!"
    )

    var inInventory = false
    private var callUpdate = false
    var inventoryItems = emptyMap<Int, ItemStack>()

    @SubscribeEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (inInventory) {
            update()
        }
    }

    @SubscribeEvent
    fun onInventoryOpen(event: InventoryFullyOpenedEvent) {
        if (!isEnabled()) return
        val isInShop = menuNamePattern.matches(event.inventoryName)
        val isInShopOptions = UtilsPatterns.shopOptionsPattern.matches(event.inventoryName)

        if (!isInShop && !isInShopOptions) return
        if (event.inventoryItems[48]?.getLore()?.first() != "§7To Chocolate Shop" && isInShopOptions) return

        inInventory = true
        callUpdate = isInShop

        inventoryItems = event.inventoryItems
        if (!callUpdate) {
            products.forEach { it.slot = null }
        }
        update()
    }

    private fun updateProducts() {
        val newProducts = mutableListOf<Product>()
        for ((slot, item) in inventoryItems) {

            val lore = item.getLore()
            val chocolate = ChocolateFactoryAPI.getChocolateBuyCost(lore) ?: continue
            val internalName = item.getInternalName()
            val itemPrice = internalName.getPriceOrNull() ?: continue
            val otherItemsPrice = item.loreCosts().sumOf { it.getPrice() }.takeIf { it != 0.0 }

            newProducts.add(Product(slot, item.itemName, internalName, chocolate, itemPrice, otherItemsPrice))
        }
        products = newProducts
    }

    private fun update() {
        if (callUpdate) updateProducts()

        val multiplier = 1.million
        // TODO merge core with SkyMartCopperPrice into a utils
        val table = mutableListOf<DisplayTableEntry>()

        for (product in products) {

            val profit = product.itemPrice - (product.otherItemPrice ?: 0.0)
            val factor = (profit / product.chocolate) * multiplier
            val perFormat = NumberUtil.format(factor)

            val hover = buildList {
                add(product.name)

                add("")
                add("§7Item price: §6${NumberUtil.format(product.itemPrice)} ")
                product.otherItemPrice?.let {
                    add("§7Additional cost: §6${NumberUtil.format(it)} ")
                }
                add("§7Profit per purchase: §6${NumberUtil.format(profit)} ")
                add("")
                add("§7Chocolate amount: §c${NumberUtil.format(product.chocolate)} ")
                add("§7Profit per million chocolate: §6${perFormat} ")
                add("")
                val formattedTimeUntilGoal = ChocolateAmount.CURRENT.formattedTimeUntilGoal(product.chocolate)
                add("§7Time until affordable: §6$formattedTimeUntilGoal ")
            }
            table.add(
                DisplayTableEntry(
                    "${product.name}§f:",
                    "§6§l$perFormat",
                    factor,
                    product.item,
                    hover,
                    highlightsOnHoverSlots = product.slot?.let { listOf(it) } ?: emptyList()
                )
            )
        }

        val newList = mutableListOf<Renderable>()
        newList.add(Renderable.string("§e§lCoins per million chocolate§f:"))
        // TODO update this value every second
        // TODO add time until can afford
        newList.add(Renderable.string("§eChocolate available: §6${ChocolateAmount.CURRENT.formatted}"))
        newList.add(LorenzUtils.fillTable(table, padding = 5, itemScale = config.itemScale))
        display = newList
    }

    @SubscribeEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        inInventory = false
        callUpdate = false
    }

    @SubscribeEvent
    fun onBackgroundDraw(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (inInventory) {
            config.position.renderRenderables(
                display,
                extraSpace = 5,
                posLabel = "Chocolate Shop Price"
            )
        }
    }

    @SubscribeEvent
    fun onChat(event: LorenzChatEvent) {
        if (!LorenzUtils.inSkyBlock) return
        if (!inInventory) return
        itemBoughtPattern.matchMatcher(event.message) {
            val item = group("item")
            val amount = groupOrNull("amount")?.toIntOrNull() ?: 1
            val product = products.find { it.name.removeColor() == item } ?: return

            ChocolateAmount.addToCurrent(product.chocolate * -amount)
        }

    }

    private fun isEnabled() = LorenzUtils.inSkyBlock && config.enabled

    private data class Product(
        var slot: Int?,
        val name: String,
        val item: NEUInternalName,
        val chocolate: Long,
        val itemPrice: Double,
        val otherItemPrice: Double?,
    )
}
