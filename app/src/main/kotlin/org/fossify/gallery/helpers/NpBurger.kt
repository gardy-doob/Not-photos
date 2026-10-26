package org.fossify.gallery.helpers

import android.app.Activity
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.widget.PopupMenu

/** Menu « burger » : reprend les options de l'ancienne barre du haut, plus quelques actions Aura. */
object NpBurger {
    class Extra(val title: String, val action: () -> Unit)

    private const val EXTRA_BASE = 0x7A000

    fun show(activity: Activity, anchor: View, source: Menu?, extras: List<Extra> = emptyList()) {
        val popup = PopupMenu(activity, anchor, Gravity.END)
        var order = 0
        extras.forEachIndexed { i, extra -> popup.menu.add(Menu.NONE, EXTRA_BASE + i, order++, extra.title) }

        if (source != null) {
            for (i in 0 until source.size()) {
                val item = source.getItem(i)
                if (!item.isVisible || item.title.isNullOrEmpty()) continue
                val sub = item.subMenu
                if (sub != null) {
                    val target = popup.menu.addSubMenu(Menu.NONE, item.itemId, order++, item.title)
                    for (j in 0 until sub.size()) {
                        val child = sub.getItem(j)
                        if (child.isVisible) target.add(Menu.NONE, child.itemId, j, child.title)
                    }
                } else {
                    popup.menu.add(Menu.NONE, item.itemId, order++, item.title)
                }
            }
        }

        popup.setOnMenuItemClickListener { item ->
            val extraIndex = item.itemId - EXTRA_BASE
            when {
                extraIndex in extras.indices -> {
                    extras[extraIndex].action()
                    true
                }

                item.hasSubMenu() -> false
                else -> source?.performIdentifierAction(item.itemId, 0) ?: false
            }
        }
        popup.show()
    }
}
