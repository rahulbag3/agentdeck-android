package com.agentdeck.app.data

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Real git inside a workspace directory, via JGit (pure Java — stock Android
 * ships no git binary). Every operation is local; nothing is pushed anywhere.
 * Publishing to GitHub stays on the existing pull-request flow.
 */
class WorkspaceGit {

    fun hasRepo(dir: File): Boolean = File(dir, ".git").isDirectory

    fun init(dir: File): String {
        if (hasRepo(dir)) return "repository already exists"
        dir.mkdirs()
        return runCatching {
            Git.init().setDirectory(dir).call().use { "initialized repository" }
        }.getOrElse { "git error: ${it.message?.take(120)}" }
    }

    /** Working-tree status as concise text. */
    fun status(dir: File): String {
        if (!hasRepo(dir)) return "no git repository in the workspace"
        return runCatching {
            Git.open(dir).use { git ->
                val st = git.status().call()
                val parts = mutableListOf<String>()
                if (st.added.isNotEmpty()) parts += "added: ${st.added.joinToString(", ").take(400)}"
                if (st.modified.isNotEmpty()) parts += "modified: ${st.modified.joinToString(", ").take(400)}"
                if (st.removed.isNotEmpty()) parts += "removed: ${st.removed.joinToString(", ").take(400)}"
                if (st.untracked.isNotEmpty()) parts += "untracked: ${st.untracked.joinToString(", ").take(400)}"
                if (parts.isEmpty()) "clean" else parts.joinToString("\n")
            }
        }.getOrElse { "git error: ${it.message?.take(120)}" }
    }

    /** Stages everything and commits; returns the short sha or a nothing-to-commit note. */
    fun commitAll(dir: File, message: String): String {
        if (!hasRepo(dir)) return "no git repository in the workspace"
        return runCatching {
            Git.open(dir).use { git ->
                git.add().addFilepattern(".").call()
                val staged = git.status().call()
                if (staged.added.isEmpty() && staged.modified.isEmpty() && staged.removed.isEmpty() &&
                    staged.changed.isEmpty()
                ) {
                    return@use "nothing to commit"
                }
                val rev = git.commit().setMessage(message).call()
                "committed ${rev.name.take(8)}: ${message.take(80)}"
            }
        }.getOrElse { "git error: ${it.message?.take(120)}" }
    }

    /**
     * Unified diff of every change (including untracked files) against HEAD,
     * in `diff --git` form — directly parseable by [HostDiff].
     */
    fun diffHead(dir: File, maxChars: Int = 120_000): String {
        if (!hasRepo(dir)) return ""
        return runCatching {
            Git.open(dir).use { git ->
                // Stage everything first so untracked files appear in the diff.
                git.add().addFilepattern(".").call()
                val out = ByteArrayOutputStream()
                val entries = git.diff().setCached(true).call()
                DiffFormatter(out).use { formatter ->
                    formatter.setRepository(git.repository)
                    entries.forEach { formatter.format(it) }
                }
                val text = out.toString("UTF-8")
                if (text.length > maxChars) text.take(maxChars) + "\n… diff truncated" else text
            }
        }.getOrElse { "" }
    }

    fun log(dir: File, max: Int = 15): String {
        if (!hasRepo(dir)) return "no git repository in the workspace"
        return runCatching {
            Git.open(dir).use { git ->
                val logs = git.log().setMaxCount(max).call()
                if (!logs.iterator().hasNext()) {
                    "no commits yet"
                } else {
                    logs.joinToString("\n") { commit ->
                        "${commit.name.take(8)} ${commit.shortMessage.take(80)}"
                    }
                }
            }
        }.getOrElse { "git error: ${it.message?.take(120)}" }
    }
}
