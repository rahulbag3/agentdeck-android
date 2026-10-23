(function () {
  'use strict';

  const now = Date.now();
  let seq = 0;
  const uid = (prefix) => `${prefix}_${(++seq).toString(36)}`;
  const iso = (minutesAgo) => new Date(now - minutesAgo * 60000).toISOString();

  function mkDiff(oldStart, newStart, lines) {
    let oldN = oldStart;
    let newN = newStart;
    return lines.map((line) => {
      const type = line[0];
      const text = line.slice(1);
      const row = { type, text };
      if (type === ' ') {
        row.old = oldN++;
        row.new = newN++;
      } else if (type === '-') {
        row.old = oldN++;
        row.new = null;
      } else {
        row.old = null;
        row.new = newN++;
      }
      return row;
    });
  }

  function lang(path) {
    if (path.endsWith('.ts') || path.endsWith('.tsx')) return 'TypeScript';
    if (path.endsWith('.css')) return 'CSS';
    if (path.endsWith('.md')) return 'Markdown';
    if (path.endsWith('.json')) return 'JSON';
    return 'Text';
  }

  function file(path, status, additions, deletions, lines, opts) {
    const options = opts || {};
    const oldStart = options.oldStart == null ? 1 : options.oldStart;
    const newStart = options.newStart == null ? 1 : options.newStart;
    return {
      id: uid('f'),
      path,
      oldPath: options.oldPath || '',
      status,
      additions,
      deletions,
      language: lang(path),
      diff: lines ? mkDiff(oldStart, newStart, lines) : null,
      unavailable: Boolean(options.unavailable),
      unavailableReason: options.reason || ''
    };
  }

  const msg = (role, text, minutesAgo, extra) => Object.assign({
    id: uid('m'),
    role,
    text,
    at: iso(minutesAgo),
    state: 'delivered'
  }, extra || {});

  const ev = (type, text, minutesAgo) => ({
    id: uid('e'),
    type,
    text,
    at: iso(minutesAgo)
  });

  const check = (type, name, status, summary, minutesAgo, extra) => Object.assign({
    id: uid('c'),
    type,
    name,
    status,
    summary,
    at: iso(minutesAgo),
    command: '',
    duration: '',
    output: ''
  }, extra || {});

  const decision = (value, feedback, minutesAgo) => ({
    id: uid('d'),
    decision: value,
    feedback,
    by: 'You',
    at: iso(minutesAgo)
  });

  const plan = (items) => items.map((item) => ({ text: item[0], state: item[1] }));

  const projects = [
    { id: 'orbit-api', name: 'orbit-api', repo: 'acme/orbit-api', defaultBranch: 'main', description: 'Core API service' },
    { id: 'mobile-ui', name: 'mobile-ui', repo: 'acme/mobile-ui', defaultBranch: 'develop', description: 'Mobile web client' },
    { id: 'docs-site', name: 'docs-site', repo: 'acme/docs-site', defaultBranch: 'main', description: 'Developer documentation' }
  ];

  const agents = [
    { id: 'claude-3-5', name: 'Claude 3.5', provider: 'host', providerLabel: 'Anthropic' },
    { id: 'gpt-4o', name: 'GPT-4o', provider: 'host', providerLabel: 'OpenAI' },
    { id: 'gemini-1-5', name: 'Gemini 1.5 Pro', provider: 'host', providerLabel: 'Google' },
    {
      id: 'qwen-coder-1-5b',
      name: 'Qwen2.5-Coder 1.5B',
      provider: 'local',
      providerLabel: 'On this phone',
      runtime: 'llama.cpp',
      quantization: 'Q4_K_M',
      sizeGB: 1.1,
      contextK: 8,
      minRamGB: 4,
      state: 'ready',
      loaded: true
    },
    {
      id: 'qwen-coder-3b',
      name: 'Qwen2.5-Coder 3B',
      provider: 'local',
      providerLabel: 'On this phone',
      runtime: 'llama.cpp',
      quantization: 'Q4_K_M',
      sizeGB: 2.0,
      contextK: 8,
      minRamGB: 6,
      state: 'not_imported',
      loaded: false
    }
  ];

  const tasks = [
    {
      id: 't_1041',
      title: 'Implement authentication',
      prompt: 'Add JWT-based authentication with login, registration, and protected routes. Keep the setup simple.',
      projectId: 'orbit-api',
      branch: 'feature/auth',
      agentId: 'claude-3-5',
      status: 'review',
      createdAt: iso(46),
      updatedAt: iso(3),
      messages: [
        msg('user', 'Add JWT-based authentication with login, registration, and protected routes. Keep the setup simple.', 46),
        msg('agent', "I'll read the existing project structure and the auth-related code first.", 44),
        msg('agent', 'Plan:\n1. Add login and registration routes\n2. Add JWT middleware and protect /me\n3. Cover valid, expired, and malformed tokens with tests', 38),
        msg('user', 'Add tests for expired tokens too.', 18),
        msg('agent', 'Already covered. Expired tokens return a consistent 401 and the suite is green.', 16),
        msg('agent', 'Authentication is implemented and the checks that ran pass. The changes are ready for your review.', 3)
      ],
      activity: [
        ev('read', 'Read src/index.ts and package.json', 45),
        ev('edit', 'Added src/routes/auth.ts', 22),
        ev('edit', 'Added JWT middleware', 20),
        ev('test', 'Ran pnpm test — 18 passed', 4),
        ev('status', 'Moved to review', 3)
      ],
      changedFiles: [
        file('src/routes/auth.ts', 'modified', 7, 0, [
          ' import { Router } from "express";',
          '+import { login, register, getProfile } from "../controllers/auth";',
          '+import { verifyToken } from "../middleware/auth";',
          ' ',
          ' const router = Router();',
          '+',
          '+router.post("/login", login);',
          '+router.post("/register", register);',
          '+router.get("/me", verifyToken, getProfile);',
          ' ',
          ' export default router;'
        ], { oldStart: 1, newStart: 1 }),
        file('src/middleware/auth.ts', 'added', 20, 0, [
          '+import type { NextFunction, Request, Response } from "express";',
          '+import { verify } from "jsonwebtoken";',
          '+import { env } from "../config/env";',
          '+',
          '+export function verifyToken(req: Request, res: Response, next: NextFunction) {',
          '+  const header = req.headers.authorization;',
          '+  if (!header?.startsWith("Bearer ")) {',
          '+    return res.status(401).json({ error: "Missing bearer token" });',
          '+  }',
          '+',
          '+  try {',
          '+    const payload = verify(header.slice(7), env.JWT_SECRET);',
          '+    req.user = payload as { id: string };',
          '+    next();',
          '+  } catch {',
          '+    res.status(401).json({ error: "Invalid or expired token" });',
          '+  }',
          '+}'
        ], { oldStart: 0, newStart: 1 }),
        file('src/controllers/auth.ts', 'added', 26, 0, [
          '+import type { Request, Response } from "express";',
          '+import bcrypt from "bcryptjs";',
          '+import { sign } from "jsonwebtoken";',
          '+import { env } from "../config/env";',
          '+import { users } from "../models/user";',
          '+',
          '+export async function login(req: Request, res: Response) {',
          '+  const { email, password } = req.body;',
          '+  const user = await users.findByEmail(email);',
          '+  if (!user || !(await bcrypt.compare(password, user.passwordHash))) {',
          '+    return res.status(401).json({ error: "Invalid credentials" });',
          '+  }',
          '+  const token = sign({ id: user.id }, env.JWT_SECRET, { expiresIn: "15m" });',
          '+  res.json({ token });',
          '+}',
          '+',
          '+export async function register(req: Request, res: Response) {',
          '+  const { email, password } = req.body;',
          '+  const passwordHash = await bcrypt.hash(password, 12);',
          '+  const user = await users.create({ email, passwordHash });',
          '+  res.status(201).json({ id: user.id });',
          '+}',
          '+',
          '+export async function getProfile(req: Request, res: Response) {',
          '+  const user = await users.findById(req.user.id);',
          '+  res.json({ id: user.id, email: user.email });',
          '+}'
        ], { oldStart: 0, newStart: 1 }),
        file('src/models/user.ts', 'modified', 6, 1, [
          ' export type User = {',
          '   id: string;',
          '   email: string;',
          '+  passwordHash: string;',
          '   createdAt: string;',
          ' };',
          ' ',
          '-export const users = [];',
          '+export const users = {',
          '+  findByEmail: async (email: string) => db.user.findUnique({ email }),',
          '+  findById: async (id: string) => db.user.findUnique({ id }),',
          '+  create: async (data: Omit<User, "id" | "createdAt">) => db.user.create(data),',
          '+};'
        ], { oldStart: 1, newStart: 1 }),
        file('package.json', 'modified', 3, 1, [
          '   "dependencies": {',
          '     "express": "^4.19.2",',
          '-    "jsonwebtoken": "^8.5.1"',
          '+    "jsonwebtoken": "^9.0.2",',
          '+    "bcryptjs": "^2.4.3"',
          '   },',
          '   "devDependencies": {',
          '+    "@types/jsonwebtoken": "^9.0.6",',
          '     "typescript": "^5.5.4"'
        ], { oldStart: 12, newStart: 12 })
      ],
      checks: [
        check('test', 'Unit tests', 'passed', '18 passed · 0 failed', 4, {
          command: 'pnpm test',
          duration: '3.4s',
          output: '✓ auth (12)\n  ✓ accepts a valid token\n  ✓ rejects expired tokens\n  ✓ rejects malformed headers\n✓ users (6)\n\nTest Files  2 passed (2)\nTests       18 passed (18)'
        }),
        check('lint', 'Lint', 'passed', '0 problems', 4, {
          command: 'pnpm lint',
          duration: '1.8s',
          output: 'eslint . --max-warnings 0\n\nNo issues found.'
        }),
        check('typecheck', 'Type check', 'passed', 'No type errors', 3, {
          command: 'pnpm typecheck',
          duration: '2.6s',
          output: 'tsc --noEmit\n\nNo errors.'
        }),
        check('build', 'Build', 'not_run', 'No build script configured', 3, {
          command: '—',
          output: 'This project has no build script, so nothing was run.'
        })
      ],
      decisions: [],
      plan: plan([
        ['Read project structure', 'done'],
        ['Add auth routes', 'done'],
        ['Add JWT middleware', 'done'],
        ['Run checks', 'done']
      ])
    },
    {
      id: 't_1047',
      title: 'Add cursor pagination',
      prompt: 'Add cursor-based pagination to /tasks. Keep the existing page parameter working for one release.',
      projectId: 'orbit-api',
      branch: 'feat/cursor-pagination',
      agentId: 'gpt-4o',
      status: 'in_progress',
      createdAt: iso(65),
      updatedAt: iso(1),
      messages: [
        msg('user', 'Add cursor-based pagination to /tasks. Keep the existing page parameter working for one release.', 65),
        msg('agent', "I'll keep `page` as a fallback and add `cursor` plus `limit`, capped at 100.", 63),
        msg('user', "Make sure the response shape doesn't break the mobile client.", 30),
        msg('agent', "The existing fields stay untouched. I'm adding `nextCursor` alongside them.", 28),
        msg('agent', 'Refactoring the query builder now. Two files changed so far.', 2)
      ],
      activity: [
        ev('read', 'Read src/routes/tasks.ts', 60),
        ev('edit', 'Updated the task query builder', 20),
        ev('edit', 'Kept the legacy page handler', 8),
        ev('status', 'Running a type check on the new query', 1)
      ],
      changedFiles: [
        file('src/routes/tasks.ts', 'modified', 9, 3, [
          ' import { Router } from "express";',
          '-import { listTasks } from "../controllers/tasks";',
          '+import { listTasks, listTasksCursor } from "../controllers/tasks";',
          ' ',
          ' const router = Router();',
          ' ',
          '-router.get("/tasks", listTasks);',
          '+router.get("/tasks", listTasksCursor);',
          '+router.get("/tasks/legacy", listTasks);',
          ' ',
          ' export default router;'
        ], { oldStart: 1, newStart: 1 }),
        file('src/services/tasks.ts', 'modified', 15, 4, [
          ' export async function listTasks(projectId: string, page: number) {',
          '-  const pageSize = 20;',
          '-  const offset = page * pageSize;',
          '+  const pageSize = 50;',
          '+  const cursor = decodeCursor(page);',
          ' ',
          '-  return db.task.findMany({ where: { projectId }, skip: offset, take: pageSize });',
          '+  return db.task.findMany({',
          '+    where: { projectId },',
          '+    take: pageSize,',
          '+    skip: cursor ? 1 : 0,',
          '+    cursor: cursor ? { id: cursor } : undefined,',
          '+    orderBy: { createdAt: "desc" },',
          '+  });',
          ' }',
          '+',
          '+function decodeCursor(value: number | string) {',
          '+  return typeof value === "string" ? value : null;',
          '+}'
        ], { oldStart: 1, newStart: 1 })
      ],
      checks: [],
      decisions: [],
      plan: plan([
        ['Read current task routes', 'done'],
        ['Keep the legacy page path', 'done'],
        ['Add cursor query builder', 'active'],
        ['Run checks', 'todo']
      ])
    },
    {
      id: 't_1042',
      title: 'Add rate limiting',
      prompt: 'Add rate limiting to the public endpoints. 100 requests per minute per API key should be enough for now.',
      projectId: 'orbit-api',
      branch: 'feature/rate-limits',
      agentId: 'claude-3-5',
      status: 'needs_input',
      createdAt: iso(120),
      updatedAt: iso(34),
      permissionRequest: {
        title: 'Install a dependency?',
        body: 'The implementation needs a small package to run the token bucket. This runs the package manager once and needs network access for this task only.',
        command: 'pnpm add rate-limiter-flexible',
        state: 'pending',
        at: iso(34)
      },
      messages: [
        msg('user', 'Add rate limiting to the public endpoints. 100 requests per minute per API key should be enough for now.', 120),
        msg('agent', "I'll key the limiter by API key and return 429 with a Retry-After header when a bucket is empty.", 112),
        msg('agent', 'Before I continue, I need to add a dependency to the project.', 34)
      ],
      activity: [
        ev('read', 'Read the public route definitions', 118),
        ev('edit', 'Planned the limiter as keyed middleware', 40),
        ev('permission', 'Waiting for permission to install a package', 34)
      ],
      changedFiles: [],
      checks: [],
      decisions: [],
      plan: plan([
        ['Read public routes', 'done'],
        ['Install rate limiter', 'active'],
        ['Implement middleware', 'todo'],
        ['Add tests', 'todo']
      ])
    },
    {
      id: 't_1044',
      title: 'Improve API error handling',
      prompt: 'Make failed upstream requests return a useful error with a retry hint instead of a blank 500.',
      projectId: 'orbit-api',
      branch: 'fix/error-states',
      agentId: 'gpt-4o',
      status: 'failed',
      createdAt: iso(180),
      updatedAt: iso(90),
      failure: {
        title: 'Type check failed',
        summary: 'src/api/client.ts(42,18): error TS2345',
        detail: "Argument of type 'unknown' is not assignable to parameter of type 'RequestError'.",
        at: iso(90)
      },
      messages: [
        msg('user', 'Make failed upstream requests return a useful error with a retry hint instead of a blank 500.', 180),
        msg('agent', 'I added an error mapping layer in the API client and mapped upstream failures to 502 responses with a retry hint.', 172),
        msg('agent', "The type check fails in src/api/client.ts and I couldn't resolve it before the session stopped.", 92)
      ],
      activity: [
        ev('read', 'Read src/api/client.ts', 176),
        ev('edit', 'Added an error mapping layer', 150),
        ev('test', 'Ran pnpm test — 31 passed', 110),
        ev('error', 'Type check failed in src/api/client.ts', 90),
        ev('status', 'Session ended', 89)
      ],
      changedFiles: [
        file('src/api/client.ts', 'modified', 24, 8, null, {
          unavailable: true,
          reason: 'The execution session ended before the full diff was captured.'
        }),
        file('src/lib/errors.ts', 'added', 12, 0, [
          '+export class RequestError extends Error {',
          '+  status: number;',
          '+  retryable: boolean;',
          '+',
          '+  constructor(message: string, status: number, retryable: boolean) {',
          '+    super(message);',
          '+    this.name = "RequestError";',
          '+    this.status = status;',
          '+    this.retryable = retryable;',
          '+  }',
          '+}',
          '+',
          '+export function toRequestError(error: unknown): RequestError {',
          '+  if (error instanceof RequestError) return error;',
          '+  return new RequestError("Upstream request failed", 502, true);',
          '+}'
        ], { oldStart: 0, newStart: 1 }),
        file('src/routes/proxy.ts', 'modified', 6, 2, [
          ' import { Router } from "express";',
          '+import { toRequestError } from "../lib/errors";',
          ' ',
          ' router.get("/proxy", async (req, res) => {',
          '-  const result = await client.get(req.query.url);',
          '-  res.json(result);',
          '+  try {',
          '+    const result = await client.get(req.query.url);',
          '+    res.json(result);',
          '+  } catch (error) {',
          '+    const mapped = toRequestError(error);',
          '+    res.status(mapped.status).json({ error: mapped.message, retryable: mapped.retryable });',
          '+  }',
          ' });'
        ], { oldStart: 1, newStart: 1 })
      ],
      checks: [
        check('test', 'Unit tests', 'passed', '31 passed · 0 failed', 110, {
          command: 'pnpm test',
          duration: '4.1s',
          output: 'Test Files  4 passed (4)\nTests       31 passed (31)'
        }),
        check('typecheck', 'Type check', 'failed', '1 error', 90, {
          command: 'pnpm typecheck',
          duration: '2.2s',
          output: 'src/api/client.ts:42:18 - error TS2345\n\n42   return normalize(error);\n                        ~~~~~\n\nArgument of type unknown is not assignable to parameter of type RequestError.'
        }),
        check('lint', 'Lint', 'skipped', 'Stopped after the type-check failure', 90, {
          command: 'pnpm lint',
          output: 'Skipped because an earlier check failed.'
        }),
        check('build', 'Build', 'not_run', 'Not reached', 90, {
          command: 'pnpm build',
          output: 'Not reached because an earlier check failed.'
        })
      ],
      decisions: [],
      plan: plan([
        ['Read the API client', 'done'],
        ['Add error mapping', 'done'],
        ['Run checks', 'failed'],
        ['Fix the type error', 'todo']
      ])
    },
    {
      id: 't_1045',
      title: 'Move token storage to Redis',
      prompt: 'Move refresh token storage to Redis and add a migration for existing sessions.',
      projectId: 'orbit-api',
      branch: 'chore/redis-sessions',
      agentId: 'gemini-1-5',
      status: 'pending',
      createdAt: iso(240),
      updatedAt: iso(240),
      messages: [
        msg('user', 'Move refresh token storage to Redis and add a migration for existing sessions.', 240)
      ],
      activity: [
        ev('queue', 'Queued behind a running task', 240)
      ],
      changedFiles: [],
      checks: [],
      decisions: [],
      plan: plan([
        ['Wait for an execution slot', 'active'],
        ['Inspect current token storage', 'todo'],
        ['Add Redis adapter', 'todo'],
        ['Migrate existing sessions', 'todo']
      ])
    },
    {
      id: 't_1040',
      title: 'Fix mobile layout',
      prompt: 'Fix the dashboard layout on small phones. Cards overflow around 375px and the bottom bar covers the composer.',
      projectId: 'mobile-ui',
      branch: 'fix/small-screens',
      agentId: 'gpt-4o',
      status: 'in_progress',
      createdAt: iso(32),
      updatedAt: iso(2),
      messages: [
        msg('user', 'Fix the dashboard layout on small phones. Cards overflow around 375px and the bottom bar covers the composer.', 32),
        msg('agent', "I can reproduce the overflow at 375px. The stat row has a fixed width, so I'll make it flexible and add safe-area padding to the composer.", 30),
        msg('user', 'Keep the visual style the same, just fix the sizing.', 20),
        msg('agent', 'Understood. Layout only, no visual changes.', 18)
      ],
      activity: [
        ev('read', 'Read Dashboard.css and tokens.css', 28),
        ev('edit', 'Made the stat grid responsive', 8),
        ev('status', 'Checking 320–430px widths', 2)
      ],
      changedFiles: [
        file('src/components/Dashboard.css', 'modified', 14, 6, [
          ' .stats-grid {',
          '-  display: flex;',
          '-  overflow-x: auto;',
          '+  display: grid;',
          '+  grid-template-columns: repeat(3, minmax(0, 1fr));',
          '+  gap: 10px;',
          ' }',
          ' ',
          ' .stat-card {',
          '-  width: 386px;',
          '-  flex: 0 0 386px;',
          '+  width: 100%;',
          '+  min-width: 0;',
          ' }',
          ' ',
          ' .composer {',
          '+  padding-bottom: calc(12px + env(safe-area-inset-bottom));',
          ' }',
          '+',
          '+@media (max-width: 374px) {',
          '+  .stats-grid {',
          '+    grid-template-columns: repeat(2, minmax(0, 1fr));',
          '+  }',
          '+}'
        ], { oldStart: 1, newStart: 1 }),
        file('src/styles/tokens.css', 'modified', 3, 0, [
          ' :root {',
          '   --safe-bottom: env(safe-area-inset-bottom);',
          '+  --nav-height: 64px;',
          '+  --composer-offset: calc(var(--nav-height) + var(--safe-bottom));',
          '+  --content-gutter: 18px;',
          ' }'
        ], { oldStart: 1, newStart: 1 })
      ],
      checks: [],
      decisions: [],
      plan: plan([
        ['Reproduce overflow at 375px', 'done'],
        ['Make the stat grid responsive', 'done'],
        ['Verify 320–430px widths', 'active'],
        ['Run tests', 'todo']
      ])
    },
    {
      id: 't_1046',
      title: 'Add dark mode tokens',
      prompt: 'Add dark mode color tokens without changing the current light theme.',
      projectId: 'mobile-ui',
      branch: 'feat/dark-tokens',
      agentId: 'claude-3-5',
      status: 'completed',
      createdAt: iso(1440),
      updatedAt: iso(1320),
      messages: [
        msg('user', 'Add dark mode color tokens without changing the current light theme.', 1440),
        msg('agent', 'Added a dark palette alongside the existing light tokens. Nothing switches automatically yet.', 1400),
        msg('user', 'Can we keep the old palette import working?', 1360),
        msg('agent', 'Yes. The old import now re-exports the new palette, and the theme provider is renamed with a compatibility export.', 1330),
        msg('agent', 'The checks that ran pass. The changes are ready for review.', 1325)
      ],
      activity: [
        ev('read', 'Read the current palette', 1420),
        ev('edit', 'Added dark mode tokens', 1360),
        ev('edit', 'Kept the legacy import working', 1335),
        ev('test', 'Ran the checks', 1325),
        ev('review', 'You approved this work', 1320)
      ],
      changedFiles: [
        file('src/theme/colors.ts', 'modified', 12, 0, [
          ' export const palette = {',
          '   background: "#ffffff",',
          '   surface: "#f7f8f9",',
          '   text: "#17191c",',
          '   muted: "#6e747c",',
          '+  border: "#e6e8eb",',
          '+  accent: "#2f6bea",',
          ' };',
          '+',
          '+export const darkPalette = {',
          '+  background: "#111315",',
          '+  surface: "#181b1e",',
          '+  text: "#f4f5f6",',
          '+  muted: "#a7adb5",',
          '+  border: "#2a2f35",',
          '+  accent: "#6f9bff",',
          '+};'
        ], { oldStart: 1, newStart: 1 }),
        file('src/theme/legacyPalette.ts', 'deleted', 0, 8, [
          '-export const legacyPalette = {',
          '-  background: "#ffffff",',
          '-  surface: "#f7f8f9",',
          '-  text: "#17191c",',
          '-  muted: "#6e747c",',
          '-};',
          '-',
          '-export default legacyPalette;'
        ], { oldStart: 1, newStart: 0 }),
        file('src/components/ThemeProvider.tsx', 'renamed', 4, 2, [
          '-import { palette } from "./legacyPalette";',
          '+import { palette } from "../theme/colors";',
          ' import type { ReactNode } from "react";',
          ' ',
          '-export function Provider({ children }: { children: ReactNode }) {',
          '+export function ThemeProvider({ children }: { children: ReactNode }) {',
          '   return <div style={{ background: palette.background }}>{children}</div>;',
          ' }',
          '+',
          '+export const Provider = ThemeProvider;'
        ], { oldStart: 1, newStart: 1, oldPath: 'src/components/Provider.tsx' })
      ],
      checks: [
        check('test', 'Unit tests', 'passed', '26 passed · 0 failed', 1325, {
          command: 'pnpm test',
          duration: '3.8s',
          output: 'Test Files  3 passed (3)\nTests       26 passed (26)'
        }),
        check('lint', 'Lint', 'passed', '0 problems', 1325, {
          command: 'pnpm lint',
          duration: '1.6s',
          output: 'No issues found.'
        }),
        check('typecheck', 'Type check', 'passed', 'No type errors', 1325, {
          command: 'pnpm typecheck',
          duration: '2.4s',
          output: 'No errors.'
        }),
        check('build', 'Build', 'not_run', 'No build script configured', 1325, {
          command: '—',
          output: 'This project has no build script, so nothing was run.'
        })
      ],
      decisions: [
        decision('approved', 'Nice. Keeping the old import working is exactly what I wanted.', 1320)
      ],
      plan: plan([
        ['Read the palette', 'done'],
        ['Add dark tokens', 'done'],
        ['Keep the legacy import', 'done'],
        ['Run checks', 'done']
      ])
    },
    {
      id: 't_1039',
      title: 'Update API reference',
      prompt: 'Update the rate limit section of the API reference to match the new limiter.',
      projectId: 'docs-site',
      branch: 'docs/rate-limits',
      agentId: 'gpt-4o',
      status: 'completed',
      createdAt: iso(2900),
      updatedAt: iso(2850),
      messages: [
        msg('user', 'Update the rate limit section of the API reference to match the new limiter.', 2900),
        msg('agent', 'Updated the limits, the 429 example, and the Retry-After guidance.', 2880),
        msg('agent', 'The link check and Markdown lint pass. Ready for review.', 2860)
      ],
      activity: [
        ev('read', 'Read docs/api/rate-limits.md', 2890),
        ev('edit', 'Updated the rate limit reference', 2880),
        ev('test', 'Ran the documentation checks', 2860),
        ev('review', 'You approved this work', 2850)
      ],
      changedFiles: [
        file('docs/api/rate-limits.md', 'modified', 18, 4, [
          ' # Rate limits',
          ' ',
          '-Public endpoints allow 60 requests per minute per API key.',
          '+Public endpoints allow 100 requests per minute per API key.',
          ' ',
          ' ## Response headers',
          ' ',
          ' Every response includes the remaining quota:',
          ' ',
          ' ```http',
          ' X-RateLimit-Limit: 100',
          ' X-RateLimit-Remaining: 42',
          '+Retry-After: 30',
          ' ```',
          '+',
          '+When a bucket is empty, the API returns `429 Too Many Requests`',
          '+with a `Retry-After` header in seconds.',
          '+Wait for that interval before retrying. Retrying sooner will',
          '+consume another rejected request.'
        ], { oldStart: 1, newStart: 1 })
      ],
      checks: [
        check('test', 'Link check', 'passed', '42 links checked', 2860, {
          command: 'pnpm check:links',
          duration: '2.1s',
          output: '42 links checked\n0 broken links'
        }),
        check('lint', 'Markdown lint', 'passed', '0 problems', 2860, {
          command: 'pnpm lint:docs',
          duration: '0.9s',
          output: 'No issues found.'
        })
      ],
      decisions: [
        decision('approved', '', 2850)
      ],
      plan: plan([
        ['Read the current reference', 'done'],
        ['Update limits and examples', 'done'],
        ['Run documentation checks', 'done']
      ])
    },
    {
      id: 't_1038',
      title: 'Fix broken links',
      prompt: 'Fix the broken links reported in the docs build.',
      projectId: 'docs-site',
      branch: 'docs/link-fixes',
      agentId: 'claude-3-5',
      status: 'completed',
      createdAt: iso(4320),
      updatedAt: iso(4270),
      messages: [
        msg('user', 'Fix the broken links reported in the docs build.', 4320),
        msg('agent', 'Fixed three links in the getting started guide and removed one link to a retired page.', 4300),
        msg('agent', 'No checks are configured for this project, so I could not run validation.', 4280)
      ],
      activity: [
        ev('read', 'Read the getting started guide', 4310),
        ev('edit', 'Fixed three links', 4300),
        ev('review', 'You approved this work', 4270)
      ],
      changedFiles: [
        file('docs/guides/getting-started.md', 'modified', 4, 4, [
          ' # Getting started',
          ' ',
          '-Install the CLI with `npm install -g orbit`.',
          '+Install the CLI with `pnpm add -g @orbit/cli`.',
          ' ',
          ' ## Create your first project',
          ' ',
          '-Run `orbit init` in an empty directory. See the old setup page.',
          '+Run `orbit init` in an empty directory, then follow the prompts.',
          '+Linking a repository is optional and can be added later.'
        ], { oldStart: 1, newStart: 1 })
      ],
      checks: [],
      decisions: [
        decision('approved', '', 4270)
      ],
      plan: plan([
        ['Read the failing pages', 'done'],
        ['Fix the links', 'done'],
        ['Check the docs build', 'done']
      ])
    },
    {
      id: 't_1037',
      title: 'Harden CORS headers',
      prompt: 'Replace the permissive CORS policy with an allowlist from environment settings.',
      projectId: 'orbit-api',
      branch: 'fix/cors',
      agentId: 'claude-3-5',
      status: 'completed',
      createdAt: iso(5760),
      updatedAt: iso(5700),
      messages: [
        msg('user', 'Replace the permissive CORS policy with an allowlist from environment settings.', 5760),
        msg('agent', 'Added an allowlist from ALLOWED_ORIGINS and kept credentials enabled for trusted origins.', 5740),
        msg('agent', 'Tests and the type check pass. Ready for review.', 5710)
      ],
      activity: [
        ev('read', 'Read the current CORS config', 5750),
        ev('edit', 'Added an origin allowlist', 5740),
        ev('test', 'Ran tests and the type check', 5710),
        ev('review', 'You approved this work', 5700)
      ],
      changedFiles: [
        file('src/config/cors.ts', 'modified', 11, 1, [
          ' import cors from "cors";',
          '+import { env } from "./env";',
          ' ',
          '-export const corsMiddleware = cors();',
          '+const allowedOrigins = env.ALLOWED_ORIGINS.split(",");',
          '+',
          '+export const corsMiddleware = cors({',
          '+  origin(origin, callback) {',
          '+    if (!origin || allowedOrigins.includes(origin)) {',
          '+      return callback(null, true);',
          '+    }',
          '+    callback(new Error("Origin not allowed"));',
          '+  },',
          '+  credentials: true,',
          '+});'
        ], { oldStart: 1, newStart: 1 })
      ],
      checks: [
        check('test', 'Unit tests', 'passed', '24 passed · 0 failed', 5710, {
          command: 'pnpm test',
          duration: '3.6s',
          output: 'Test Files  3 passed (3)\nTests       24 passed (24)'
        }),
        check('typecheck', 'Type check', 'passed', 'No type errors', 5710, {
          command: 'pnpm typecheck',
          duration: '2.1s',
          output: 'No errors.'
        })
      ],
      decisions: [
        decision('approved', '', 5700)
      ],
      plan: plan([
        ['Read CORS config', 'done'],
        ['Add allowlist', 'done'],
        ['Run checks', 'done']
      ])
    },
    {
      id: 't_1036',
      title: 'Rotate signing keys',
      prompt: 'Add support for a secondary signing key so keys can rotate without invalidating sessions.',
      projectId: 'orbit-api',
      branch: 'feat/key-rotation',
      agentId: 'claude-3-5',
      status: 'completed',
      createdAt: iso(7200),
      updatedAt: iso(7140),
      messages: [
        msg('user', 'Add support for a secondary signing key so keys can rotate without invalidating sessions.', 7200),
        msg('agent', 'Verification now tries the active key first, then the previous key. New tokens are only signed with the active key.', 7180),
        msg('agent', 'This project has no automated checks configured, so I validated the change manually.', 7160)
      ],
      activity: [
        ev('read', 'Read the signing configuration', 7190),
        ev('edit', 'Added secondary key verification', 7180),
        ev('review', 'You approved this work', 7140)
      ],
      changedFiles: [
        file('src/auth/keys.ts', 'modified', 8, 2, [
          ' export function verifyToken(token: string) {',
          '-  return verify(token, activeKey);',
          '+  for (const key of [activeKey, previousKey]) {',
          '+    try {',
          '+      return verify(token, key);',
          '+    } catch {',
          '+      continue;',
          '+    }',
          '+  }',
          '+  throw new Error("Invalid token signature");',
          ' }'
        ], { oldStart: 1, newStart: 1 })
      ],
      checks: [],
      decisions: [
        decision('approved', '', 7140)
      ],
      plan: plan([
        ['Read signing config', 'done'],
        ['Add secondary key', 'done'],
        ['Validate manually', 'done']
      ])
    },
    {
      id: 't_1048',
      title: 'Explain the type-check failure',
      prompt: 'Explain the type-check error in src/api/client.ts and suggest a minimal fix. Do not change any files yet.',
      projectId: 'orbit-api',
      branch: 'fix/error-states',
      agentId: 'qwen-coder-1-5b',
      status: 'needs_input',
      createdAt: iso(24),
      updatedAt: iso(12),
      messages: [
        msg('user', 'Explain the type-check error in src/api/client.ts and suggest a minimal fix. Do not change any files yet.', 24),
        msg('agent', 'The failure is at src/api/client.ts:42. normalize() receives an unknown value but expects a RequestError. A minimal fix is to narrow the value with toRequestError(error) before calling normalize(). I can outline the patch here; applying it needs the execution host.', 16, { local: true, preview: true }),
        msg('agent', 'Do you want the minimal patch outline, or a short review of the surrounding error handling?', 12, { local: true, preview: true })
      ],
      activity: [
        ev('local', 'Answered on this phone (preview)', 16),
        ev('status', 'Waiting for your response', 12)
      ],
      changedFiles: [],
      checks: [],
      decisions: [],
      plan: null
    }
  ];

  window.AgentDeckKit = { mkDiff, iso, uid };

  window.AgentDeckSeed = function () {
    return JSON.parse(JSON.stringify({
      meta: { version: 4, seededAt: new Date().toISOString() },
      user: { name: 'Rahul', email: 'demo@agentdeck.local' },
      host: {
        id: 'host_studio',
        name: 'Studio Mac',
        platform: 'macOS',
        transport: 'Encrypted tunnel',
        state: 'connected',
        lastSeen: new Date().toISOString()
      },
      projects,
      agents,
      settings: {
        activeProjectId: 'orbit-api',
        defaultProjectId: 'orbit-api',
        defaultAgentId: 'claude-3-5',
        demoNoticeDismissed: false
      },
      tasks
    }));
  };
})();
