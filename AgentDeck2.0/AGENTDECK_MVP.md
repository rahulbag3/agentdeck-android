# AgentDeck — MVP Product Brief

> **Purpose:** A living specification for building AgentDeck incrementally. Keep the first release focused; add features only after the core workflow works end to end.
>
> **Status:** Initial draft · **Version:** 0.1 · **Last updated:** 2026-10-23

## 1. Product overview

**AgentDeck is a mobile control center for AI coding agents.** It lets a developer see what coding tasks are running, talk to an agent, monitor its progress, inspect code changes, and decide whether work is ready to accept—all from a phone.

AgentDeck is not intended to be a full desktop IDE squeezed onto a smaller screen. The mobile app should make supervising agent work easy, quick, and safe.

### Product principles

1. **Mobile-first:** prioritize quick status checks, short instructions, notifications, and review actions.
2. **Clear state:** always show whether a task is pending, running, waiting for input, failed, or ready for review.
3. **Trust before automation:** show the changes and validation results before work is accepted.
4. **One task, one history:** keep instructions, agent messages, tool activity, changed files, and review outcomes together.
5. **Small, incremental releases:** get one complete workflow working before adding advanced agent orchestration.

## 2. MVP goal

Deliver one complete workflow:

1. The user opens a project.
2. The user creates a coding task and gives the agent instructions.
3. The agent runs in a connected execution environment.
4. The user can see its status and send follow-up messages.
5. When the agent finishes, the user inspects its changed files and diff.
6. The user reviews validation results and approves the work or requests changes.
7. The task and its history remain available later.

**Important dependency:** the mobile interface alone cannot execute code. AgentDeck needs a secure connection to a machine or service that hosts the coding-agent runtime. For an early UI prototype, use mock data; for a functional MVP, implement this connection before claiming tasks actually run.

## 3. Core features — MVP

These are the initial numbered features. Keep this list as the source of truth for the first release.

### F01 — Project and repository selection

- Show the user's available projects.
- Display the selected project and repository/branch context when available.
- Keep every task associated with a project.
- Show connection state when the project or execution host is unavailable.

**Acceptance criteria**
- A user can select a project and see its tasks.
- A task cannot accidentally be created against an unclear or missing project.
- Connection failures are shown with a useful explanation and retry action.

### F02 — Task dashboard

- Show active, pending, review, failed/blocked, and completed task counts.
- List recent tasks with title, project, current status, agent/model (if relevant), and last activity time.
- Support basic search and status filtering.
- Provide a clear **New task** action.

**Acceptance criteria**
- The user can quickly find a task and identify its current state without opening it.
- Status names and colors are consistent throughout the app.
- Empty, loading, and error states are designed—not just the successful state.

### F03 — Create a coding task

- Require a task title or prompt.
- Let the user choose a project and, when supported, a branch.
- Allow selecting an available agent/model; offer a sensible default.
- Show any necessary permissions or execution context before starting.

**Acceptance criteria**
- The user can submit a task with a clear goal and see whether it was accepted.
- Double taps or network retries must not silently create duplicate tasks.
- Failed task creation preserves the user's prompt so it can be retried.

### F04 — Agent conversation

- Show user messages and agent responses in a persistent task thread.
- Let the user send follow-up instructions while the task is running or after it finishes.
- Clearly display sending, queued, failed-to-send, and received states.
- Keep relevant status updates and tool activity associated with the task.

**Acceptance criteria**
- The user can reopen a task and see its previous conversation.
- A failed message can be retried without retyping it.
- The UI does not imply that the agent received a message until the backend confirms it.

### F05 — Task status and execution activity

Use a small, consistent status model:

- **Pending:** queued and not yet running.
- **In progress:** the agent is actively working.
- **Needs input:** the agent is waiting for a response or permission.
- **Review:** the agent has finished its current work and changes are ready to inspect.
- **Failed:** execution stopped because of an error.
- **Completed:** work has been accepted or otherwise explicitly completed.

Show the latest activity, errors, and available progress information. Allow stop/cancel only when the execution backend genuinely supports it.

**Acceptance criteria**
- Status updates are based on backend state, not guessed from elapsed time.
- A stalled or disconnected session is distinguishable from a running session.
- Important errors include a next step, such as retry, reconnect, or view details.

