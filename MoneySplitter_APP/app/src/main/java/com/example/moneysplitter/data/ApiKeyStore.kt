package com.example.moneysplitter.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object ApiKeyStore {

    private const val PREFS_NAME = "moneysplitter_secure_prefs"
    private const val KEY_OPENAI = "openai_api_key"

    private fun getPrefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun getApiKey(context: Context): String? =
        getPrefs(context).getString(KEY_OPENAI, null)?.takeIf { it.isNotBlank() }

    fun setApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_OPENAI, key.trim()).apply()
    }

    fun hasApiKey(context: Context): Boolean = getApiKey(context) != null
}
