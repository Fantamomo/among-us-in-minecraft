package com.fantamomo.mc.amongus.util.internal

import com.destroystokyo.paper.profile.PlayerProfile
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.data.AmongUsConfig
import com.fantamomo.mc.amongus.data.AmongUsSecrets
import com.fantamomo.mc.amongus.util.safeCreateDirectories
import com.fantamomo.mc.amongus.util.skinblender.SkinBlender
import com.fantamomo.mc.amongus.util.skinblender.VirusSkinBlender
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.mineskin.JsoupRequestHandler
import org.mineskin.MineSkinClient
import org.mineskin.data.JobInfo
import org.mineskin.data.Visibility
import org.mineskin.request.GenerateRequest
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

@Suppress("OPT_IN_USAGE")
object MorphSkinManager {

    private val logger = LoggerFactory.getLogger("AmongUs-MorphSkinManager")

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

    private val blender: SkinBlender by lazy {
        val selected = AmongUsConfig.MorphBlender.blender
        SkinBlender.blenders.firstOrNull { it.id == selected } ?: VirusSkinBlender
    }

    private val json = Json {
        prettyPrint = AmongUs.IN_DEVELOPMENT
        ignoreUnknownKeys = true
    }

    init {
        skinDir.safeCreateDirectories()
        dataDir.safeCreateDirectories()
    }

    fun isValid() = API_KEY.isNotBlank() && AmongUsConfig.MorphBlender.enabled
    private fun checkValid() = require(isValid()) { "MineSkin API key cannot be blank" }

    fun pregenerateFromProfiles(
        baseProfile: PlayerProfile,
        targetProfile: PlayerProfile,
        variants: Int
    ): CompletableFuture<List<Skin>> {

        checkValid()

        return CompletableFuture.supplyAsync {
            val baseSkin = fetchSkinFromProfile(baseProfile)
            val targetSkin = fetchSkinFromProfile(targetProfile)

            val baseId = baseProfile.id.toString()
            val targetId = targetProfile.id.toString()

            pregenerate(baseSkin, targetSkin, baseId, targetId, variants, baseProfile, targetProfile).join()
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
                .thenApply { skinData ->
                    Skin.GeneratedSkin(
                        hash = hash,
                        t = t,
                        pngFile = pngFile,
                        value = skinData.value,
                        signature = skinData.signature
                    )
                }

            futures += future
        }

        return CompletableFuture
            .allOf(*futures.toTypedArray())
            .thenApply {
                futures.map { it.join() }
                    .sortedBy { it.t }
            }
    }

    fun getTexture(hash: String): SkinData? {
        val file = dataDir.resolve("$hash.json").toFile()
        if (!file.exists()) return null
        return json.decodeFromStream<SkinData>(file.inputStream())
    }

    private fun uploadToMineSkin(
        file: File,
        hash: String
    ): CompletableFuture<SkinData> {

        val request = GenerateRequest.upload(file)
            .name("Morph-$hash".take(20))
            .visibility(Visibility.PUBLIC)

        return client.queue().submit(request)
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

                val outFile = dataDir.resolve("$hash.json").toFile()
                json.encodeToStream(skinData, outFile.outputStream())

                logger.debug("Uploaded & cached: $hash")

                skinData
            }
            .exceptionally { throwable ->
                logger.error("MineSkin upload failed for $hash", throwable)
                throw RuntimeException(throwable)
            }
    }

    private fun fetchSkinFromProfile(profile: PlayerProfile): BufferedImage {
        val property = profile.properties.firstOrNull { it.name == "textures" }
            ?: throw IllegalStateException("Profile has no textures")

        val decoded = String(Base64.getDecoder().decode(property.value))
        val urlRegex = """"url"\s*:\s*"([^"]+)"""".toRegex()
        val match = urlRegex.find(decoded) ?: throw IllegalStateException("Cannot parse skin url")
        val url = match.groupValues[1]

        @Suppress("DEPRECATION")
        return ImageIO.read(URL(url))
    }

    private fun isCached(hash: String): Boolean {
        return dataDir.resolve("$hash.json").toFile().exists()
    }

    fun buildHash(base: String, target: String, t: Float, blender: SkinBlender = this.blender): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = "$base-$target-${blender.id}-${"%.4f".format(t)}"
        val bytes = md.digest(input.toByteArray())
        return bytes.toHexString()
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
}