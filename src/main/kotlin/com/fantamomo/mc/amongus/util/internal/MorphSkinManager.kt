package com.fantamomo.mc.amongus.util.internal

import com.destroystokyo.paper.profile.PlayerProfile
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.data.AmongUsConfig
import com.fantamomo.mc.amongus.data.AmongUsSecrets
import com.fantamomo.mc.amongus.util.internal.mineskin.HttpClientRequestHandler
import com.fantamomo.mc.amongus.util.safeCreateDirectories
import com.fantamomo.mc.amongus.util.skinblender.SkinBlender
import com.fantamomo.mc.amongus.util.skinblender.VirusSkinBlender
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.mineskin.MineSkinClient
import org.mineskin.data.JobInfo
import org.mineskin.data.User
import org.mineskin.exception.MineSkinRequestException
import org.mineskin.request.GenerateRequest
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.lang.ref.WeakReference
import java.net.URL
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull
import kotlin.uuid.Uuid
import org.mineskin.data.Visibility as MineSkinVisibility

@OptIn(ExperimentalSerializationApi::class)
object MorphSkinManager {
    private const val INVALID_API_KEY_MESSAGE = "Invalid API Key"
    private const val AVERAGE_SKIN_GENERATION_TIME = 1.5
    private val logger = LoggerFactory.getLogger("AmongUs-MorphSkinManager")
    private val mineskinLogger = LoggerFactory.getLogger("AmongUs-MineSkin")

    private val baseDir = AmongUs.dataPath.resolve("morph_cache")
    internal val skinDir = baseDir.resolve("skins")
    private val dataDir = baseDir.resolve("data")

    private val API_KEY by AmongUsSecrets::MINE_SKIN_API_KEY

    private val client: MineSkinClient by lazy {
        MineSkinClient.builder()
            .requestHandler(::HttpClientRequestHandler)
            .userAgent("Fantamomo/among-us-in-minecraft/1.0")
            .apiKey(API_KEY)
            .build()
    }

    private var initializing: Boolean = false
    private var initialized: Boolean? = null
        set(value) {
            if (value == null) throw IllegalArgumentException("Can not set initialized to null")
            field = value
            initializing = false
        }
    private var visibility: MineSkinVisibility = MineSkinVisibility.UNLISTED

    private val blender: SkinBlender by lazy {
        val selected = AmongUsConfig.MorphBlender.blender
        SkinBlender.blenders.firstOrNull { it.id == selected } ?: VirusSkinBlender
    }

    private val json = Json {
        prettyPrint = AmongUs.IN_DEVELOPMENT
        ignoreUnknownKeys = true
    }

    private val textureCache = ConcurrentHashMap<String, WeakReference<SkinData>>()

    internal fun init() {
        if (!AmongUsConfig.MorphBlender.enabled) return
        if (initialized != null || initializing) return
        initializing = true

        if (API_KEY.isEmpty()) {
            mineskinLogger.error("It seams like you enabled MorphBlender but didn't set your MineSkin API Key.")
            mineskinLogger.error("Please check your secrets.properties file and set the 'mineskin' variable to your API Key.")
            mineskinLogger.error("If you don't have an API Key, you can get one here: https://account.mineskin.org/keys/")
            mineskinLogger.error("If you don't want to use MorphBlender, disable it in the config.yml file.")
            initialized = false
            return
        }

        AmongUs.server.scheduler.runTaskAsynchronously(AmongUs, ::asyncInit)
    }

    private fun asyncInit() {
        client.misc().user
            .thenAccept { user ->
                val user = user.user
                printUser(user)

                val privateSkinsPermission = user.grants().getBoolean("private_skins").getOrDefault(false)
                val configVisibility = AmongUsConfig.MorphBlender.visibility

                visibility = when (configVisibility) {
                    MorphSkinManager.Visibility.PRIVATE if (!privateSkinsPermission) -> {
                        mineskinLogger.warn("You selected the visibility 'private' in config.yml")
                        mineskinLogger.warn("But your MineSkin plan doesn't allow private skins.")
                        mineskinLogger.warn("Falling back to visibility 'unlisted'")
                        MineSkinVisibility.UNLISTED
                    }

                    MorphSkinManager.Visibility.AUTO -> if (privateSkinsPermission) MineSkinVisibility.PRIVATE else MineSkinVisibility.UNLISTED
                    else -> configVisibility.handle!!
                }

                skinDir.safeCreateDirectories()
                dataDir.safeCreateDirectories()

                initialized = true
            }.exceptionally { ex ->
                val cause = ex.cause
                if (cause is MineSkinRequestException) {
                    val message = cause.message
                    if (message == INVALID_API_KEY_MESSAGE) {
                        mineskinLogger.error("Invalid MineSkin API Key")
                        mineskinLogger.error("Please check your secrets.properties file")
                        mineskinLogger.error("MorphBlender will not work without a valid API Key")
                        mineskinLogger.error("If you don't have an API Key, you can get one here: https://account.mineskin.org/keys/")
                        initialized = false
                        return@exceptionally null
                    }
                    mineskinLogger.error("Failed to authenticate with MineSkin API: $message")
                    val response = cause.response
                    mineskinLogger.error("Status: ${response.status}")
                    response.errors.forEach { mineskinLogger.error("Error: ${it.message}") }
                    response.warnings.forEach { mineskinLogger.error("Warning: ${it.message}") }
                    response.messages.forEach { mineskinLogger.error("Message: ${it.message}") }
                } else {
                    mineskinLogger.error("Unexpected exception while trying to authenticate with MineSkin API", cause)
                }
                initialized = false
                null
            }
    }

