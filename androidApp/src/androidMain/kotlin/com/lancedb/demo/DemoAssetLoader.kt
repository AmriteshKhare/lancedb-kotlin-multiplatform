package com.lancedb.demo

import android.content.Context
import java.io.File

class DemoAssetLoader(private val context: Context) {

    private val filesRoot: File
        get() = context.filesDir

    fun databasePath(): String = File(filesRoot, "lancedb").absolutePath

    fun modelsPath(): String = File(filesRoot, "models").absolutePath

    suspend fun prepareFiles() {
        copyAssetDir("models", File(filesRoot, "models"))
    }

    fun readSeedJson(): String =
        context.assets.open("recipes_seed.json").bufferedReader().use { it.readText() }

    private fun copyAssetDir(assetDir: String, targetDir: File) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val entries = context.assets.list(assetDir) ?: return
        for (name in entries) {
            val assetPath = "$assetDir/$name"
            val outFile = File(targetDir, name)
            if (context.assets.list(assetPath)?.isNotEmpty() == true) {
                copyAssetDir(assetPath, outFile)
            } else {
                if (!outFile.exists() || outFile.length() == 0L) {
                    context.assets.open(assetPath).use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
}
