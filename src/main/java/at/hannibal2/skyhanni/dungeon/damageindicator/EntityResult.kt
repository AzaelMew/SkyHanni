package at.hannibal2.skyhanni.dungeon.damageindicator

class EntityResult(
    val delayedStart: Long = -1L,
    val ignoreBlocks: Boolean = false,
    val finalDungeonBoss: Boolean = false,
    val bossType: BossType = BossType.DUNGEON,
)