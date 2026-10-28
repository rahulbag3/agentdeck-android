package com.agentdeck.app.data

/**
 * Reattaches a task whose modelId no longer exists to an imported model with
 * the same historical name. Used after a local model is removed and imported
 * again under a new id.
 */
internal fun rebindOrphanedTask(
    task: Task,
    modelIds: Set<String>,
    imported: List<ImportedModel>
): Task? {
    if (task.modelId in modelIds) return null
    val local = task.modelId.startsWith("local-") ||
        task.messages.any { it.provider == ModelProvider.LOCAL }
    if (!local) return null

    val historicalName = task.messages.asReversed()
        .firstOrNull {
            it.role == MessageRole.AGENT &&
                it.provider == ModelProvider.LOCAL &&
                !it.modelName.isNullOrBlank()
        }
        ?.modelName
        ?: task.messages.asReversed()
            .firstOrNull { it.role == MessageRole.AGENT && !it.modelName.isNullOrBlank() }
            ?.modelName
        ?: return null

    val target = historicalName.trim().removeSuffix(".gguf")
    val matches = imported.filter {
        it.name.trim().removeSuffix(".gguf").equals(target, ignoreCase = true)
    }
    val model = matches.singleOrNull() ?: return null
    return task.copy(modelId = model.id)
}
