package com.example.analysis

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayInputStream

sealed class AnalysisPipelineEvent {
    object Started : AnalysisPipelineEvent()
    data class Result(val hosts: List<HostResult>) : AnalysisPipelineEvent()
    data class Error(val message: String) : AnalysisPipelineEvent()
}

class AnalysisPipeline(private val context: Context) {

    private val commandLineRunner = CommandLineRunner()
    private val xmlParser = XmlAnalysisParser()

    suspend fun executeAnalysis(assetPath: String, arguments: List<String>): Flow<AnalysisPipelineEvent> = flow {
        emit(AnalysisPipelineEvent.Started)

        try {
            // 1. Prepare binary
            val executablePath = BinaryAssetManager.ensureBinaryAvailable(context, assetPath)

            // 2. Execute the process and collect its output
            val stdoutBuilder = java.lang.StringBuilder()
            val stderrBuilder = java.lang.StringBuilder()

            commandLineRunner.execute(executablePath, arguments).collect { processLine ->
                when (processLine) {
                    is ProcessLine.Stdout -> stdoutBuilder.append(processLine.text).append("\n")
                    is ProcessLine.Stderr -> stderrBuilder.append(processLine.text).append("\n")
                    is ProcessLine.Exit -> {
                        if (processLine.code == 0) {
                            // 3. Process succeeded, parse the XML output
                            val xmlOutput = stdoutBuilder.toString()
                            val inputStream = ByteArrayInputStream(xmlOutput.toByteArray(Charsets.UTF_8))
                            
                            val parsedHosts = xmlParser.parse(inputStream)
                            emit(AnalysisPipelineEvent.Result(parsedHosts))
                        } else {
                            // 4. Process failed, report error
                            val errorMessage = if (stderrBuilder.isNotEmpty()) {
                                stderrBuilder.toString().trim()
                            } else {
                                "Process exited with code ${processLine.code}"
                            }
                            emit(AnalysisPipelineEvent.Error(errorMessage))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 5. Catch any IO, parsing, or coroutine exceptions
            emit(AnalysisPipelineEvent.Error(e.message ?: "An unknown error occurred: ${e.javaClass.simpleName}"))
        }
    }.flowOn(Dispatchers.IO)
}
