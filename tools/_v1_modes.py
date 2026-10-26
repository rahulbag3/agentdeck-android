import re

# ---------- AgentDeckState.kt ----------
p = 'data/AgentDeckState.kt'
s = open(p, encoding='utf-8').read()

# A) quick session starter (placed next to openNewTask)
anchor = '''    fun openNewTask(kind: TaskKind = TaskKind.CODING) {'''
addition = '''    /** Mode card tap: creates the session immediately and opens the composer. */
    fun startQuickSession(kind: TaskKind) {
        val modelId = defaultModelId()
        val m = model(modelId)
        if (m == null || (!m.isCloud && !m.isLocal)) {
            toast("Add a cloud API or load a local model first")
            openCloudSetup()
            return
        }
        val chat = kind == TaskKind.CHAT
        val stamp = System.currentTimeMillis()
        val id = "t-" + java.util.UUID.randomUUID().toString().take(8)
        val task = Task(
            id = id,
            title = if (chat) "New chat" else "New coding session",
            prompt = "",
            projectId = data.settings.activeProjectId,
            branch = "",
            modelId = modelId,
            status = TaskStatus.NEEDS_INPUT,
            createdAt = stamp,
            updatedAt = stamp,
            kind = kind,
            agentMode = !chat && m.isCloud,
            analysisOnly = false,
            cloudModel = if (m.isCloud) cloudConfig.model else null,
            plan = if (chat) null else cloudPlan(false)
        )
        data = data.copy(tasks = listOf(task) + data.tasks)
        backStack.clear()
        backStack.add(Screen.Root(if (chat) RootTab.TASKS else RootTab.PROJECTS))
        screen = Screen.TaskScreen(id, TaskTab.CHAT)
    }

    fun openNewTask() {'''
assert anchor in s
s = s.replace(anchor, addition, 1)

# A2) title from first message
old_send = '''        val currentTask = task(taskId) ?: return'''
if old_send not in s:
    old_send = '''        val task = task(taskId) ?: return'''
# find sendMessage start to add title auto-set right after task lookup
m = re.search(r'fun sendMessage\(taskId: String, rawText: String\)\s*\{\s*\n(.*?)\n', s)
# simpler: patch at the point the user message is built in each branch is complex; add before dispatch
anchor2 = '''        if (isLocalTask(task)) {'''
title_set = '''        if (task.title == "New chat" || task.title == "New coding session") {
            updateTask(taskId) { it.copy(title = rawText.take(48).ifBlank { it.title }) }
        }
        if (isLocalTask(task)) {'''
assert anchor2 in s
s = s.replace(anchor2, title_set, 1)

# B) remove read-only routing: all cloud tasks run the loop
old_r1 = '''            if (task.agentMode || task.kind == TaskKind.CHAT) runAgentLoop(taskId) else streamCloudReply(taskId, apiKey, cfg, context)'''
new_r1 = '''            runAgentLoop(taskId)'''
assert old_r1 in s
s = s.replace(old_r1, new_r1, 1)
old_r2 = '''            if (task.agentMode || task.kind == TaskKind.CHAT) runAgentLoop(taskId)
            else streamCloudReply(taskId, apiKey, cfg, context, dropTrailingAssistant)'''
new_r2 = '''            runAgentLoop(taskId)'''
assert old_r2 in s
s = s.replace(old_r2, new_r2, 1)

# B2) createTask: no more analysis-only
s = s.replace('analysisOnly = cloud && !chat,', 'analysisOnly = false,', 1)

# C) task.systemPrompt field usage in loop
old_sp = '''            val systemPrompt = ToolParser.buildSystemPrompt(
                coding = coding,
                memorySnapshot = memoryStore.snapshot(),
                instruction = instructions.all().firstOrNull { it.id == newTaskDraft.instructionId }?.body
                    ?: instructions.all().firstOrNull { it.id == "" }?.body,'''
new_sp = '''            val systemPrompt = ToolParser.buildSystemPrompt(
                coding = coding,
                memorySnapshot = memoryStore.snapshot(),
                instruction = initial.systemPrompt.ifBlank { null },'''
assert old_sp in s
s = s.replace(old_sp, new_sp, 1)

# C2) setter for session system prompt
anchor3 = '''    fun saveSshConfig('''
add3 = '''    fun setTaskSystemPrompt(taskId: String, prompt: String) {
        val now = System.currentTimeMillis()
        updateTask(taskId) { it.copy(systemPrompt = prompt.trim().take(4_000), updatedAt = now) }
        toast(if (prompt.isBlank()) "System prompt cleared" else "System prompt saved for this session")
    }

    fun saveSshConfig('''
assert anchor3 in s
s = s.replace(anchor3, add3, 1)
open(p, 'w', encoding='utf-8').write(s)
print('state ok')

# ---------- Models.kt ----------
p = 'data/Models.kt'
s = open(p, encoding='utf-8').read()
old = '''    val analysisOnly: Boolean = false,'''
new = '''    val analysisOnly: Boolean = false,
    /** User-provided system prompt for this session; blank = default behaviour. */
    val systemPrompt: String = "",'''
assert old in s
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('models ok')

# ---------- TaskCodec.kt ----------
p = 'data/local/TaskCodec.kt'
s = open(p, encoding='utf-8').read()
s = s.replace('            put("agentMode", task.agentMode)\n',
              '            put("agentMode", task.agentMode)\n            put("systemPrompt", task.systemPrompt)\n', 1)