### F06 — Changed-file list

- List files modified by the agent.
- Show file paths and added/deleted line counts when available.
- Let the user open a file's diff.
- Indicate deleted, added, renamed, and modified files distinctly when the backend provides that information.

**Acceptance criteria**
- The changed-file list reflects the current task result.
- Long file paths remain readable and can be inspected without breaking the layout.
- A missing diff or unavailable file is handled clearly.

### F07 — Code-diff review

- Display added and removed lines with a readable, consistent color palette.
- Support long lines and files on narrow screens without overlapping controls.
- Provide a file list or file switcher for reviewing multiple changes.
- Show the branch or commit context when available.

**Acceptance criteria**
- The user can distinguish additions from deletions without relying on color alone.
- Code remains legible on a phone, with horizontal scrolling where needed.
- The review screen uses the same design tokens as the rest of the app.

### F08 — Approve or request changes

- Allow the user to approve the proposed work or request changes with written feedback.
- Make it clear what “approve” does in the connected environment (for example, mark reviewed, accept a patch, or permit a merge).
- Require confirmation for destructive or externally visible actions such as merging or deploying.
- Record the decision in task history.

**Acceptance criteria**
- The user can tell what will happen before approving.
- Requesting changes sends the feedback to the right task/session.
- The app never reports a change as approved unless the backend confirms it.

### F09 — Validation results

- Show test, build, lint, and type-check results when these checks are configured and available.
- Distinguish passed, failed, skipped, and not run.
- Include useful error output and a way to return to the agent conversation.
- Do not claim work passed checks when no checks actually ran.

**Acceptance criteria**
- The review screen clearly summarizes validation status.
- Failed checks can be opened for details.
- Unavailable validation is displayed as “Not run” or “Unavailable,” never as a pass.

### F10 — Task history and recovery

- Preserve task prompts, conversation, state changes, changed-file summary, validation outcomes, and review decisions.
- Allow users to reopen and search previous tasks.
- Show when the last update occurred and whether the execution host is currently reachable.

**Acceptance criteria**
- Previously completed task records remain accessible after app restart.
- Reopening a task does not accidentally launch it again.
- The app distinguishes old history from live session activity.

## 4. Initial screens

Keep the first navigation small and predictable.

1. **Dashboard** — project selector, task counts, recent tasks, search/filter, New task.
2. **Task detail / Agent chat** — conversation, current status, activity summary, message composer.
3. **Changes / Review** — changed files, diffs, validation results, approve/request changes.
4. **Tasks board or list** — browse tasks by status. A compact list is sufficient for the first MVP; a kanban board can follow.
5. **Connection and settings** — connect/disconnect execution host, show connection state, choose supported defaults, and sign out.

A task detail page may contain tabs or sections for **Chat**, **Changes**, and **Checks** rather than creating many separate screens.

## 5. Non-functional requirements

### Security and permissions

- Authenticate the user and protect project/session data.
- Use secure transport for all remote communication.
- Store credentials/tokens using platform-appropriate secure storage; never put secrets in source code or logs.
- Request only the permissions required for the selected action.
- Ask for explicit confirmation before destructive or externally visible operations.
- Provide a way to disconnect/revoke a paired host or session.

### Reliability

- Handle poor network conditions, retries, stale sessions, and reconnects.
- Make request states visible so a user does not unknowingly submit the same task or message repeatedly.
- Persist task history on the backend, not only in local UI state.

### Accessibility and mobile usability

- Keep controls touch-friendly and readable at phone sizes.
- Support dynamic text or sensible text scaling where the platform permits.
- Do not use color as the only way to communicate status.
- Respect safe areas; keep the message composer and primary actions clear of the keyboard and home indicator.
- Make long code and paths usable with scrolling instead of shrinking text until it is illegible.

### Visual direction

- App name: **AgentDeck**.
- Keep the app icon omitted for now; use a simple text wordmark.
- Current design direction: light surfaces, neutral gray borders, readable dark text, restrained blue actions, subtle green additions and red deletions in code review.
- Use consistent spacing, typography, corner radii, icon size, and status colors across every screen.
- Treat the existing HTML/SVG files as visual prototypes, not as production application logic.

## 6. Out of scope for the first MVP

