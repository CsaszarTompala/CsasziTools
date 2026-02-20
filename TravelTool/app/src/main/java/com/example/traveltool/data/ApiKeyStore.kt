package com.example.traveltool.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Stores API keys securely using EncryptedSharedPreferences.
 * Keys are encrypted at rest using Android Keystore, so even if someone
 * extracts the app data, the keys cannot be read.
 */
object ApiKeyStore {

    private const val PREFS_NAME = "traveltool_secure_keys"
    private const val KEY_OPENAI = "openai_api_key"
    private const val KEY_OPENAI_MODEL = "openai_model"
    private const val DEFAULT_MODEL = "gpt-4o-mini"

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getOpenAiKey(context: Context): String {
        return try {
            getEncryptedPrefs(context).getString(KEY_OPENAI, "") ?: ""
        } catch (_: Exception) {
            // Fallback if encrypted prefs fail
            ""
        }
    }

    fun setOpenAiKey(context: Context, key: String) {
        try {
            getEncryptedPrefs(context).edit().putString(KEY_OPENAI, key).apply()
        } catch (_: Exception) {
            // Silent fail — user can re-enter the key
        }
    }

    fun getOpenAiModel(context: Context): String {
        return try {
            getEncryptedPrefs(context).getString(KEY_OPENAI_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        } catch (_: Exception) {
            DEFAULT_MODEL
        }
    }

    fun setOpenAiModel(context: Context, model: String) {
        try {
            getEncryptedPrefs(context).edit().putString(KEY_OPENAI_MODEL, model).apply()
        } catch (_: Exception) {
            // Silent fail
        }
    }

    fun resetOpenAiModel(context: Context) {
        setOpenAiModel(context, DEFAULT_MODEL)
    }
}
