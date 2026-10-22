package com.easylife.app.core.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class TaskPrompt(val instruction: String, val schema: JsonElement? = null)

/** The bundle produced from /ai-prompts by the Gradle task (also the default of Remote Config `ai_prompts`). */
@Serializable
data class PromptBundle(
    val version: String,
    val system: String,
    val tasks: Map<String, TaskPrompt>,
) {
    /** Full prompt text for a cloud provider: task instruction + schema + the (already masked) input. */
    fun userPrompt(taskKey: String, inputText: String?, params: Map<String, String>): String? {
        val task = tasks[taskKey] ?: return null
        return buildString {
            appendLine(task.instruction)
            task.schema?.let {
                appendLine()
                appendLine("Reply with JSON matching this schema:")
                appendLine(it.toString())
            }
            if (params.isNotEmpty()) {
                appendLine()
                appendLine("Input parameters:")
                params.forEach { (k, v) -> appendLine("- $k: $v") }
            }
            if (!inputText.isNullOrBlank()) {
                appendLine()
                appendLine("Input text:")
                appendLine("\"\"\"")
                appendLine(inputText)
                appendLine("\"\"\"")
            }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): PromptBundle = json.decodeFromString(serializer(), text)
    }
}
