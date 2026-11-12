package com.fantamomo.mc.amongus.game

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.area.GameArea
import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.util.TickContext
import com.fantamomo.mc.amongus.util.safeCreateDirectories
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.WorldCreator
import org.slf4j.LoggerFactory
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import kotlin.io.path.*
import kotlin.uuid.Uuid

@OptIn(ExperimentalPathApi::class)
object GameManager {
    private const val AMONG_US_WORLD_DIR_DETECTION_FILE = "AMONG_US_WORLD_DIR_DETECTION_FILE.txt"
    private const val AMONG_US_WORLD_DIR_DETECTION_FILE_CONTENT =
        "This file is used to detect and delete Among Us worlds where a game has been played.\n" +
                "Please do not delete this file"

    private val gamesByCode = mutableMapOf<String, Game>()
    private val games = mutableListOf<Game>()
    private var taskId = -1
    private val logger = LoggerFactory.getLogger("AmongUsGameManager")
    private val markedForRemove: MutableSet<Game> = mutableSetOf()
    private val worlds: MutableSet<World> = mutableSetOf()
    private var ticks: Long = 0

    var currentTick = TickContext(0)
        private set

    fun getGames(): List<Game> = games

    fun addGame(game: Game): Boolean {
        init()
        if (gamesByCode.containsKey(game.code)) return false
        games.add(game)
        gamesByCode[game.code] = game
        return true
    }

    fun removeAmongUsWorlds() {
        try {
            val serverRootFolder = AmongUs.server.worldContainer.toPath().toAbsolutePath()

            serverRootFolder.listDirectoryEntries().forEach { worldFolder ->
                if (!worldFolder.isDirectory()) return@forEach

                if (!worldFolder.resolve("level.dat").isRegularFile()) {
                    return@forEach
                }

                val amongUsFolder = worldFolder
                    .resolve("dimensions")
                    .resolve("among-us")

                if (!amongUsFolder.isDirectory()) return@forEach

                val detectionFile = amongUsFolder
                    .resolve(AMONG_US_WORLD_DIR_DETECTION_FILE)

                if (detectionFile.isRegularFile()) {
                    logger.info("Deleting Among Us worlds directory: $amongUsFolder")
                    amongUsFolder.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to delete Among Us worlds directories", e)
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
        ticks++
        val tickContext = TickContext(ticks)
        currentTick = tickContext
        if (markedForRemove.isNotEmpty()) {
            markedForRemove.forEach {
                for (player in it.players.toList()) {
                    it.removePlayer0(player)
                    PlayerManager.gameEnds(player, false)
                }
                gameEnd(it)
            }
            games.removeAll(markedForRemove)
            gamesByCode.values.removeAll(markedForRemove)
            markedForRemove.clear()
        }
        for (game in games) {
            try {
                game.tick(tickContext)
            } catch (e: Exception) {
                logger.error("Failed to tick game ${game.code}", e)
            }
        }
    }

    operator fun get(code: String): Game? = gamesByCode[code]

    fun createGame(area: GameArea, maxPlayers: Int, callback: (Game?) -> Unit) {
        val worldFolder = area.worldFolder
        if (worldFolder.isEmpty()) throw IllegalArgumentException("Area ${area.name} has no world folder")
        val worldContainer = AmongUs.server.worldContainer.toPath().toAbsolutePath()
        val path = worldContainer.resolve(worldFolder).toAbsolutePath()
        if (worldContainer == path) throw IllegalArgumentException("Area ${area.name} is in the same directory as the server")

        val uuid = Uuid.random()
        val worldKey = NamespacedKey("among-us", "world/$uuid")

        // TODO: this might not work for all cases, so fix it
        val dimensionFolder = worldContainer.resolve(Path(worldFolder).subpath(0, 2))

        val amongUsNamespacedPath = dimensionFolder.resolve(worldKey.namespace)


        val gameWorldPath = amongUsNamespacedPath.resolve(worldKey.key).toAbsolutePath()

        logger.info("Copying area from $path to $gameWorldPath")

        AmongUs.server.scheduler.runTaskAsynchronously(AmongUs, Runnable {
            try {
                gameWorldPath.safeCreateDirectories()
                path.copyToRecursively(
                    target = gameWorldPath,
                    onError = { source, _, ex ->
                        if (source.name == "session.lock") OnErrorResult.SKIP_SUBTREE
                        else throw ex
                    },
                    followLinks = false,
                ) { src, dst ->
                    if (src.name == "session.lock" || src.name == "uid.dat")
                        return@copyToRecursively CopyActionResult.CONTINUE
                    val dstIsDirectory = dst.isDirectory(LinkOption.NOFOLLOW_LINKS)
                    val srcIsDirectory = src.isDirectory(LinkOption.NOFOLLOW_LINKS)
                    val relative = src.relativeTo(path).invariantSeparatorsPathString

                    if (relative == "data/paper") {
                        return@copyToRecursively CopyActionResult.SKIP_SUBTREE
                    }

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

            try {
                val detectionFile = amongUsNamespacedPath.resolve(AMONG_US_WORLD_DIR_DETECTION_FILE)
                if (detectionFile.notExists()) {
                    detectionFile.bufferedWriter().use {
                        it.write(AMONG_US_WORLD_DIR_DETECTION_FILE_CONTENT)
                    }
                }
            } catch (ex: Exception) {
                logger.error("Failed to create detection file", ex)
            }

            AmongUs.server.scheduler.runTask(AmongUs, Runnable {
                val creator = WorldCreator(worldKey)
                val newWorld = creator.createWorld()
                if (newWorld == null) {
                    callback(null)
                    return@Runnable
                }
                worlds.add(newWorld)
                newWorld.isAutoSave = false
                val game = Game(area, newWorld, maxPlayers)
                addGame(game)
                callback(game)
            })
        })
    }

    fun isGameWorld(world: World) = world in worlds

    fun gameEnd(game: Game) {
        val world = game.world
        for (player in world.players) {
            player.teleportAsync(AmongUs.server.respawnWorld.spawnLocation)
            player.inventory.clear()
        }
        logger.info("Unloading world ${world.name}")
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