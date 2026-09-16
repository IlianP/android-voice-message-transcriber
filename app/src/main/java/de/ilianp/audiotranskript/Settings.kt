package de.ilianp.audiotranskript

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** A selectable transcription language. An empty [code] means "auto-detect". */
data class LanguageOption(val label: String, val code: String)

val LANGUAGE_OPTIONS = listOf(
    LanguageOption("Automatisch erkennen", ""),
    LanguageOption("Deutsch", "de"),
    LanguageOption("Englisch", "en"),
)

/**
 * Persisted settings. API keys are stored in an [EncryptedSharedPreferences] file; if the
 * keystore-backed store cannot be created (rare, older/broken devices) we fall back to plain
 * preferences so the app keeps working. Values from the legacy plaintext "settings" file are
 * migrated on first run.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context).also { securePrefs ->
        migrateLegacy(context, securePrefs)
        dropObsoleteFalKey(context, securePrefs)
    }

    var openRouterApiKey: String
        get() = prefs.getString(KEY_OPENROUTER, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_OPENROUTER, value.trim()).apply()
        }

    var groqApiKey: String
        get() = prefs.getString(KEY_GROQ, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_GROQ, value.trim()).apply()
        }

    var sonioxApiKey: String
        get() = prefs.getString(KEY_SONIOX, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SONIOX, value.trim()).apply()
        }

    var languageCode: String
        get() = prefs.getString(KEY_LANG, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LANG, value).apply()
        }

    /** Last chosen playback speed factor (1.0, 1.5, 2.0, 2.5). */
    var playbackSpeedFactor: Float
        get() = prefs.getFloat(KEY_SPEED, 1.0f)
        set(value) {
            prefs.edit().putFloat(KEY_SPEED, value).apply()
        }

    companion object {
        private const val SECURE_FILE = "settings_secure"
        private const val LEGACY_FILE = "settings"
        private const val PROBE_FILE = "settings_encryption_probe"
        private const val KEY_OPENROUTER = "openrouter_api_key"
        private const val KEY_GROQ = "api_key"
        private const val KEY_SONIOX = "soniox_api_key"
        private const val KEY_LEGACY_FAL = "fal_api_key"
        private const val KEY_LANG = "language"
        private const val KEY_SPEED = "playback_speed"

        /**
         * Opens the encrypted store, and recreates it if — and only if — the existing file is
         * the thing that is broken.
         *
         * The master key lives in the Android Keystore, which no backup or device transfer
         * carries along and which the system can also drop on its own (some lock-screen
         * changes invalidate keys). Next to a surviving [SECURE_FILE] that leaves ciphertext
         * nobody can read any more, this app included. Falling straight through to plain
         * preferences would keep using that same file name and write every newly entered API
         * key into it in cleartext, permanently and silently.
         *
         * Recreating the store is only the right answer when encryption itself works here,
         * though. On a device that can never build a keystore-backed store, every single
         * construction would otherwise delete the user's keys — so [canEncrypt] probes with a
         * throwaway file first, and a failing probe means the stored values are kept and used
         * as they are.
         */
        private fun createPrefs(context: Context): SharedPreferences = try {
            openEncrypted(context, SECURE_FILE)
        } catch (first: Exception) {
            if (canEncrypt(context)) {
                Log.w("AudioTranskript", "Encrypted store unreadable, recreating it", first)
                context.deleteSharedPreferences(SECURE_FILE)
                try {
                    openEncrypted(context, SECURE_FILE)
                } catch (second: Exception) {
                    Log.w("AudioTranskript", "Recreating the store failed, using plain prefs", second)
                    context.getSharedPreferences(SECURE_FILE, Context.MODE_PRIVATE)
                }
            } else {
                Log.w("AudioTranskript", "Encryption unavailable, using plain prefs", first)
                context.getSharedPreferences(SECURE_FILE, Context.MODE_PRIVATE)
            }
        }

        /**
         * Whether a keystore-backed store can be created at all, checked on a throwaway file so
         * the answer says nothing about the state of [SECURE_FILE].
         */
        private fun canEncrypt(context: Context): Boolean = try {
            openEncrypted(context, PROBE_FILE)
            context.deleteSharedPreferences(PROBE_FILE)
            true
        } catch (e: Exception) {
            false
        }

        private fun openEncrypted(context: Context, fileName: String): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        /** Copies keys from the old plaintext file into [securePrefs] once, then clears them. */
        private fun migrateLegacy(context: Context, securePrefs: SharedPreferences) {
            val legacy = context.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
            val hasLegacy = legacy.contains(KEY_GROQ) || legacy.contains(KEY_LANG)
            if (!hasLegacy || securePrefs.contains(KEY_GROQ)) return

            securePrefs.edit()
                .putString(KEY_GROQ, legacy.getString(KEY_GROQ, "") ?: "")
                .putString(KEY_LANG, legacy.getString(KEY_LANG, "") ?: "")
                .apply()
            legacy.edit().clear().apply()
        }

        /**
         * fal.ai was replaced by Soniox, so a stored fal key is dead weight. Remove it from both
         * stores — including the plaintext file, which [migrateLegacy] may have skipped.
         */
        private fun dropObsoleteFalKey(context: Context, securePrefs: SharedPreferences) {
            if (securePrefs.contains(KEY_LEGACY_FAL)) {
                securePrefs.edit().remove(KEY_LEGACY_FAL).apply()
            }
            val legacy = context.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
            if (legacy.contains(KEY_LEGACY_FAL)) {
                legacy.edit().remove(KEY_LEGACY_FAL).apply()
            }
        }
    }
}
