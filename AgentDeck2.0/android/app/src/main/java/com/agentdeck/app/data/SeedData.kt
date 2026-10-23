package com.agentdeck.app.data

import java.util.concurrent.atomic.AtomicLong

object SeedData {
    private val ids = AtomicLong(0)
    private val now = System.currentTimeMillis()

    private fun id(prefix: String): String = "$prefix-${ids.incrementAndGet()}"
    private fun at(minutesAgo: Long): Long = now - minutesAgo * 60_000L

    private fun message(
        role: MessageRole,
        text: String,
        minutesAgo: Long,
        delivery: DeliveryState = DeliveryState.DELIVERED,
        localPreview: Boolean = false
    ) = Message(id("msg"), role, text, at(minutesAgo), delivery, localPreview)

    private fun event(type: String, text: String, minutesAgo: Long) =
        ActivityEvent(type, text, at(minutesAgo))

    private fun diff(oldStart: Int = 1, newStart: Int = 1, vararg lines: String): List<DiffLine> {
        var old = oldStart
        var new = newStart
        return lines.map { raw ->
            val type = when {
                raw.startsWith("+") -> DiffLineType.ADD
                raw.startsWith("-") -> DiffLineType.DELETE
                else -> DiffLineType.CONTEXT
            }
            val oldNumber = if (type == DiffLineType.ADD) null else old++
            val newNumber = if (type == DiffLineType.DELETE) null else new++
            DiffLine(type, oldNumber, newNumber, raw.drop(1))
        }
    }

    private fun file(
        path: String,
        changeType: ChangeType,
        language: String,
        vararg lines: String,
        oldStart: Int = 1,
        newStart: Int = 1,
        oldPath: String? = null
    ): ChangedFile {
        val diffLines = diff(oldStart, newStart, *lines)
        return ChangedFile(
            id = id("file"),
            path = path,
            oldPath = oldPath,
            changeType = changeType,
            additions = diffLines.count { it.type == DiffLineType.ADD },
            deletions = diffLines.count { it.type == DiffLineType.DELETE },
            language = language,
            diffLines = diffLines
        )
    }

    private fun unavailableFile(
        path: String,
        changeType: ChangeType,
        language: String,
        additions: Int,
        deletions: Int,
        reason: String
    ) = ChangedFile(
        id = id("file"),
        path = path,
        changeType = changeType,
        additions = additions,
        deletions = deletions,
        language = language,
        unavailableReason = reason
    )

    private fun check(
        type: String,
        name: String,
        status: CheckStatus,
        summary: String,
        command: String = "",
        duration: String = "",
        output: String = ""
    ) = CheckRun(id("check"), type, name, status, summary, command, duration, output)

    private fun plan(vararg steps: Pair<String, PlanState>): List<PlanStep> =
        steps.map { PlanStep(it.first, it.second) }

    private val models = listOf(
        ModelOption("claude-3-5", "Claude 3.5", ModelProvider.HOST, "Anthropic"),
        ModelOption("gpt-4o", "GPT-4o", ModelProvider.HOST, "OpenAI"),
        ModelOption("gemini-1-5", "Gemini 1.5 Pro", ModelProvider.HOST, "Google"),
        ModelOption(
            id = "qwen-coder-1-5b",
            name = "Qwen2.5-Coder 1.5B",
            provider = ModelProvider.LOCAL,
            providerLabel = "On this phone",
            quantization = "Q4_K_M",
            sizeGB = 1.1,
            contextK = 8,
            minRamGB = 4,
            localState = LocalModelState.READY
        ),
        ModelOption(
            id = "qwen-coder-3b",
            name = "Qwen2.5-Coder 3B",
            provider = ModelProvider.LOCAL,
            providerLabel = "On this phone",
            quantization = "Q4_K_M",
            sizeGB = 2.0,
            contextK = 8,
            minRamGB = 6,
            localState = LocalModelState.NOT_IMPORTED
        )
    )

    private val projects = listOf(
        Project("orbit-api", "orbit-api", "acme/orbit-api", "main"),
        Project("mobile-ui", "mobile-ui", "acme/mobile-ui", "develop"),
        Project("docs-site", "docs-site", "acme/docs-site", "main")
    )

