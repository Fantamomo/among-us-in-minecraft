package com.fantamomo.mc.amongus.util

enum class JarType {
    /**
     * All Libraries are in the JAR
     *
     * No Libraries are downloaded at runtime
     */
    STANDALONE,

    /**
     * Only the Libraries that cannot be downloaded at runtime are in the JAR
     *
     * Some Libraries are downloaded at runtime
     */
    LITE,

    /**
     * Only the compiled code is in the JAR.
     *
     * The plugin will disable itself.
     */
    THIN,
    UNKNOWN;

    val shouldDownloadDependencies: Boolean
        get() = this != STANDALONE

    companion object {
        fun get(name: String) = entries.find { it.name.equals(name, true) } ?: UNKNOWN
    }
}