package org.fossify.gallery.helpers

import android.content.Context

/** Réglages propres à Aura. */
object NpSettings {
    const val STYLE_TIMELINE = 0
    const val STYLE_CARDS = 1

    private const val PREFS = "np_settings"
    private const val KEY_SHOW_JALONS = "show_jalons"
    private const val KEY_STYLE = "jalon_style"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun showJalons(context: Context) = prefs(context).getBoolean(KEY_SHOW_JALONS, true)

    fun setShowJalons(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_SHOW_JALONS, value).apply()

    fun style(context: Context) = prefs(context).getInt(KEY_STYLE, STYLE_TIMELINE)

    fun setStyle(context: Context, value: Int) = prefs(context).edit().putInt(KEY_STYLE, value).apply()
}
