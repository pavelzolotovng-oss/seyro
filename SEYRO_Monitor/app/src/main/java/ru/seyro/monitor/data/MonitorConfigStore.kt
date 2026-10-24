package ru.seyro.monitor.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.net.URI
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.monitorDataStore by preferencesDataStore(name = "monitor_settings")

class MonitorConfigStore(context: Context) {
    private val dataStore = context.applicationContext.monitorDataStore

    val config: Flow<MonitorConfig> = dataStore.data.map { preferences ->
        val encryptedToken = preferences[TOKEN]
        val token = encryptedToken?.let { runCatching { decrypt(it) }.getOrDefault("") }.orEmpty()
        MonitorConfig(
            baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
            token = token
        )
    }

    suspend fun save(baseUrl: String, token: String) {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        require(token.isNotBlank()) { "Введите monitor-token" }
        require('\n' !in token && '\r' !in token) { "Токен содержит недопустимые символы" }
        val encryptedToken = encrypt(token.trim())
        dataStore.edit { preferences ->
            preferences[BASE_URL] = normalizedUrl
            preferences[TOKEN] = encryptedToken
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(TOKEN)
            preferences.remove(BASE_URL)
        }
    }

    private fun normalizeBaseUrl(value: String): String {
        val normalized = value.trim().trimEnd('/')
        val uri = runCatching { URI(normalized) }.getOrNull()
            ?: throw IllegalArgumentException("Укажите корректный HTTPS URL, например https://admin.seyro.ru")
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "Укажите корректный HTTPS URL, например https://admin.seyro.ru"
        }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "URL не должен содержать логин, пароль, параметры или якорь"
        }
        return normalized
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = ByteBuffer.allocate(cipher.iv.size + encrypted.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val packed = Base64.decode(value, Base64.NO_WRAP)
        require(packed.size > GCM_IV_LENGTH) { "Сохранённый токен повреждён" }
        val buffer = ByteBuffer.wrap(packed)
        val iv = ByteArray(GCM_IV_LENGTH).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), javax.crypto.spec.GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("token_encrypted")
        const val DEFAULT_BASE_URL = "https://admin.seyro.ru"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "seyro_monitor_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
    }
}
