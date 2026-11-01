package de.athena.app

import android.content.Context
import org.json.JSONObject

/** Shared catalogs are also served to the web UI; HA entity names are not translated. */
class I18n(context: Context) {
    private val catalogs=listOf("de","en").associateWith {language ->
        context.assets.open("i18n/$language.json").bufferedReader().use {JSONObject(it.readText())}
    }
    fun text(language: String,key: String): String = catalogs[language]?.optString(key,key) ?: key
}
