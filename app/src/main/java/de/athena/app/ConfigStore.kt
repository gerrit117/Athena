package de.athena.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("athena", Context.MODE_PRIVATE)
    val deviceId: String = prefs.getString("alarmDeviceId",null) ?: java.util.UUID.randomUUID().toString().also {
        check(prefs.edit().putString("alarmDeviceId",it).commit())
    }
    var lastZone: String
        get()=prefs.getString("lastZone",java.util.TimeZone.getDefault().id)!!
        set(value) {check(prefs.edit().putString("lastZone",value).commit())}
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("athena.credentials", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("athena.credentials", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    var config: AppConfig
        get() = runCatching { AppConfig.from(JSONObject(prefs.getString("config", "{}")!!)) }.getOrDefault(AppConfig())
        set(value) { check(prefs.edit().putString("config", value.json().toString()).commit()) }
    var token: String
        get() = runCatching {
            val value = prefs.getString("haCredential", null) ?: return ""
            val parts = value.split(':')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrDefault("")
        set(value) {
            val edit = prefs.edit()
            if (value.isBlank()) edit.remove("haCredential") else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
                val encrypted = cipher.doFinal(value.toByteArray())
                edit.putString("haCredential", Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }
            check(edit.commit())
        }
    var browserSessions: String
        get() = prefs.getString("browserSessions", "{}")!!
        set(value) { check(prefs.edit().putString("browserSessions", value).remove("webKey").commit()) }
}