s = s.replace('            agentMode = payload.optBoolean("agentMode", false),\n',
              '            agentMode = payload.optBoolean("agentMode", false),\n            systemPrompt = payload.optString("systemPrompt", ""),\n', 1)
open(p, 'w', encoding='utf-8').write(s)
print('codec ok')

# ---------- HomeScreen cards ----------
p = 'ui/HomeScreen.kt'
s = open(p, encoding='utf-8').read()
s = s.replace('onClick = { state.openNewTask(TaskKind.CHAT) },', 'onClick = { state.startQuickSession(TaskKind.CHAT) },', 1)
s = s.replace('onClick = { state.openNewTask(TaskKind.CODING) },', 'onClick = { state.startQuickSession(TaskKind.CODING) },', 1)
open(p, 'w', encoding='utf-8').write(s)
print('home cards ok')

# ---------- TasksScreen new-session button ----------
p = 'ui/TasksScreen.kt'
s = open(p, encoding='utf-8').read()
s = s.replace('AdIconButton(Icons.Outlined.Add, "New task", state::openNewTask, filled = true)',
              'AdIconButton(Icons.Outlined.Add, "New task", { state.startQuickSession(kind) }, filled = true)', 1)
open(p, 'w', encoding='utf-8').write(s)
print('tasks button ok')

# ---------- TaskScreen: strip analysis-only UI ----------
p = 'ui/TaskScreen.kt'
s = open(p, encoding='utf-8').read()
old_top = '''                if (cloud) AnalysisTag() else if (!local) HostTag()'''
new_top = '''                if (!cloud && !local) HostTag()'''
assert old_top in s
s = s.replace(old_top, new_top, 1)

old_banner = '''        if (task.agentMode) {
            InfoBanner(
                title = "Agent task",
                text = "This task works in the project workspace on this phone: reading files, editing, running shell commands and git. Reads run automatically; every write, command, and commit waits for your approval.",
                kind = BannerKind.LOCAL,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        } else if (task.analysisOnly) {
            RepoContextSummary(state, task)
            Spacer(Modifier.height(10.dp))
            InfoBanner(
                title = "Read-only cloud analysis",
                text = if (task.repoContext != null) {
                    "Replies can review the attached snapshot and propose patches. Nothing is written until you apply a proposal from the Changes tab — that opens a pull request on GitHub."
                } else {
                    "This chat can plan and propose patches, but no repository snapshot is attached. Nothing is written until you apply a proposal from the Changes tab."
                },
                kind = BannerKind.CLOUD,
                modifier = Modifier.padding(bottom = 12.dp)
            )'''
new_banner = '''        if (task.analysisOnly) {
            RepoContextSummary(state, task)
            Spacer(Modifier.height(10.dp))
        }
        if (task.agentMode || cloud) {
            InfoBanner(
                title = "Agent session",
                text = if (task.agentMode) {
                    "This task works in the project workspace on this phone: reading files, editing, running shell commands and git. Reads run automatically; every write, command, and commit waits for your approval."
                } else {
                    "Full agent session: web search, page retrieval, APIs, memory, and file tools. Reads run automatically; consequential actions wait for your approval."
                },
                kind = if (task.agentMode) BannerKind.LOCAL else BannerKind.CLOUD,
                modifier = Modifier.padding(bottom = 12.dp)
            )'''
assert old_banner in s
s = s.replace(old_banner, new_banner, 1)

old_hint = '''                cloud -> "Nothing is changed or run here. Apply proposed files from the Changes tab to open a pull request."'''
new_hint = '''                cloud -> "Agent session · tools run with your approval."'''
assert old_hint in s
s = s.replace(old_hint, new_hint, 1)
open(p, 'w', encoding='utf-8').write(s)
print('taskscreen ok')

# ---------- Sheets: system prompt editor in TaskModelsSheet ----------
p = 'ui/Sheets.kt'
s = open(p, encoding='utf-8').read()
anchor = '''    Spacer(Modifier.height(10.dp))
    SectionRow("ON THIS DEVICE") {
        if (LocalEngine.available) AdTag("LLAMA.CPP", GreenSoft, GreenInk) else PlaceholderTag("No engine")
    }'''
addition = '''    Spacer(Modifier.height(12.dp))
    SectionRow("SYSTEM PROMPT · THIS SESSION") {}
    Spacer(Modifier.height(6.dp))
    var sysPrompt by remember(task.id) { mutableStateOf(task.systemPrompt) }
    AdTextField(
        value = sysPrompt,
        onValueChange = { sysPrompt = it },
        placeholder = "Custom instructions for this session…",
        minHeight = 64.dp
    )
    Spacer(Modifier.height(6.dp))
    AdButton(
        "Save system prompt",
        onClick = { state.setTaskSystemPrompt(taskId, sysPrompt) },
        kind = AdButtonKind.OUTLINE,
        small = true,
        enabled = sysPrompt != task.systemPrompt
    )
    Spacer(Modifier.height(10.dp))
    SectionRow("ON THIS DEVICE") {
        if (LocalEngine.available) AdTag("LLAMA.CPP", GreenSoft, GreenInk) else PlaceholderTag("No engine")
    }'''
assert anchor in s
s = s.replace(anchor, addition, 1)
open(p, 'w', encoding='utf-8').write(s)
print('sheets ok')
