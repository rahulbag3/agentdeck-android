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
        private const val KEY_CLOUD_MODEL = "cloud.model"
        private const val KEY_GITHUB_REPO = "github.repo"
        private const val KEY_GITHUB_BRANCH = "github.branch"
        private const val KEY_APP_LOCK = "security.app-lock"
        private const val KEY_HOST_URL = "host.base-url"
        private const val KEY_HOST_INSECURE = "host.allow-insecure-lan"
        private const val KEY_HOST_NAME = "host.name"
        private const val KEY_HOST_PLATFORM = "host.platform"
    }
}
