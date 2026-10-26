import re

# 1) AppConfig: agent behavior + global system prompt
p = 'data/AppConfig.kt'
s = open(p, encoding='utf-8').read()
if 'agentBehavior' not in s:
    s = s.replace('''    var sshEnabled: Boolean''', '''    /** "permissions" (default policies) or "prompt" (user's global system prompt). */
    var agentBehavior: String
        get() = prefs.getString(KEY_AGENT_BEHAVIOR, "permissions").orEmpty()
        set(value) = prefs.edit().putString(KEY_AGENT_BEHAVIOR, value).apply()

    /** Global system prompt used by agent sessions when behavior is "prompt". */
    var globalSystemPrompt: String
        get() = prefs.getString(KEY_GLOBAL_PROMPT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_GLOBAL_PROMPT, value).apply()

    var sshEnabled: Boolean''', 1)
    s = s.replace('        private const val KEY_SSH_ENABLED = "ssh.enabled"',
                  '        private const val KEY_AGENT_BEHAVIOR = "agent.behavior"\n        private const val KEY_GLOBAL_PROMPT = "agent.global-prompt"\n        private const val KEY_SSH_ENABLED = "ssh.enabled"', 1)
    open(p, 'w', encoding='utf-8').write(s)
print('config ok')

# 2) AgentDeckState: free-first sort + model fallback + behavior wiring
p = 'data/AgentDeckState.kt'
s = open(p, encoding='utf-8').read()

old = '''                onSuccess = { ids ->
                    fetchedCloudModels = ids
                    toast("${ids.size} models available on ${profile.label}")
                },'''
new = '''                onSuccess = { ids ->
                    // Free models first, then alphabetical.
                    fetchedCloudModels = ids.sortedWith(
                        compareByDescending<String> { it.endsWith(":free", ignoreCase = true) }
                            .thenBy { it.lowercase() }
                    )
                    toast("${ids.size} models available on ${profile.label}")
                },'''
assert old in s
s = s.replace(old, new, 1)

# loop: model fallback across the profile's saved models
old_loop = '''            val baseTurns = buildCloudTurns(initial.messages)
            var extraTurns = emptyList<CloudTurn>()
            try {
                for (step in 0 until MAX_AGENT_STEPS) {
                    currentCoroutineContext().ensureActive()
                    val replyText = if (usingCloud) {
                        streamAgentReply(taskId, apiKey.orEmpty(), cfg, initial, baseTurns + extraTurns, systemPrompt)
                    } else {
                        localAgentReply(taskId, initial, baseTurns + extraTurns, systemPrompt)
                    } ?: return@launch'''
new_loop = '''            val baseTurns = buildCloudTurns(initial.messages)
            var extraTurns = emptyList<CloudTurn>()
            // On provider errors (401/429/5xx), fall back to the next saved
            // model of the active profile before giving up.
            val modelCandidates = mutableListOf(cfg.model)
            activeCloudProfile()?.models?.forEach { if (it !in modelCandidates) modelCandidates.add(it) }
            var candidateIndex = 0
            try {
                for (step in 0 until MAX_AGENT_STEPS) {
                    currentCoroutineContext().ensureActive()
                    val replyText = if (usingCloud) {
                        streamAgentReply(
                            taskId, apiKey.orEmpty(), cfg.copy(model = modelCandidates[candidateIndex]),
                            initial, baseTurns + extraTurns, systemPrompt,
                            failHard = candidateIndex >= modelCandidates.size - 1
                        )
                    } else {
                        localAgentReply(taskId, initial, baseTurns + extraTurns, systemPrompt)
                    } ?: run {
                        candidateIndex++
                        if (usingCloud && candidateIndex < modelCandidates.size) {
                            val next = modelCandidates[candidateIndex]
                            updateTask(taskId) {
                                it.copy(
                                    cloudModel = next,
                                    updatedAt = System.currentTimeMillis(),
                                    activity = it.activity + ActivityEvent("cloud", "Provider error · retrying with $next", System.currentTimeMillis())
                                )
                            }
                            null
                        } else {
                            return@launch
                        }
                    }
                    if (replyText == null) continue'''
assert old_loop in s
s = s.replace(old_loop, new_loop, 1)

# streamAgentReply: failHard param
old_sig = '''        turns: List<CloudTurn>,
        systemPrompt: String
    ): String? {'''
new_sig = '''        turns: List<CloudTurn>,
        systemPrompt: String,
        failHard: Boolean = true
    ): String? {'''
assert old_sig in s
s = s.replace(old_sig, new_sig, 1)

old_fail = '''            onFailure = { error ->
                if (added) {
                    updateMessage(taskId, replyId) { it.copy(text = "$text\\n\\n[stream interrupted]", at = now) }
                }
                agentFailure(taskId, error.message ?: "The request failed.")
                null
            }'''
new_fail = '''            onFailure = { error ->
                if (added) {
                    updateMessage(taskId, replyId) { it.copy(text = "$text\\n\\n[stream interrupted]", at = now) }
                }
                if (failHard) {
                    agentFailure(taskId, error.message ?: "The request failed.")
                }
                null
            }'''
assert old_fail in s
s = s.replace(old_fail, new_fail, 1)

# behavior wiring: loop instruction falls back to global prompt when behavior=prompt
old_sp = '''                instruction = initial.systemPrompt.ifBlank { null },'''
new_sp = '''                instruction = initial.systemPrompt.ifBlank {
                    if (config.agentBehavior == "prompt") config.globalSystemPrompt.ifBlank { null } else null
                },'''
assert old_sp in s
s = s.replace(old_sp, new_sp, 1)

# setters
anchor = '''    fun setTaskSystemPrompt('''
add = '''    fun setAgentBehavior(mode: String) {
        config.agentBehavior = mode
    }

    fun setGlobalSystemPrompt(prompt: String) {
        config.globalSystemPrompt = prompt.trim().take(4_000)
    }

    fun setTaskSystemPrompt('''
assert anchor in s
s = s.replace(anchor, add, 1)
open(p, 'w', encoding='utf-8').write(s)
print('state ok')
