package com.example.budgetflow

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage private constructor(context: Context) {
    private val sharedPreferences: SharedPreferences

    init {
        // Always force application context to avoid memory leaks
        val appContext = context.applicationContext

        // 1. Create or retrieve the Master Key from the Android KeyStore
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // 2. Initialize EncryptedSharedPreferences
        sharedPreferences = EncryptedSharedPreferences.create(
            appContext,
            "secure_prefs_file", // Name of the file
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // Scheme for encrypting keys
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM // Scheme for encrypting values
        )
    }

    // Saves a secure String key-value pair.
    fun saveString(key: String, value: String) {
        sharedPreferences.edit().apply {
            putString(key, value)
            commit() // Use apply() for asynchronous saving; use commit() for synchronous saving
        }
    }

    // Retrieves a secure String value.
    fun getString(key: String, defaultValue: String = ""): String {
        return sharedPreferences.getString(key, defaultValue)!!
    }

    // Clears a specific key from storage.
    fun removeKey(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: SecureStorage? = null

        // Call this to get the single instance of SecureStorage
        fun getInstance(context: Context): SecureStorage {
            return INSTANCE ?: synchronized(this) {
                val instance = SecureStorage(context)
                INSTANCE = instance
                instance
            }
        }
    }
}