Do not build these until the core workflow is working reliably:

- Running many agents in parallel or managing agent teams.
- Advanced project memory and automatic knowledge-base creation.
- Full terminal emulator on mobile.
- Full code editor with multi-file editing.
- Browser/device simulator streaming and visual QA automation.
- Deployment management and production infrastructure controls.
- Advanced cost analytics and model benchmarking.
- Team workspaces, billing, organization administration, and complex role systems.
- A large plugin marketplace or extensive integrations.

These can be reconsidered after feedback from real use.

## 7. Suggested build sequence

### Milestone 0 — UI prototype

- Implement the five initial screens using mock data.
- Establish reusable components and design tokens.
- Verify phone sizing, loading/empty/error states, and navigation.
- Label mock functionality clearly so it cannot be mistaken for live execution.

**Done when:** all screens can be navigated, and sample tasks can be viewed and reviewed using mock data.

### Milestone 1 — Project and task persistence

- Add authentication and project selection.
- Persist tasks and status changes.
- Implement create, search, filter, and task history.

**Done when:** tasks survive app restarts and are scoped to the correct user/project.

### Milestone 2 — Real agent connection

- Define the API/protocol to a connected coding-agent runtime.
- Send task prompts and follow-up messages.
- Stream or refresh status and activity safely.
- Handle disconnects, errors, and cancellation where supported.

**Done when:** a real task can be started and monitored from the mobile app.

### Milestone 3 — Review and validation

- Retrieve changed files and diffs.
- Show real test/build results.
- Submit review feedback or approval to the backend.
- Record review decisions in history.

**Done when:** a user can inspect actual agent changes, verify checks, and complete the review loop from the phone.

### Milestone 4 — Remote usability

- Add actionable notifications for completion, errors, and required input.
- Improve reconnect and resume behavior.
- Add attachments only after the core remote workflow is stable.

**Done when:** a user can leave the app, receive a useful alert, return to the correct task, and continue safely.

## 8. Initial domain model

Use these concepts regardless of the eventual framework or database. Names can be adjusted during implementation.

- **User:** identity and authentication details.
- **Project:** repository metadata, default branch, and execution-host association.
- **Task:** title, prompt, project ID, branch, status, selected agent/model, timestamps.
- **TaskMessage:** task ID, author/type, content, timestamp, delivery state.
- **ActivityEvent:** task ID, event type, summary, timestamp, optional metadata.
- **ChangedFile:** task ID, path, change type, additions/deletions, diff reference.
- **ValidationRun:** task ID, check type, status, output summary, timestamps.
- **ReviewDecision:** task ID, decision, feedback, user ID, timestamp.
- **ExecutionHost:** connection state, host identity, supported capabilities; do not store long-lived secrets as ordinary fields.

## 9. Initial success criteria

The MVP is useful when a developer can reliably:

1. Find the right project and create a task.
2. See whether the coding agent is actually running, waiting, or finished.
3. Send follow-up instructions and recover from a temporary disconnection.
4. Inspect the changed files and understand the diff on a phone.
5. See which checks passed or failed.
6. Approve work or request changes with a clear record of the decision.
7. Reopen the task later and understand what happened.

Track these practical measures during testing:

- Percentage of tasks that can be started and followed to a terminal state without losing updates.
- Rate of duplicate task/message submissions.
- Time needed to find a task and reach its review screen.
- Percentage of completed tasks with visible validation status.
- Reconnect success rate and number of unclear/error states reported by testers.

Do not optimize for the number of features shipped. Optimize for completing this workflow reliably.

## 10. Open decisions for later

Do not block the UI prototype on all of these. Decide them before production backend work:

- Target platform: iOS, Android, responsive web, or cross-platform mobile.
- Where agents execute: the user's own computer, a remote server, or supported hosted runtimes.
- Which coding-agent runtimes/providers are supported first.
- How repositories are authenticated and accessed.
- What exactly “Approve” means in the first integration.
- Whether the task board is needed in v1 or whether a filtered list is enough.
- Data retention, privacy, telemetry, and cost limits.

---

## Working rule

**Build the smallest reliable end-to-end workflow first.** Keep this file updated as scope changes, and move new ideas into a later milestone rather than silently expanding the MVP.
