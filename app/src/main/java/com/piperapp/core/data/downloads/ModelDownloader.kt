package com.piperapp.core.data.downloads

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ModelDownloader(private val manifestUrl: String, private val filesDir: File) {
    data class ModelInfo(
        val voiceId: String,
        val url: String,
        val sha256: String,
        val sizeBytes: Long,
        val version: String
    )

    private val client = OkHttpClient.Builder().build()

    fun downloadModel(info: ModelInfo, progress: (Float) -> Unit = {}): File {
        val dir = File(filesDir, "models/${info.voiceId}")
        dir.mkdirs()
        val temp = File(dir, ".tmp.${info.voiceId}")
        val target = File(dir, "model.onnx")

        val request = Request.Builder().url(info.url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed: ${response.code}")
            temp.outputStream().use { out ->
                response.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                }
            }
        }

        if (info.sha256.isNotBlank()) {
            val digest = MessageDigest.getInstance("SHA-256")
            temp.inputStream().use { input ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } != -1) digest.update(buf, 0, read)
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (hex != info.sha256) throw SecurityException("SHA-256 mismatch for ${info.voiceId}")
        }

        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) throw IOException("Atomic rename failed")
        return target
    }
}