    private fun printUser(user: User) {
        val uuid = user.uuid().let { Uuid.parseOrNull(it)?.toString() ?: it }
        var maskedSize = 19
        val maskedUuid = uuid.mapIndexed { index, ch ->
            when {
                ch == '-' -> '-'.also { maskedSize++ }
                index > maskedSize -> ch
                else -> '*'
            }
        }.joinToString("")
        mineskinLogger.info("Successfully authenticated as $maskedUuid")

        val grants = user.grants()
        val delayStr = grants.getString("delay").getOrNull()
        val perMinuteStr = grants.getString("per_minute").getOrNull()
        val concurrencyStr = grants.getString("concurrency").getOrNull()
        val priority = grants.getString("priority").getOrNull() ?: "<unknown>"
        val privateSkins = grants.getBoolean("private_skins").getOrDefault(false)

        mineskinLogger.info(
            "Grants[delay=$delayStr, perMinute=$perMinuteStr, concurrency=$concurrencyStr, priority=$priority, privateSkins=$privateSkins]"
        )

        val missingValues = mutableListOf<String>()
        val c = concurrencyStr?.toDoubleOrNull() ?: run { missingValues.add("concurrency"); null }
        val r = perMinuteStr?.toDoubleOrNull() ?: run { missingValues.add("per_minute"); null }
        val d = delayStr?.toDoubleOrNull() ?: run { missingValues.add("delay"); null }

        if (missingValues.isNotEmpty()) {
            mineskinLogger.info(
                "Cannot estimate Morph Animation generation time: missing or invalid values: ${
                    missingValues.joinToString(", ")
                }"
            )
            return
        }

        if (c!! <= 0 || r!! <= 0 || d!! < 0) {
            mineskinLogger.info(
                "Cannot estimate Morph Animation generation time: invalid numeric values (concurrency=$c, perMinute=$r, delay=$d)"
            )
            return
        }

