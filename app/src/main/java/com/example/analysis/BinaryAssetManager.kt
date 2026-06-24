package com.example.analysis

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object BinaryAssetManager {

    /**
     * Ensures that the specified binary asset is available in the app's private files directory,
     * and that it has the correct size and executable permissions.
     *
     * @param context The application context.
     * @param assetPath The path to the binary in the assets folder.
     * @return The absolute path to the ready-to-use executable binary.
     * @throws IOException If there is an error reading the asset or writing the file.
     */
    suspend fun ensureBinaryAvailable(context: Context, assetPath: String): String = withContext(Dispatchers.IO) {
        val fileName = File(assetPath).name
        val targetFile = File(context.filesDir, fileName)

        val assetManager = context.assets

        // Determine the expected size of the asset
        val expectedSize: Long = try {
            assetManager.openFd(assetPath).use { it.length }
        } catch (e: Exception) {
            // openFd might fail if the file is compressed in the APK, 
            // fallback to reading available bytes
            assetManager.open(assetPath).use { it.available().toLong() }
        }

        // Check if the file already exists and matches the expected size
        if (targetFile.exists() && targetFile.length() == expectedSize) {
            // Ensure it is executable in case permissions were somehow lost
            if (!targetFile.canExecute()) {
                targetFile.setExecutable(true, false)
            }
            return@withContext targetFile.absolutePath
        }

        // Copy the file if it doesn't exist or size is mismatched
        try {
            assetManager.open(assetPath).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: IOException) {
            throw IOException("Failed to copy binary asset from $assetPath to ${targetFile.absolutePath}", e)
        }

        // Set the file as executable (owner only)
        val success = targetFile.setExecutable(true, false)
        if (!success) {
            throw IOException("Failed to set executable permissions on ${targetFile.absolutePath}")
        }

        return@withContext targetFile.absolutePath
    }
}