    fun create(): AgentDeckData {
        val authFile = file(
            "src/routes/auth.ts", ChangeType.MODIFIED, "TypeScript",
            " import { Router } from \"express\";",
            "+import { login, register, getProfile } from \"../controllers/auth\";",
            "+import { verifyToken } from \"../middleware/auth\";",
            " ",
            " const router = Router();",
            "+",
            "+router.post(\"/login\", login);",
            "+router.post(\"/register\", register);",
            "+router.get(\"/me\", verifyToken, getProfile);",
            " ",
            " export default router;"
        )
        val middlewareFile = file(
            "src/middleware/auth.ts", ChangeType.ADDED, "TypeScript",
            "+import type { NextFunction, Request, Response } from \"express\";",
            "+import { verify } from \"jsonwebtoken\";",
            "+import { env } from \"../config/env\";",
            "+",
            "+export function verifyToken(req: Request, res: Response, next: NextFunction) {",
            "+  const header = req.headers.authorization;",
            "+  if (!header?.startsWith(\"Bearer \")) {",
            "+    return res.status(401).json({ error: \"Missing bearer token\" });",
            "+  }",
            "+",
            "+  try {",
            "+    const payload = verify(header.slice(7), env.JWT_SECRET);",
            "+    req.user = payload as { id: string };",
            "+    next();",
            "+  } catch {",
            "+    res.status(401).json({ error: \"Invalid or expired token\" });",
            "+  }",
            "+}",
            oldStart = 0
        )
        val controllerFile = file(
            "src/controllers/auth.ts", ChangeType.ADDED, "TypeScript",
            "+import type { Request, Response } from \"express\";",
            "+import bcrypt from \"bcryptjs\";",
            "+import { sign } from \"jsonwebtoken\";",
            "+import { users } from \"../models/user\";",
            "+",
            "+export async function login(req: Request, res: Response) {",
            "+  const user = await users.findByEmail(req.body.email);",
            "+  const valid = user && await bcrypt.compare(req.body.password, user.passwordHash);",
            "+  if (!valid) return res.status(401).json({ error: \"Invalid credentials\" });",
            "+  res.json({ token: sign({ id: user.id }, env.JWT_SECRET, { expiresIn: \"15m\" }) });",
            "+}",
            oldStart = 0
        )
        val userFile = file(
            "src/models/user.ts", ChangeType.MODIFIED, "TypeScript",
            " export type User = {",
            "   id: string;",
            "   email: string;",
            "+  passwordHash: string;",
            "   createdAt: string;",
            " };",
            " ",
            "-export const users = [];",
            "+export const users = {",
            "+  findByEmail: async (email: string) => db.user.findUnique({ email }),",
            "+  create: async (data: Omit<User, \"id\" | \"createdAt\">) => db.user.create(data),",
            "+};"
        )
        val packageFile = file(
            "package.json", ChangeType.MODIFIED, "JSON",
            "   \"dependencies\": {",
            "     \"express\": \"^4.19.2\",",
            "-    \"jsonwebtoken\": \"^8.5.1\"",
            "+    \"jsonwebtoken\": \"^9.0.2\",",
            "+    \"bcryptjs\": \"^2.4.3\"",
            "   },",
            "   \"devDependencies\": {",
            "+    \"@types/jsonwebtoken\": \"^9.0.6\",",
            "     \"typescript\": \"^5.5.4\""
        )

        val cursorRoute = file(
            "src/routes/tasks.ts", ChangeType.MODIFIED, "TypeScript",
            " import { Router } from \"express\";",
            "-import { listTasks } from \"../controllers/tasks\";",
            "+import { listTasks, listTasksCursor } from \"../controllers/tasks\";",
            " ",
            " const router = Router();",
            " ",
            "-router.get(\"/tasks\", listTasks);",
            "+router.get(\"/tasks\", listTasksCursor);",
            "+router.get(\"/tasks/legacy\", listTasks);",
            " ",
            " export default router;"
        )
        val cursorService = file(
            "src/services/tasks.ts", ChangeType.MODIFIED, "TypeScript",
            " export async function listTasks(projectId: string, page: number) {",
            "-  const pageSize = 20;",
            "-  const offset = page * pageSize;",
            "+  const pageSize = 50;",
            "+  const cursor = decodeCursor(page);",
            " ",
            "-  return db.task.findMany({ where: { projectId }, skip: offset, take: pageSize });",
            "+  return db.task.findMany({",
            "+    where: { projectId },",
            "+    take: pageSize,",
            "+    skip: cursor ? 1 : 0,",
            "+    cursor: cursor ? { id: cursor } : undefined,",
            "+    orderBy: { createdAt: \"desc\" },",
            "+  });",
            " }"
        )

        val corsFile = file(
            "src/config/cors.ts", ChangeType.MODIFIED, "TypeScript",
            " import cors from \"cors\";",
            "+import { env } from \"./env\";",
            " ",
            "-export const corsMiddleware = cors();",
            "+const allowedOrigins = env.ALLOWED_ORIGINS.split(\",\");",
            "+",
            "+export const corsMiddleware = cors({",
            "+  origin(origin, callback) {",
            "+    if (!origin || allowedOrigins.includes(origin)) return callback(null, true);",
            "+    callback(new Error(\"Origin not allowed\"));",
            "+  },",
            "+  credentials: true,",
            "+});"
        )
        val errorFile = file(
            "src/lib/errors.ts", ChangeType.ADDED, "TypeScript",
            "+export class RequestError extends Error {",
            "+  status: number;",
            "+  retryable: boolean;",
            "+",
            "+  constructor(message: string, status: number, retryable: boolean) {",
            "+    super(message);",
            "+    this.name = \"RequestError\";",
            "+    this.status = status;",
            "+    this.retryable = retryable;",
            "+  }",
            "+}",
            "+",
            "+export function toRequestError(error: unknown): RequestError {",
            "+  if (error instanceof RequestError) return error;",
            "+  return new RequestError(\"Upstream request failed\", 502, true);",
            "+}",
            oldStart = 0
        )
        val proxyFile = file(
            "src/routes/proxy.ts", ChangeType.MODIFIED, "TypeScript",
            " import { Router } from \"express\";",
            "+import { toRequestError } from \"../lib/errors\";",
            " ",
            " router.get(\"/proxy\", async (req, res) => {",
            "-  const result = await client.get(req.query.url);",
            "-  res.json(result);",
            "+  try {",
            "+    const result = await client.get(req.query.url);",
            "+    res.json(result);",
            "+  } catch (error) {",
            "+    const mapped = toRequestError(error);",
            "+    res.status(mapped.status).json({ error: mapped.message, retryable: mapped.retryable });",
            "+  }",
            " });"
        )
        val colorsFile = file(
            "src/theme/colors.ts", ChangeType.MODIFIED, "TypeScript",
            " export const palette = {",
            "   background: \"#ffffff\",",
            "   surface: \"#f7f8f9\",",
            "   text: \"#17191c\",",
            "   muted: \"#6e747c\",",
            "+  border: \"#e6e8eb\",",
            "+  accent: \"#2f6bea\",",
            " };",
            "+",
            "+export const darkPalette = {",
            "+  background: \"#111315\",",
            "+  surface: \"#181b1e\",",
            "+  text: \"#f4f5f6\",",
            "+  muted: \"#a7adb5\",",
            "+  border: \"#2a2f35\",",
            "+  accent: \"#6f9bff\",",
            "+};"
        )
        val legacyPaletteFile = file(
            "src/theme/legacyPalette.ts", ChangeType.DELETED, "TypeScript",
            "-export const legacyPalette = {",
            "-  background: \"#ffffff\",",
            "-  surface: \"#f7f8f9\",",
            "-  text: \"#17191c\",",
            "-  muted: \"#6e747c\",",
            "-};",
            "-",
            "-export default legacyPalette;",
            newStart = 0
        )
        val themeProviderFile = file(
            "src/components/ThemeProvider.tsx", ChangeType.RENAMED, "TypeScript",
            "-import { palette } from \"./legacyPalette\";",
            "+import { palette } from \"../theme/colors\";",
            " import type { ReactNode } from \"react\";",
            " ",
            "-export function Provider({ children }: { children: ReactNode }) {",
            "+export function ThemeProvider({ children }: { children: ReactNode }) {",
            "   return <div style={{ background: palette.background }}>{children}</div>;",
            " }",
            "+",
            "+export const Provider = ThemeProvider;",
            oldPath = "src/components/Provider.tsx"
        )
        val docsFile = file(
            "docs/api/rate-limits.md", ChangeType.MODIFIED, "Markdown",
            " # Rate limits",
            " ",
            "-Public endpoints allow 60 requests per minute per API key.",
            "+Public endpoints allow 100 requests per minute per API key.",
            " ",
            " ## Response headers",
            " ",
            " X-RateLimit-Limit: 100",
            "+Retry-After: 30",
            "+",
            "+When a bucket is empty, the API returns `429 Too Many Requests`",
            "+with a `Retry-After` header in seconds."
        )
        val linksFile = file(
            "docs/guides/getting-started.md", ChangeType.MODIFIED, "Markdown",
            " # Getting started",
            " ",
            "-Install the CLI with `npm install -g orbit`.",
            "+Install the CLI with `pnpm add -g @orbit/cli`.",
            " ",
            "-Run `orbit init` in an empty directory.",
            "+Run `orbit init` in an empty directory, then follow the prompts.",
            "+Linking a repository is optional and can be added later."
        )

        val tasks = listOf(
            Task(
                id = "t-1041",
                title = "Implement authentication",
                prompt = "Add JWT-based authentication with login, registration, and protected routes. Keep the setup simple.",
                projectId = "orbit-api",
                branch = "feature/auth",
                modelId = "claude-3-5",
                status = TaskStatus.REVIEW,
                createdAt = at(46),
                updatedAt = at(3),
                messages = listOf(
                    message(MessageRole.USER, "Add JWT-based authentication with login, registration, and protected routes. Keep the setup simple.", 46),
                    message(MessageRole.AGENT, "I'll read the existing project structure and the auth-related code first.", 44),
                    message(MessageRole.AGENT, "Plan:\n1. Add login and registration routes\n2. Add JWT middleware and protect /me\n3. Cover valid, expired, and malformed tokens with tests", 38),
                    message(MessageRole.USER, "Add tests for expired tokens too.", 18),
                    message(MessageRole.AGENT, "Already covered. Expired tokens return a consistent 401 and the suite is green.", 16),
                    message(MessageRole.AGENT, "Authentication is implemented and the checks that ran pass. The changes are ready for your review.", 3)
                ),
                activity = listOf(
                    event("read", "Read src/index.ts and package.json", 45),
                    event("edit", "Added src/routes/auth.ts", 22),
                    event("edit", "Added JWT middleware", 20),
                    event("test", "Ran pnpm test — 18 passed", 4),
                    event("status", "Moved to review", 3)
                ),
                changedFiles = listOf(authFile, middlewareFile, controllerFile, userFile, packageFile),
                checks = listOf(
                    check("test", "Unit tests", CheckStatus.PASSED, "18 passed · 0 failed", "pnpm test", "3.4s", "Test Files  2 passed (2)\nTests       18 passed (18)"),
                    check("lint", "Lint", CheckStatus.PASSED, "0 problems", "pnpm lint", "1.8s", "No issues found."),
                    check("typecheck", "Type check", CheckStatus.PASSED, "No type errors", "pnpm typecheck", "2.6s", "No errors."),
                    check("build", "Build", CheckStatus.NOT_RUN, "No build script configured", "—", "", "This project has no build script, so nothing was run.")
                ),
                plan = plan(
                    "Read project structure" to PlanState.DONE,
                    "Add auth routes" to PlanState.DONE,
                    "Add JWT middleware" to PlanState.DONE,
                    "Run checks" to PlanState.DONE
                )
            ),
            Task(
                id = "t-1047",
                title = "Add cursor pagination",
                prompt = "Add cursor-based pagination to /tasks. Keep the existing page parameter working for one release.",
                projectId = "orbit-api",
                branch = "feat/cursor-pagination",
                modelId = "gpt-4o",
                status = TaskStatus.IN_PROGRESS,
                createdAt = at(65),
                updatedAt = at(1),
                messages = listOf(
                    message(MessageRole.USER, "Add cursor-based pagination to /tasks. Keep the existing page parameter working for one release.", 65),
                    message(MessageRole.AGENT, "I'll keep `page` as a fallback and add `cursor` plus `limit`, capped at 100.", 63),
                    message(MessageRole.USER, "Make sure the response shape doesn't break the mobile client.", 30),
                    message(MessageRole.AGENT, "The existing fields stay untouched. I'm adding `nextCursor` alongside them.", 28),
                    message(MessageRole.AGENT, "Refactoring the query builder now. Two files changed so far.", 2)
                ),
                activity = listOf(
                    event("read", "Read src/routes/tasks.ts", 60),
                    event("edit", "Updated the task query builder", 20),
                    event("edit", "Kept the legacy page handler", 8),
                    event("status", "Running a type check on the new query", 1)
                ),
                changedFiles = listOf(cursorRoute, cursorService),
                plan = plan(
                    "Read current task routes" to PlanState.DONE,
                    "Keep the legacy page path" to PlanState.DONE,
                    "Add cursor query builder" to PlanState.ACTIVE,
                    "Run checks" to PlanState.TODO
                )
            ),
            Task(
                id = "t-1042",
                title = "Add rate limiting",
                prompt = "Add rate limiting to the public endpoints. 100 requests per minute per API key should be enough for now.",
                projectId = "orbit-api",
                branch = "feature/rate-limits",
                modelId = "claude-3-5",
                status = TaskStatus.NEEDS_INPUT,
                createdAt = at(120),
                updatedAt = at(34),
                messages = listOf(
                    message(MessageRole.USER, "Add rate limiting to the public endpoints. 100 requests per minute per API key should be enough for now.", 120),
                    message(MessageRole.AGENT, "I'll key the limiter by API key and return 429 with a Retry-After header when a bucket is empty.", 112),
                    message(MessageRole.AGENT, "Before I continue, I need to add a dependency to the project.", 34)
                ),
                activity = listOf(
                    event("read", "Read the public route definitions", 118),
                    event("edit", "Planned the limiter as keyed middleware", 40),
                    event("permission", "Waiting for permission to install a package", 34)
                ),
                permissionRequest = PermissionRequest(
                    title = "Install a dependency?",
                    body = "The implementation needs a small package to run the token bucket. This runs the package manager once and needs network access for this task only.",
                    command = "pnpm add rate-limiter-flexible"
                ),
                plan = plan(
                    "Read public routes" to PlanState.DONE,
                    "Install rate limiter" to PlanState.ACTIVE,
                    "Implement middleware" to PlanState.TODO,
                    "Add tests" to PlanState.TODO
                )
            ),
            Task(
                id = "t-1044",
                title = "Improve API error handling",
                prompt = "Make failed upstream requests return a useful error with a retry hint instead of a blank 500.",
                projectId = "orbit-api",
                branch = "fix/error-states",
                modelId = "gpt-4o",
                status = TaskStatus.FAILED,
                createdAt = at(180),
                updatedAt = at(90),
                messages = listOf(
                    message(MessageRole.USER, "Make failed upstream requests return a useful error with a retry hint instead of a blank 500.", 180),
                    message(MessageRole.AGENT, "I added an error mapping layer in the API client and mapped upstream failures to 502 responses with a retry hint.", 172),
                    message(MessageRole.AGENT, "The type check fails in src/api/client.ts and I couldn't resolve it before the session stopped.", 92)
                ),
                activity = listOf(
                    event("read", "Read src/api/client.ts", 176),
                    event("edit", "Added an error mapping layer", 150),
                    event("test", "Ran pnpm test — 31 passed", 110),
                    event("error", "Type check failed in src/api/client.ts", 90),
                    event("status", "Session ended", 89)
                ),
                changedFiles = listOf(
                    unavailableFile(
                        "src/api/client.ts", ChangeType.MODIFIED, "TypeScript", 24, 8,
                        "The execution session ended before the full diff was captured."
                    ),
                    errorFile,
                    proxyFile
                ),
                checks = listOf(
                    check("test", "Unit tests", CheckStatus.PASSED, "31 passed · 0 failed", "pnpm test", "4.1s", "Test Files  4 passed (4)\nTests       31 passed (31)"),
                    check("typecheck", "Type check", CheckStatus.FAILED, "1 error", "pnpm typecheck", "2.2s", "src/api/client.ts:42:18 - error TS2345\n\n42   return normalize(error);\n\nArgument of type unknown is not assignable to parameter of type RequestError."),
                    check("lint", "Lint", CheckStatus.SKIPPED, "Stopped after the type-check failure", "pnpm lint", "", "Skipped because an earlier check failed."),
                    check("build", "Build", CheckStatus.NOT_RUN, "Not reached", "pnpm build", "", "Not reached because an earlier check failed.")
                ),
                failure = Failure(
                    title = "Type check failed",
                    summary = "src/api/client.ts(42,18): error TS2345",
                    detail = "Argument of type 'unknown' is not assignable to parameter of type 'RequestError'.",
                    at = at(90)
                ),
                plan = plan(
                    "Read the API client" to PlanState.DONE,
                    "Add error mapping" to PlanState.DONE,
                    "Run checks" to PlanState.FAILED,
                    "Fix the type error" to PlanState.TODO
                )
            ),
            Task(
                id = "t-1045",
                title = "Move token storage to Redis",
                prompt = "Move refresh token storage to Redis and add a migration for existing sessions.",
                projectId = "orbit-api",
                branch = "chore/redis-sessions",
                modelId = "gemini-1-5",
                status = TaskStatus.PENDING,
                createdAt = at(240),
                updatedAt = at(240),
                messages = listOf(
                    message(MessageRole.USER, "Move refresh token storage to Redis and add a migration for existing sessions.", 240)
                ),
                activity = listOf(event("queue", "Queued behind a running task", 240)),
                plan = plan(
                    "Wait for an execution slot" to PlanState.ACTIVE,
                    "Inspect current token storage" to PlanState.TODO,
                    "Add Redis adapter" to PlanState.TODO,
                    "Migrate existing sessions" to PlanState.TODO
                )
            ),
            Task(
                id = "t-1048",
                title = "Explain the type-check failure",
                prompt = "Explain the type-check error in src/api/client.ts and suggest a minimal fix. Do not change any files yet.",
                projectId = "orbit-api",
                branch = "fix/error-states",
                modelId = "qwen-coder-1-5b",
                status = TaskStatus.NEEDS_INPUT,
                createdAt = at(24),
                updatedAt = at(12),
                messages = listOf(
                    message(MessageRole.USER, "Explain the type-check error in src/api/client.ts and suggest a minimal fix. Do not change any files yet.", 24),
                    message(MessageRole.AGENT, "The failure is at src/api/client.ts:42. normalize() receives an unknown value but expects a RequestError. A minimal fix is to narrow the value with toRequestError(error) before calling normalize(). I can outline the patch here; applying it needs the execution host.", 16, localPreview = true),
                    message(MessageRole.AGENT, "Do you want the minimal patch outline, or a short review of the surrounding error handling?", 12, localPreview = true)
                ),
                activity = listOf(
                    event("local", "Answered on this phone (preview)", 16),
                    event("status", "Waiting for your response", 12)
                )
            ),
            Task(
                id = "t-1040",
                title = "Fix mobile layout",
                prompt = "Fix the dashboard layout on small phones. Cards overflow around 375px and the bottom bar covers the composer.",
                projectId = "mobile-ui",
                branch = "fix/small-screens",
                modelId = "gpt-4o",
                status = TaskStatus.IN_PROGRESS,
                createdAt = at(32),
                updatedAt = at(2),
                messages = listOf(
                    message(MessageRole.USER, "Fix the dashboard layout on small phones. Cards overflow around 375px and the bottom bar covers the composer.", 32),
                    message(MessageRole.AGENT, "I can reproduce the overflow at 375px. The stat row has a fixed width, so I'll make it flexible and add safe-area padding to the composer.", 30),
                    message(MessageRole.USER, "Keep the visual style the same, just fix the sizing.", 20),
                    message(MessageRole.AGENT, "Understood. Layout only, no visual changes.", 18)
                ),
                activity = listOf(
                    event("read", "Read Dashboard.css and tokens.css", 28),
                    event("edit", "Made the stat grid responsive", 8),
                    event("status", "Checking 320–430px widths", 2)
                ),
                changedFiles = listOf(
                    file(
                        "src/components/Dashboard.css", ChangeType.MODIFIED, "CSS",
                        " .stats-grid {",
                        "-  display: flex;",
                        "-  overflow-x: auto;",
                        "+  display: grid;",
                        "+  grid-template-columns: repeat(3, minmax(0, 1fr));",
                        "+  gap: 10px;",
                        " }",
                        " ",
                        " .composer {",
                        "+  padding-bottom: calc(12px + env(safe-area-inset-bottom));",
                        " }"
                    ),
                    file(
                        "src/styles/tokens.css", ChangeType.MODIFIED, "CSS",
                        " :root {",
                        "   --safe-bottom: env(safe-area-inset-bottom);",
                        "+  --nav-height: 64px;",
                        "+  --composer-offset: calc(var(--nav-height) + var(--safe-bottom));",
                        " }"
                    )
                ),
                plan = plan(
                    "Reproduce overflow at 375px" to PlanState.DONE,
                    "Make the stat grid responsive" to PlanState.DONE,
                    "Verify 320–430px widths" to PlanState.ACTIVE,
                    "Run tests" to PlanState.TODO
                )
            ),
            Task(
                id = "t-1046",
                title = "Add dark mode tokens",
                prompt = "Add dark mode color tokens without changing the current light theme.",
                projectId = "mobile-ui",
                branch = "feat/dark-tokens",
                modelId = "claude-3-5",
                status = TaskStatus.COMPLETED,
                createdAt = at(1440),
                updatedAt = at(1320),
                messages = listOf(
                    message(MessageRole.USER, "Add dark mode color tokens without changing the current light theme.", 1440),
                    message(MessageRole.AGENT, "Added a dark palette alongside the existing light tokens. Nothing switches automatically yet.", 1400),
                    message(MessageRole.USER, "Can we keep the old palette import working?", 1360),
                    message(MessageRole.AGENT, "Yes. The old import now re-exports the new palette, and the theme provider is renamed with a compatibility export.", 1330),
                    message(MessageRole.AGENT, "The checks that ran pass. The changes are ready for review.", 1325)
                ),
                activity = listOf(
                    event("read", "Read the current palette", 1420),
                    event("edit", "Added dark mode tokens", 1360),
                    event("edit", "Kept the legacy import working", 1335),
                    event("test", "Ran the checks", 1325),
                    event("review", "You approved this work", 1320)
                ),
                changedFiles = listOf(colorsFile, legacyPaletteFile, themeProviderFile),
                checks = listOf(
                    check("test", "Unit tests", CheckStatus.PASSED, "26 passed · 0 failed", "pnpm test", "3.8s", "Test Files  3 passed (3)\nTests       26 passed (26)"),
                    check("lint", "Lint", CheckStatus.PASSED, "0 problems", "pnpm lint", "1.6s", "No issues found."),
                    check("typecheck", "Type check", CheckStatus.PASSED, "No type errors", "pnpm typecheck", "2.4s", "No errors."),
                    check("build", "Build", CheckStatus.NOT_RUN, "No build script configured", "—", "", "This project has no build script, so nothing was run.")
                ),
                decisions = listOf(ReviewDecision("approved", "Nice. Keeping the old import working is exactly what I wanted.", at(1320))),
                plan = plan(
                    "Read the palette" to PlanState.DONE,
                    "Add dark tokens" to PlanState.DONE,
                    "Keep the legacy import" to PlanState.DONE,
                    "Run checks" to PlanState.DONE
                )
            ),
            Task(
                id = "t-1039",
                title = "Update API reference",
                prompt = "Update the rate limit section of the API reference to match the new limiter.",
                projectId = "docs-site",
                branch = "docs/rate-limits",
                modelId = "gpt-4o",
                status = TaskStatus.COMPLETED,
                createdAt = at(2900),
                updatedAt = at(2850),
                messages = listOf(
                    message(MessageRole.USER, "Update the rate limit section of the API reference to match the new limiter.", 2900),
                    message(MessageRole.AGENT, "Updated the limits, the 429 example, and the Retry-After guidance.", 2880),
                    message(MessageRole.AGENT, "The link check and Markdown lint pass. Ready for review.", 2860)
                ),
                activity = listOf(
                    event("read", "Read docs/api/rate-limits.md", 2890),
                    event("edit", "Updated the rate limit reference", 2880),
                    event("test", "Ran the documentation checks", 2860),
                    event("review", "You approved this work", 2850)
                ),
                changedFiles = listOf(docsFile),
                checks = listOf(
                    check("custom", "Link check", CheckStatus.PASSED, "42 links checked", "pnpm check:links", "2.1s", "42 links checked\n0 broken links"),
                    check("lint", "Markdown lint", CheckStatus.PASSED, "0 problems", "pnpm lint:docs", "0.9s", "No issues found.")
                ),
                decisions = listOf(ReviewDecision("approved", "", at(2850))),
                plan = plan(
                    "Read the current reference" to PlanState.DONE,
                    "Update limits and examples" to PlanState.DONE,
                    "Run documentation checks" to PlanState.DONE
                )
            ),
            Task(
                id = "t-1038",
                title = "Fix broken links",
                prompt = "Fix the broken links reported in the docs build.",
                projectId = "docs-site",
                branch = "docs/link-fixes",
                modelId = "claude-3-5",
                status = TaskStatus.COMPLETED,
                createdAt = at(4320),
                updatedAt = at(4270),
                messages = listOf(
                    message(MessageRole.USER, "Fix the broken links reported in the docs build.", 4320),
                    message(MessageRole.AGENT, "Fixed three links in the getting started guide and removed one link to a retired page.", 4300),
                    message(MessageRole.AGENT, "No checks are configured for this project, so I could not run validation.", 4280)
                ),
                activity = listOf(
                    event("read", "Read the getting started guide", 4310),
                    event("edit", "Fixed three links", 4300),
                    event("review", "You approved this work", 4270)
                ),
                changedFiles = listOf(linksFile),
                decisions = listOf(ReviewDecision("approved", "", at(4270))),
                plan = plan(
                    "Read the failing pages" to PlanState.DONE,
                    "Fix the links" to PlanState.DONE,
                    "Check the docs build" to PlanState.DONE
                )
            ),
            Task(
                id = "t-1037",
                title = "Harden CORS headers",
                prompt = "Replace the permissive CORS policy with an allowlist from environment settings.",
                projectId = "orbit-api",
                branch = "fix/cors",
                modelId = "claude-3-5",
                status = TaskStatus.COMPLETED,
                createdAt = at(5760),
                updatedAt = at(5700),
                messages = listOf(
                    message(MessageRole.USER, "Replace the permissive CORS policy with an allowlist from environment settings.", 5760),
                    message(MessageRole.AGENT, "Added an allowlist from ALLOWED_ORIGINS and kept credentials enabled for trusted origins.", 5740),
                    message(MessageRole.AGENT, "Tests and the type check pass. Ready for review.", 5710)
                ),
                activity = listOf(
                    event("read", "Read the current CORS config", 5750),
                    event("edit", "Added an origin allowlist", 5740),
                    event("test", "Ran tests and the type check", 5710),
                    event("review", "You approved this work", 5700)
                ),
                changedFiles = listOf(corsFile),
                checks = listOf(
                    check("test", "Unit tests", CheckStatus.PASSED, "24 passed · 0 failed", "pnpm test", "3.6s", "Test Files  3 passed (3)\nTests       24 passed (24)"),
                    check("typecheck", "Type check", CheckStatus.PASSED, "No type errors", "pnpm typecheck", "2.1s", "No errors.")
                ),
                decisions = listOf(ReviewDecision("approved", "", at(5700))),
                plan = plan(
                    "Read CORS config" to PlanState.DONE,
                    "Add allowlist" to PlanState.DONE,
                    "Run checks" to PlanState.DONE
                )
            ),
            Task(
                id = "t-1036",
                title = "Rotate signing keys",
                prompt = "Add support for a secondary signing key so keys can rotate without invalidating sessions.",
                projectId = "orbit-api",
                branch = "feat/key-rotation",
                modelId = "claude-3-5",
                status = TaskStatus.COMPLETED,
                createdAt = at(7200),
                updatedAt = at(7140),
                messages = listOf(
                    message(MessageRole.USER, "Add support for a secondary signing key so keys can rotate without invalidating sessions.", 7200),
                    message(MessageRole.AGENT, "Verification now tries the active key first, then the previous key. New tokens are only signed with the active key.", 7180),
                    message(MessageRole.AGENT, "This project has no automated checks configured, so I validated the change manually.", 7160)
                ),
                activity = listOf(
                    event("read", "Read the signing configuration", 7190),
                    event("edit", "Added secondary key verification", 7180),
                    event("review", "You approved this work", 7140)
                ),
                changedFiles = listOf(
                    file(
                        "src/auth/keys.ts", ChangeType.MODIFIED, "TypeScript",
                        " export function verifyToken(token: string) {",
                        "-  return verify(token, activeKey);",
                        "+  for (const key of [activeKey, previousKey]) {",
                        "+    try {",
                        "+      return verify(token, key);",
                        "+    } catch {",
                        "+      continue;",
                        "+    }",
                        "+  }",
                        "+  throw new Error(\"Invalid token signature\");",
                        " }"
                    )
                ),
                decisions = listOf(ReviewDecision("approved", "", at(7140))),
                plan = plan(
                    "Read signing config" to PlanState.DONE,
                    "Add secondary key" to PlanState.DONE,
                    "Validate manually" to PlanState.DONE
                )
            )
        )

        return AgentDeckData(
            host = Host("Studio Mac", "macOS", "Encrypted tunnel", connected = true, lastSeen = now),
            projects = projects,
            models = models,
            settings = AppSettings(
                activeProjectId = "orbit-api",
                defaultProjectId = "orbit-api",
                defaultModelId = "claude-3-5"
            ),
            tasks = tasks
        )
    }
}