        val n = 8
        val estimatedSeconds = (n / c) * (AVERAGE_SKIN_GENERATION_TIME + d)
        mineskinLogger.info(
            "Estimated Morph Animation generation time ($n frames): ~%.2f seconds (~%.2f minutes)"
                .format(estimatedSeconds, estimatedSeconds / 60.0)
        )
    }

    fun isValid() = initialized == true && AmongUsConfig.MorphBlender.enabled && API_KEY.isNotBlank()

    private fun checkValid() = require(isValid()) { "MineSkin API key cannot be blank or MorphBlender disabled" }

    fun getCachedFrames(
        baseProfile: PlayerProfile,
        targetProfile: PlayerProfile,
        variants: Int
    ): List<Skin>? {
        val baseId = baseProfile.textures.skin?.toString() ?: baseProfile.id.toString()
        val targetId = targetProfile.textures.skin?.toString() ?: targetProfile.id.toString()

        if (baseId == targetId) {
            logger.debug("getCachedFrames: base == target ($baseId), returning all PlayerProfileSkins")
            return (0..variants + 1).map { i ->
                Skin.PlayerProfileSkin(baseProfile, i.toFloat() / (variants + 1))
            }
        }

        val valueToSkin = mutableMapOf<String, Skin.GeneratedSkin>()
        val frames = mutableListOf<Skin>()

        for (i in 0..variants + 1) {
            when (i) {
                0 -> frames += Skin.PlayerProfileSkin(baseProfile, 0f)
                variants + 1 -> frames += Skin.PlayerProfileSkin(targetProfile, 1f)
                else -> {
                    val t = i.toFloat() / (variants + 1)
                    val hash = buildHash(baseId, targetId, t)
                    val data = getTexture(hash) ?: return null
                    val skin = valueToSkin.getOrPut(data.value) {
                        Skin.GeneratedSkin(
                            hash = hash,
                            t = t,
                            pngFile = skinDir.resolve("$hash.png").toFile(),
                            value = data.value,
                            signature = data.signature
                        )
                    }
                    frames += skin
                }
            }
        }

        return frames
    }

    fun pregenerateFromProfiles(
        baseProfile: PlayerProfile,
        targetProfile: PlayerProfile,
        variants: Int
    ): CompletableFuture<List<Skin>> {
        return try {
            checkValid()
            CompletableFuture.supplyAsync {
                try {
                    val baseSkin = fetchSkinFromProfile(baseProfile)
                    val targetSkin = fetchSkinFromProfile(targetProfile)
                    pregenerate(baseSkin, targetSkin, baseProfile, targetProfile, variants).join()
                } catch (e: Exception) {
                    logger.error("Failed to pregenerate skins from profiles", e)
                    emptyList()
                }
            }
        } catch (e: Exception) {
            logger.error("Pregeneration aborted due to invalid configuration", e)
            CompletableFuture.completedFuture(emptyList())
        }
    }

    private class PendingFrame(val pixels: IntArray, val dataFuture: CompletableFuture<SkinData>)

    private fun pregenerate(
        baseSkin: BufferedImage,
        targetSkin: BufferedImage,
        baseProfile: PlayerProfile,
        targetProfile: PlayerProfile,
        variants: Int
    ): CompletableFuture<List<Skin>> {
        checkValid()

        val baseId = baseProfile.textures.skin?.toString() ?: baseProfile.id.toString()
        val targetId = targetProfile.textures.skin?.toString() ?: targetProfile.id.toString()

        val basePixels = baseSkin.toPixelArray()
        val targetPixels = targetSkin.toPixelArray()

        if (basePixels.contentEquals(targetPixels)) {
            logger.info("pregenerate: base and target skins are pixel-identical – skipping generation entirely")
            return CompletableFuture.completedFuture(
                (0..variants + 1).map { i ->
                    Skin.PlayerProfileSkin(baseProfile, i.toFloat() / (variants + 1))
                }
            )
        }

        val futures = mutableListOf<CompletableFuture<out Skin>>()
        val pendingFrames = mutableListOf<PendingFrame>()

        for (i in 0..variants + 1) {

            if (i == 0) {
                futures += CompletableFuture.completedFuture(Skin.PlayerProfileSkin(baseProfile, 0f))
                continue
            }
            if (i == variants + 1) {
                futures += CompletableFuture.completedFuture(Skin.PlayerProfileSkin(targetProfile, 1f))
                continue
            }

            val t = i.toFloat() / (variants + 1)
            val hash = buildHash(baseId, targetId, t)
            val pngFile = skinDir.resolve("$hash.png").toFile()

            try {
                val cached = getTexture(hash)
                if (cached != null) {
                    logger.debug("Cache hit: $hash")
                    futures += CompletableFuture.completedFuture(
                        Skin.GeneratedSkin(
                            hash = hash, t = t, pngFile = pngFile,
                            value = cached.value, signature = cached.signature
                        )
                    )
                    continue
                }

                val image = blender.blend(baseSkin, targetSkin, t)
                val blendedPixels = image.toPixelArray()

                if (blendedPixels.contentEquals(basePixels)) {
                    logger.debug("Blend == base at t=${"%.4f".format(t)}, skipping upload")
                    futures += CompletableFuture.completedFuture(Skin.PlayerProfileSkin(baseProfile, t))
                    continue
                }
                if (blendedPixels.contentEquals(targetPixels)) {
                    logger.debug("Blend == target at t=${"%.4f".format(t)}, skipping upload")
                    futures += CompletableFuture.completedFuture(Skin.PlayerProfileSkin(targetProfile, t))
                    continue
                }

                val duplicate = pendingFrames.firstOrNull { it.pixels.contentEquals(blendedPixels) }
                if (duplicate != null) {
                    logger.debug("Blend == previous frame at t=${"%.4f".format(t)}, reusing upload")
                    val dedupFuture = duplicate.dataFuture
                        .thenApply { originalData ->
                            val redirectData = SkinData(hash, originalData.value, originalData.signature)
                            try {
                                json.encodeToStream(redirectData, dataDir.resolve("$hash.json").toFile().outputStream())
                                textureCache[hash] = WeakReference(redirectData)
                            } catch (e: Exception) {
                                logger.error("Failed to persist redirect cache for $hash", e)
                            }
                            redirectData
                        }
                        .thenApply<Skin> { data ->
                            Skin.GeneratedSkin(
                                hash = hash, t = t, pngFile = pngFile,
                                value = data.value, signature = data.signature
                            )
                        }
                        .exceptionally { throwable ->
                            logger.error("Failed to resolve duplicate skin for t=$t, hash=$hash", throwable)
                            Skin.PlayerProfileSkin(baseProfile, t)
                        }
                    futures += dedupFuture
                    continue
                }

                ImageIO.write(image, "png", pngFile)

                val uploadFuture = uploadToMineSkin(pngFile, hash)

                pendingFrames += PendingFrame(blendedPixels, uploadFuture)

                futures += uploadFuture
                    .thenApply<Skin> { skinData ->
                        Skin.GeneratedSkin(
                            hash = hash, t = t, pngFile = pngFile,
                            value = skinData.value, signature = skinData.signature
                        )
                    }
                    .exceptionally { throwable ->
                        logger.error("Failed to generate or upload skin for t=$t, hash=$hash", throwable)
                        Skin.PlayerProfileSkin(baseProfile, t)
                    }

            } catch (e: Exception) {
                logger.error("Error during pregeneration for t=$t, hash=$hash", e)
            }
        }

        return CompletableFuture
            .allOf(*futures.toTypedArray())
            .thenApply { futures.map { it.join() }.sortedBy { it.t } }
            .exceptionally { throwable ->
                logger.error("Error completing pregeneration", throwable)
                emptyList()
            }
    }

    fun getTexture(hash: String): SkinData? {
        textureCache[hash]?.get()?.let { return it }

        return try {
            val file = dataDir.resolve("$hash.json").toFile()
            if (!file.exists()) return null
            json.decodeFromStream<SkinData>(file.inputStream()).also { data ->
                textureCache[hash] = WeakReference(data)
            }
        } catch (e: Exception) {
            logger.error("Failed to read texture $hash", e)
            null
        }
    }

    private fun uploadToMineSkin(file: File, hash: String): CompletableFuture<SkinData> {
        return try {
            val request = GenerateRequest.upload(file)
                .name("Morph-$hash".take(20))
                .visibility(visibility)

            client.queue().submit(request)
                .thenCompose { queueResponse ->
                    queueResponse.job
                    val job: JobInfo = queueResponse.job
                    val result = job.waitForCompletion(client)
                    result
                }
                .thenCompose { jobResponse ->
                    jobResponse.getOrLoadSkin(client)
                }
                .thenApply { skinInfo ->
                    val value = skinInfo.texture().data().value()
                    val signature = skinInfo.texture().data().signature()
                    val skinData = SkinData(hash, value, signature)

                    try {
                        json.encodeToStream(skinData, dataDir.resolve("$hash.json").toFile().outputStream())
                        textureCache[hash] = WeakReference(skinData)
                    } catch (e: Exception) {
                        logger.error("Failed to cache skin data $hash", e)
                    }

                    logger.debug("Uploaded & cached: $hash")
                    skinData
                }
                .exceptionally { throwable ->
                    logger.error("MineSkin upload failed for $hash", throwable)
                    throw RuntimeException(throwable)
                }

        } catch (e: Exception) {
            logger.error("Failed to submit skin upload request for $hash", e)
            CompletableFuture.failedFuture(e)
        }
    }

    private fun fetchSkinFromProfile(profile: PlayerProfile): BufferedImage {
        return try {
            val property = profile.properties.firstOrNull { it.name == "textures" }
                ?: throw IllegalStateException("Profile has no textures")

            val decoded = String(Base64.getDecoder().decode(property.value))
            val urlRegex = """"url"\s*:\s*"([^"]+)"""".toRegex()
            val match = urlRegex.find(decoded) ?: throw IllegalStateException("Cannot parse skin url")

            @Suppress("DEPRECATION")
            ImageIO.read(URL(match.groupValues[1]))
        } catch (e: Exception) {
            logger.error("Failed to fetch skin for profile ${profile.name}", e)
            throw e
        }
    }

    internal fun buildHash(base: String, target: String, t: Float, blender: SkinBlender = this.blender): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val input = "$base-$target-${blender.id}-${"%.4f".format(t)}"
            md.digest(input.toByteArray()).toHexString()
        } catch (e: Exception) {
            logger.error("Failed to build hash for $base -> $target t=$t", e)
            UUID.randomUUID().toString().replace("-", "")
        }
    }

    private fun BufferedImage.toPixelArray(): IntArray {
        val pixels = IntArray(width * height)
        getRGB(0, 0, width, height, pixels, 0, width)
        return pixels
    }

    @Serializable
    data class SkinData(
        val hash: String,
        val value: String,
        val signature: String
    )

    sealed interface Skin {
        val t: Float

        data class PlayerProfileSkin(val profile: PlayerProfile, override val t: Float) : Skin
        data class GeneratedSkin(
            val hash: String,
            override val t: Float,
            val pngFile: File,
            val value: String,
            val signature: String
        ) : Skin
    }

    enum class Visibility(val handle: MineSkinVisibility?) {
        PUBLIC(MineSkinVisibility.PUBLIC),
        UNLISTED(MineSkinVisibility.UNLISTED),
        PRIVATE(MineSkinVisibility.PRIVATE),
        AUTO(null);

        companion object {
            fun getOrNull(name: String) = entries.find { it.name == name.uppercase() }
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}