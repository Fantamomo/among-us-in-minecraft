package com.fantamomo.mc.amongus.util.internal

import com.destroystokyo.paper.profile.PlayerProfile
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.data.AmongUsConfig
import com.fantamomo.mc.amongus.data.AmongUsSecrets
import com.fantamomo.mc.amongus.util.safeCreateDirectories
import com.fantamomo.mc.amongus.util.skinblender.SkinBlender
import com.fantamomo.mc.amongus.util.skinblender.VirusSkinBlender
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.mineskin.JsoupRequestHandler
import org.mineskin.MineSkinClient
import org.mineskin.data.JobInfo
import org.mineskin.data.User
import org.mineskin.exception.MineSkinRequestException
import org.mineskin.request.GenerateRequest
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import kotlin.io.path.exists
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
            .requestHandler(::JsoupRequestHandler)
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

        val c = concurrencyStr?.toDoubleOrNull()
            ?: run { missingValues.add("concurrency"); null }

        val r = perMinuteStr?.toDoubleOrNull()
            ?: run { missingValues.add("per_minute"); null }

        val d = delayStr?.toDoubleOrNull()
            ?: run { missingValues.add("delay"); null }

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

                    val baseId = baseProfile.textures.skin?.toString() ?: baseProfile.id.toString()
                    val targetId = targetProfile.textures.skin?.toString() ?: targetProfile.id.toString()

                    pregenerate(baseSkin, targetSkin, baseId, targetId, variants, baseProfile, targetProfile).join()
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

    private fun pregenerate(
        baseSkin: BufferedImage,
        targetSkin: BufferedImage,
        baseId: String,
        targetId: String,
        variants: Int,
        baseProfile: PlayerProfile,
        targetProfile: PlayerProfile
    ): CompletableFuture<List<Skin>> {

        checkValid()

        val futures = mutableListOf<CompletableFuture<out Skin>>()

        for (i in 0..variants + 1) {
            val t = i.toFloat() / (variants + 1)
            val hash = buildHash(baseId, targetId, t)
            val pngFile = skinDir.resolve("$hash.png").toFile()

            try {
                if (isCached(hash)) {
                    logger.debug("Cache hit: $hash")

                    val cached = getTexture(hash)
                    if (cached != null) {
                        futures += CompletableFuture.completedFuture(
                            Skin.GeneratedSkin(
                                hash = hash,
                                t = t,
                                pngFile = pngFile,
                                value = cached.value,
                                signature = cached.signature
                            )
                        )
                        continue
                    }
                }

                if (i == 0) {
                    futures += CompletableFuture.completedFuture(Skin.PlayerProfileSkin(baseProfile, 0f))
                    continue
                } else if (i == variants + 1) {
                    futures += CompletableFuture.completedFuture(Skin.PlayerProfileSkin(targetProfile, 1f))
                    continue
                }

                val image = blender.blend(baseSkin, targetSkin, t)

                ImageIO.write(image, "png", pngFile)

                val future = uploadToMineSkin(pngFile, hash)
                    .thenApply<Skin> { skinData ->
                        Skin.GeneratedSkin(
                            hash = hash,
                            t = t,
                            pngFile = pngFile,
                            value = skinData.value,
                            signature = skinData.signature
                        )
                    }
                    .exceptionally { throwable ->
                        logger.error("Failed to generate or upload skin for t=$t, hash=$hash", throwable)
                        Skin.PlayerProfileSkin(baseProfile, t)
                    }

                futures += future

            } catch (e: Exception) {
                logger.error("Error during pregeneration for t=$t, hash=$hash", e)
            }
        }

        return CompletableFuture
            .allOf(*futures.toTypedArray())
            .thenApply {
                futures.map { it.join() }
                    .sortedBy { it.t }
            }
            .exceptionally { throwable ->
                logger.error("Error completing pregeneration", throwable)
                emptyList()
            }
    }

    fun getTexture(hash: String): SkinData? {
        return try {
            val file = dataDir.resolve("$hash.json").toFile()
            if (!file.exists()) return null
            json.decodeFromStream<SkinData>(file.inputStream())
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
                    val job: JobInfo = queueResponse.job
                    job.waitForCompletion(client)
                }
                .thenCompose { jobResponse ->
                    jobResponse.getOrLoadSkin(client)
                }
                .thenApply { skinInfo ->
                    val value = skinInfo.texture().data().value()
                    val signature = skinInfo.texture().data().signature()

                    val skinData = SkinData(hash, value, signature)

                    try {
                        val outFile = dataDir.resolve("$hash.json").toFile()
                        json.encodeToStream(skinData, outFile.outputStream())
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
            val url = match.groupValues[1]

            @Suppress("DEPRECATION")
            ImageIO.read(URL(url))
        } catch (e: Exception) {
            logger.error("Failed to fetch skin for profile ${profile.name}", e)
            throw e
        }
    }

    private fun isCached(hash: String) = try {
        dataDir.resolve("$hash.json").exists()
    } catch (e: Exception) {
        logger.error("Failed to check cache for $hash", e)
        false
    }

    fun buildHash(base: String, target: String, t: Float, blender: SkinBlender = this.blender): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val input = "$base-$target-${blender.id}-${"%.4f".format(t)}"
            val bytes = md.digest(input.toByteArray())
            bytes.toHexString()
        } catch (e: Exception) {
            logger.error("Failed to build hash for $base -> $target t=$t", e)
            UUID.randomUUID().toString().replace("-", "")
        }
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
            fun getOrNull(name: String) = name.uppercase().let { name -> entries.find { it.name == name } }
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}