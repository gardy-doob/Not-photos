package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.format.Formatter
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.exifinterface.media.ExifInterface
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.databinding.NpInfoSheetBinding
import org.fossify.gallery.models.Medium
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Infos d'une photo façon Google Photos : en glissant vers le haut, la photo remonte et un panneau
 * blanc apparaît avec la date, le lieu et les détails (appareil, fichier, stockage).
 */
class NpInfoSheet(
    private val activity: Activity,
    private val binding: NpInfoSheetBinding,
    private val photo: View,
    private val bars: () -> List<View>
) {
    var isShown = false
        private set

    private val dateFormat = SimpleDateFormat("EEE d MMM yyyy • HH:mm", Locale.getDefault())

    init {
        binding.npInfoDismiss.setOnClickListener { hide() }
        val detector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (velocityY > 800 && abs(velocityY) > abs(velocityX) && !binding.npInfoPanel.canScrollVertically(-1)) {
                    hide()
                    return true
                }
                return false
            }
        })
        binding.npInfoPanel.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    fun show(medium: Medium) {
        fill(medium)
        if (isShown) return
        isShown = true
        val sheet = binding.root
        sheet.visibility = View.VISIBLE
        val panel = binding.npInfoPanel
        val maxHeight = (activity.resources.displayMetrics.heightPixels * 0.62f).toInt()
        panel.layoutParams = panel.layoutParams.apply { height = maxHeight }
        panel.post {
            val h = panel.height.toFloat().takeIf { it > 0 } ?: maxHeight.toFloat()
            panel.translationY = h
            panel.animate().translationY(0f).setDuration(320).setInterpolator(DecelerateInterpolator(1.8f)).start()
            photo.animate().translationY(-h * 0.5f).setDuration(320).setInterpolator(DecelerateInterpolator(1.8f)).start()
        }
        bars().forEach { it.animate().alpha(0f).setDuration(180).withEndAction { it.visibility = View.INVISIBLE }.start() }
    }

    fun hide() {
        if (!isShown) return
        isShown = false
        val panel = binding.npInfoPanel
        panel.animate().translationY(panel.height.toFloat()).setDuration(240).withEndAction { binding.root.visibility = View.GONE }.start()
        photo.animate().translationY(0f).setDuration(240).start()
        bars().forEach {
            it.visibility = View.VISIBLE
            it.animate().alpha(1f).setDuration(200).start()
        }
    }

    private fun fill(medium: Medium) {
        val ts = if (medium.taken > 0L) medium.taken else medium.modified
        binding.npInfoDate.text = dateFormat.format(ts).replaceFirstChar { it.uppercase() }
        binding.npInfoPlaceRow.visibility = View.GONE
        binding.npInfoCameraCard.visibility = View.GONE
        binding.npInfoFileTitle.text = medium.name
        binding.npInfoFileSub.text = ""
        binding.npInfoStorageTitle.text = activity.getString(R.string.np_info_on_device)
        binding.npInfoStorageSub.text = medium.parentPath

        ensureBackgroundThread {
            val file = File(medium.path)
            val size = if (file.exists()) Formatter.formatShortFileSize(activity, file.length()) else ""
            var width = 0
            var height = 0
            var camera = ""
            var settings = ""
            var lat: Double? = null
            var lon: Double? = null

            try {
                if (medium.isVideo()) {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(medium.path)
                    width = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    height = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let { loc ->
                        Regex("([+-][0-9.]+)([+-][0-9.]+)").find(loc)?.let {
                            lat = it.groupValues[1].toDoubleOrNull()
                            lon = it.groupValues[2].toDoubleOrNull()
                        }
                    }
                    r.release()
                } else {
                    val exif = ExifInterface(medium.path)
                    val make = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty().trim()
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty().trim()
                    camera = if (model.startsWith(make, true) || make.isEmpty()) model else "$make $model"
                    val parts = ArrayList<String>()
                    exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { parts.add("f/${trim(it)}") }
                    exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let {
                        parts.add(if (it < 1) "1/${Math.round(1 / it)} s" else "${trim(it)} s")
                    }
                    exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { parts.add("ISO $it") }
                    exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { parts.add("${trim(it)} mm") }
                    settings = parts.joinToString(" · ")
                    exif.latLong?.let {
                        lat = it[0]
                        lon = it[1]
                    }
                    width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                    height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                    if (width <= 0 || height <= 0) {
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(medium.path, o)
                        width = o.outWidth
                        height = o.outHeight
                    }
                }
            } catch (ignored: Exception) {
            }

            val mp = if (width > 0 && height > 0) String.format(Locale.getDefault(), "%.1f Mpx", width * height / 1_000_000f) else ""
            val res = if (width > 0 && height > 0) "$width × $height" else ""

            activity.runOnUiThread {
                binding.npInfoFileSub.text = listOf(mp, res).filter { it.isNotEmpty() }.joinToString(" · ")
                binding.npInfoStorageTitle.text = listOf(activity.getString(R.string.np_info_on_device), size).filter { it.isNotEmpty() }
                    .joinToString(" • ")
                if (camera.isNotEmpty() || settings.isNotEmpty()) {
                    binding.npInfoCameraCard.visibility = View.VISIBLE
                    binding.npInfoCameraTitle.text = camera.ifEmpty { "Appareil photo" }
                    binding.npInfoCameraSub.text = settings
                    binding.npInfoCameraSub.visibility = if (settings.isEmpty()) View.GONE else View.VISIBLE
                }
                val la = lat
                val lo = lon
                if (la != null && lo != null && !(la == 0.0 && lo == 0.0)) {
                    binding.npInfoPlaceRow.visibility = View.VISIBLE
                    binding.npInfoPlace.text = String.format(Locale.US, "%.5f, %.5f", la, lo)
                    binding.npInfoMaps.setOnClickListener {
                        try {
                            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$la,$lo?q=$la,$lo")))
                        } catch (ignored: Exception) {
                        }
                    }
                }
            }
        }
    }

    private fun trim(value: Double): String {
        val s = String.format(Locale.US, "%.1f", value)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }
}
