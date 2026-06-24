package com.example.analysis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

sealed class ProcessLine {
    data class Stdout(val text: String) : ProcessLine()
    data class Stderr(val text: String) : ProcessLine()
    data class Exit(val code: Int) : ProcessLine()
}

class CommandLineRunner {

    suspend fun execute(executablePath: String, arguments: List<String>): Flow<ProcessLine> = callbackFlow {
        val executableFile = File(executablePath)
        if (!executableFile.exists()) {
            close(IOException("Executable not found at path: $executablePath"))
            return@callbackFlow
        }

        val command = mutableListOf(executablePath)
        command.addAll(arguments)

        val processBuilder = ProcessBuilder(command)

        val process = try {
            processBuilder.start()
        } catch (e: IOException) {
            close(IOException("Failed to start process: ${e.message}", e))
            return@callbackFlow
        }

        val stdoutJob = launch(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        trySend(ProcessLine.Stdout(line!!))
                    }
                }
            } catch (e: Exception) {
                // Ignore expected IOExceptions if process is destroyed
            }
        }

        val stderrJob = launch(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        trySend(ProcessLine.Stderr(line!!))
                    }
                }
            } catch (e: Exception) {
                // Ignore expected IOExceptions if process is destroyed
            }
        }

        launch(Dispatchers.IO) {
            try {
                val exitCode = process.waitFor()
                // Wait for the stream readers to finish before emitting exit
                stdoutJob.join()
                stderrJob.join()
                
                trySend(ProcessLine.Exit(exitCode))
                close()
            } catch (e: InterruptedException) {
                // Handle coroutine cancellation during waitFor
                process.destroy()
            }
        }

        awaitClose {
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)
}
