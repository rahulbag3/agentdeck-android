package com.agentdeck.app.data

import android.content.Context

/** Non-secret provider settings. API keys and tokens live in SecureStore. */
class AppConfig(context: Context) {
    private val prefs = context.getSharedPreferences("agentdeck.config", Context.MODE_PRIVATE)

    var cloudBaseUrl: String
        get() = prefs.getString(KEY_CLOUD_BASE_URL, DEFAULT_CLOUD_BASE_URL).orEmpty()
        set(value) = prefs.edit().putString(KEY_CLOUD_BASE_URL, value).apply()

    var cloudModel: String
        get() = prefs.getString(KEY_CLOUD_MODEL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CLOUD_MODEL, value).apply()

    /** JSON array of saved cloud API profiles (no secrets; keys live in SecureStore). */
    var cloudProfilesJson: String
        get() = prefs.getString(KEY_CLOUD_PROFILES, "[]").orEmpty()
        set(value) = prefs.edit().putString(KEY_CLOUD_PROFILES, value).apply()

    /** Device shell (Termux over loopback SSH) for real toolchains. */
    var sshEnabled: Boolean
        get() = prefs.getBoolean(KEY_SSH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SSH_ENABLED, value).apply()

    var sshPort: Int
        get() = prefs.getInt(KEY_SSH_PORT, 8022)
        set(value) = prefs.edit().putInt(KEY_SSH_PORT, value).apply()

    var sshUser: String
        get() = prefs.getString(KEY_SSH_USER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SSH_USER, value).apply()

    var activeCloudProfileId: String
        get() = prefs.getString(KEY_ACTIVE_CLOUD_PROFILE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ACTIVE_CLOUD_PROFILE, value).apply()

    var githubRepo: String
        get() = prefs.getString(KEY_GITHUB_REPO, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_GITHUB_REPO, value).apply()

    var githubBranch: String
        get() = prefs.getString(KEY_GITHUB_BRANCH, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_GITHUB_BRANCH, value).apply()

    var appLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(value) {
            prefs.edit().putBoolean(KEY_APP_LOCK, value).commit()
        }

    var hostBaseUrl: String
        get() = prefs.getString(KEY_HOST_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_HOST_URL, value).apply()

    var hostAllowInsecureLan: Boolean
        get() = prefs.getBoolean(KEY_HOST_INSECURE, false)
        set(value) = prefs.edit().putBoolean(KEY_HOST_INSECURE, value).apply()

    var hostName: String
        get() = prefs.getString(KEY_HOST_NAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_HOST_NAME, value).apply()

    var hostPlatform: String
        get() = prefs.getString(KEY_HOST_PLATFORM, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_HOST_PLATFORM, value).apply()

    /** JSON catalogue of imported GGUF models. */
    var localModelsJson: String
        get() = prefs.getString(KEY_LOCAL_MODELS, "[]").orEmpty()
        set(value) = prefs.edit().putString(KEY_LOCAL_MODELS, value).apply()

    /** auto | npu | gpu | cpu */
    var localAccelerator: String
        get() = prefs.getString(KEY_LOCAL_ACCELERATOR, "auto").orEmpty()
        set(value) = prefs.edit().putString(KEY_LOCAL_ACCELERATOR, value).apply()

    fun clearHost() {
        prefs.edit()
            .remove(KEY_HOST_URL)
            .remove(KEY_HOST_INSECURE)
            .remove(KEY_HOST_NAME)
            .remove(KEY_HOST_PLATFORM)
            .apply()
    }

    fun clearCloud() {
        prefs.edit().remove(KEY_CLOUD_BASE_URL).remove(KEY_CLOUD_MODEL).apply()
    }

    fun clearGitHub() {
        prefs.edit().remove(KEY_GITHUB_REPO).remove(KEY_GITHUB_BRANCH).apply()
    }

    companion object {
        const val DEFAULT_CLOUD_BASE_URL = "https://api.openai.com/v1"
        private const val KEY_CLOUD_BASE_URL = "cloud.base-url"
        private const val KEY_CLOUD_PROFILES = "cloud.profiles"
        private const val KEY_ACTIVE_CLOUD_PROFILE = "cloud.active-profile"
        private const val KEY_SSH_ENABLED = "ssh.enabled"
        private const val KEY_SSH_PORT = "ssh.port"
        private const val KEY_SSH_USER = "ssh.user"
        private const val KEY_CLOUD_MODEL = "cloud.model"
        private const val KEY_GITHUB_REPO = "github.repo"
        private const val KEY_GITHUB_BRANCH = "github.branch"
        private const val KEY_APP_LOCK = "security.app-lock"
        private const val KEY_HOST_URL = "host.base-url"
        private const val KEY_HOST_INSECURE = "host.allow-insecure-lan"
        private const val KEY_HOST_NAME = "host.name"
        private const val KEY_HOST_PLATFORM = "host.platform"
        private const val KEY_LOCAL_MODELS = "local.models"
        private const val KEY_LOCAL_ACCELERATOR = "local.accelerator"
    }
}
