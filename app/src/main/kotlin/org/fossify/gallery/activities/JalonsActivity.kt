package org.fossify.gallery.activities

import android.content.Intent
import android.graphics.Outline
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.gallery.databinding.ActivityJalonsBinding
import org.fossify.gallery.helpers.NP_SCROLL_TO
import org.fossify.gallery.helpers.NP_TAB_GALLERY
import org.fossify.gallery.helpers.NP_TAB_JALONS
import org.fossify.gallery.helpers.NpJalonDialog
import org.fossify.gallery.helpers.NpJalons
import org.fossify.gallery.helpers.NpNav
import java.text.SimpleDateFormat
import java.util.Locale

/** Onglet Jalons : tes événements sous forme d'albums. */
class JalonsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityJalonsBinding::inflate)
    private val dateFormat = SimpleDateFormat("EEE d MMM yyyy · HH:mm", Locale.getDefault())
    private var cuts: List<NpJalons.Cut> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(
            padTopSystem = listOf(binding.jalonsList),
            padBottomSystem = listOf(binding.jalonsList),
            moveBottomSystem = listOf(binding.npNav.root)
        )
        org.fossify.gallery.helpers.NpDiag.watch(this)
        NpNav.setup(this, NP_TAB_JALONS, onReselect = { binding.jalonsList.smoothScrollToPosition(0) })
        binding.jalonsList.layoutManager = GridLayoutManager(this, 2)
        binding.jalonsList.adapter = JalonsAdapter()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        cuts = NpJalons.all(this).sortedByDescending { it.taken }
        binding.jalonsEmpty.visibility = if (cuts.isEmpty()) View.VISIBLE else View.GONE
        binding.jalonsEmpty.setTextColor(getProperTextColor())
        binding.jalonsList.adapter?.notifyDataSetChanged()
    }

    override fun onBackPressedCompat(): Boolean {
        NpNav.go(this, NP_TAB_GALLERY)
        return true
    }

    private fun openInGallery(cut: NpJalons.Cut) {
        val intent = NpNav.intentFor(this, NP_TAB_GALLERY).apply {
            putExtra(NP_SCROLL_TO, cut.path)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private inner class JalonsAdapter : RecyclerView.Adapter<CardHolder>() {
        private val density = resources.displayMetrics.density

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder {
            val size = (resources.displayMetrics.widthPixels - (16 * density).toInt()) / 2 - (12 * density).toInt()
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                val m = (6 * density).toInt()
                setPadding(m, m, m, (10 * density).toInt())
                isClickable = true
                isFocusable = true
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
            }
            val image = ImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER_HORIZONTAL }
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, 16 * density)
                    }
                }
                setBackgroundColor(0xFFE7E0DB.toInt())
            }
            val title = TextView(parent.context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding((2 * density).toInt(), (8 * density).toInt(), 0, 0)
                maxLines = 1
            }
            val subtitle = TextView(parent.context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                alpha = 0.7f
                setPadding((2 * density).toInt(), (2 * density).toInt(), 0, 0)
                maxLines = 1
            }
            card.addView(image)
            card.addView(title)
            card.addView(subtitle)
            return CardHolder(card, image, title, subtitle)
        }

        override fun onBindViewHolder(holder: CardHolder, position: Int) {
            val cut = cuts[position]
            val textColor = getProperTextColor()
            holder.title.setTextColor(textColor)
            holder.subtitle.setTextColor(textColor)
            val date = if (cut.taken > 0L) dateFormat.format(cut.taken) else ""
            holder.title.text = cut.name.ifEmpty { date }
            holder.subtitle.text = if (cut.name.isNotEmpty()) date else ""
            holder.subtitle.visibility = if (cut.name.isNotEmpty()) View.VISIBLE else View.GONE
            Glide.with(holder.image)
                .load(cut.path)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .centerCrop()
                .into(holder.image)
            holder.itemView.setOnClickListener { openInGallery(cut) }
            holder.itemView.setOnLongClickListener {
                NpJalonDialog.show(this@JalonsActivity, cut.path) { refresh() }
                true
            }
        }

        override fun getItemCount() = cuts.size
    }

    private class CardHolder(view: View, val image: ImageView, val title: TextView, val subtitle: TextView) :
        RecyclerView.ViewHolder(view)
}
