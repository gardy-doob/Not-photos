package org.fossify.gallery.helpers

import android.app.Activity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.fossify.gallery.R

/** Renommer ou retirer un jalon. */
object NpJalonDialog {
    fun show(activity: Activity, cutPath: String, onChanged: () -> Unit) {
        val cut = NpJalons.all(activity).firstOrNull { it.path == cutPath } ?: return
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val input = EditText(activity).apply {
            setText(cut.name)
            hint = activity.getString(R.string.np_jalon_name_hint)
            setSingleLine()
            setSelection(text.length)
        }
        val box = FrameLayout(activity).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.np_jalon)
            .setView(box)
            .setPositiveButton(R.string.np_save) { _, _ ->
                NpJalons.rename(activity, cutPath, input.text.toString())
                onChanged()
            }
            .setNegativeButton(R.string.np_remove) { _, _ ->
                NpJalons.remove(activity, cutPath)
                Toast.makeText(activity, R.string.np_jalon_removed, Toast.LENGTH_SHORT).show()
                onChanged()
            }
            .setNeutralButton(R.string.np_cancel, null)
            .show()
    }
}
