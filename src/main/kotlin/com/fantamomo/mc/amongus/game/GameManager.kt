package com.fantamomo.mc.amongus.game

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.area.GameArea
import com.fantamomo.mc.amongus.util.safeCreateDirectories
import org.bukkit.NamespacedKey
import org.bukkit.WorldCreator
import org.slf4j.LoggerFactory
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import kotlin.io.path.*
import kotlin.uuid.Uuid

@OptIn(ExperimentalPathApi::class)
object GameManager {
    private val gamesByCode = mutableMapOf<String, Game>()
    private val games = mutableListOf<Game>()
    private var taskId = -1
    private val worldsPath = AmongUs.dataPath.resolve("worlds")
    private val logger = LoggerFactory.getLogger("AmongUsGameManager")
    private val markedForRemove: MutableSet<Game> = mutableSetOf()

    fun getGames(): List<Game> = games

    fun addGame(game: Game): Boolean {
        init()
        if (gamesByCode.containsKey(game.code)) return false
        games.add(game)
        gamesByCode[game.code] = game
        return true
    }

    init {
        try {
            worldsPath.deleteRecursively()
        } catch (e: Exception) {
            logger.error("Failed to delete worlds directory", e)
        }
    }

    fun init() {
        if (taskId != -1) return
        taskId = AmongUs.server.scheduler.scheduleSyncRepeatingTask(AmongUs, ::tick, 0L, 1L)
    }

    private fun tick() {
        if (games.isEmpty()) {
            AmongUs.server.scheduler.cancelTask(taskId)
            taskId = -1
        }
        if (markedForRemove.isNotEmpty()) {
            markedForRemove.forEach(::gameEnd)
            games.removeAll(markedForRemove)
            gamesByCode.values.removeAll(markedForRemove)
            markedForRemove.clear()
        }
        games.forEach(Game::tick)
    }

    operator fun get(code: String): Game? = gamesByCode[code]

    fun createGame(area: GameArea, maxPlayers: Int, callback: (Game?) -> Unit) {
        val world = AmongUs.server.getWorld(area.worldId)
            ?: throw IllegalArgumentException("World ${area.worldId} not found")
        val path = world.worldPath
        val uuid = Uuid.random()
        val gameWorldPath = worldsPath.resolve(uuid.toString())

        AmongUs.server.scheduler.runTaskAsynchronously(AmongUs, Runnable {
            try {
                gameWorldPath.safeCreateDirectories()
                path.copyToRecursively(gameWorldPath, { source, _, ex ->
                    if (source.name == "session.lock") OnErrorResult.SKIP_SUBTREE
                    else throw ex
                }, followLinks = false) { src, dst ->
                    if (src.name == "session.lock" || src.name == "uid.dat")
                        return@copyToRecursively CopyActionResult.CONTINUE
                    val dstIsDirectory = dst.isDirectory(LinkOption.NOFOLLOW_LINKS)
                    val srcIsDirectory = src.isDirectory(LinkOption.NOFOLLOW_LINKS)
                    if (!(srcIsDirectory && dstIsDirectory)) {
                        if (dstIsDirectory) dst.deleteRecursively()
                        src.copyTo(dst, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING)
                    }
                    CopyActionResult.CONTINUE
                }
            } catch (e: Exception) {
                logger.error("Failed to copy world", e)
                AmongUs.server.scheduler.runTask(AmongUs, Runnable { callback(null) })
                return@Runnable
            }

            AmongUs.server.scheduler.runTask(AmongUs, Runnable {
                val worldContainer = AmongUs.server.worldContainer.toPath()
                val creator = WorldCreator(
                    worldContainer.relativize(gameWorldPath).toString(),
                    NamespacedKey("among-us", "world/$uuid")
                )
                val newWorld = creator.createWorld()
                if (newWorld == null) {
                    callback(null)
                    return@Runnable
                }
                newWorld.isAutoSave = false
                val game = Game(area, newWorld, maxPlayers)
                addGame(game)
                callback(game)
            })
        })
    }

    fun gameEnd(game: Game) {
        val world = game.world
        for (player in world.players) {
            player.teleportAsync(AmongUs.server.respawnWorld.spawnLocation)
            player.inventory.clear()
        }
        AmongUs.server.unloadWorld(world, false)
    }

    fun removeGame(game: Game) {
        games.remove(game)
        gamesByCode.remove(game.code)
        gameEnd(game)
    }

    internal fun markForRemove(game: Game) {
        markedForRemove.add(game)
    }
}