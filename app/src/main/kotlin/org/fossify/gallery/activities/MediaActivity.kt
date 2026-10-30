package org.fossify.gallery.activities

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.core.net.toUri
import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import org.fossify.commons.dialogs.CreateNewFolderDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.deleteFiles
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getIsPathDirectory
import org.fossify.commons.extensions.getLatestMediaByDateId
import org.fossify.commons.extensions.getLatestMediaId
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.handleLockedFolderOpening
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.IS_FROM_GALLERY
import org.fossify.commons.helpers.REQUEST_EDIT_IMAGE
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.asynctasks.GetMediaAsynctask
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.databinding.ActivityMediaBinding
import org.fossify.gallery.dialogs.ChangeGroupingDialog
import org.fossify.gallery.dialogs.ChangeSortingDialog
import org.fossify.gallery.dialogs.ChangeViewTypeDialog
import org.fossify.gallery.dialogs.FilterMediaDialog
import org.fossify.gallery.dialogs.GrantAllFilesDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.deleteDBPath
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.emptyAndDisableTheRecycleBin
import org.fossify.gallery.extensions.emptyTheRecycleBin
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.getCachedMedia
import org.fossify.gallery.extensions.getHumanizedFilename
import org.fossify.gallery.extensions.isDownloadsFolder
import org.fossify.gallery.extensions.launchAbout
import org.fossify.gallery.extensions.launchCamera
import org.fossify.gallery.extensions.launchSettings
import org.fossify.gallery.extensions.launchGesturePlayer
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.movePathsInRecycleBin
import org.fossify.gallery.extensions.openPath
import org.fossify.gallery.extensions.openRecycleBin
import org.fossify.gallery.extensions.restoreRecycleBinPaths
import org.fossify.gallery.extensions.showRecycleBinEmptyingDialog
import org.fossify.gallery.extensions.showRestoreConfirmationDialog
import org.fossify.gallery.extensions.tryDeleteFileDirItem
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.NP_FORCE_SHOW_ALL
import org.fossify.gallery.helpers.NP_ONLY_VIDEOS
import org.fossify.gallery.helpers.NP_TAB
import org.fossify.gallery.helpers.NP_TAB_ALL
import org.fossify.gallery.helpers.NP_TAB_FOLDERS
import org.fossify.gallery.helpers.NP_TAB_GALLERY
import org.fossify.gallery.helpers.NP_TAB_VIDEOS
import org.fossify.gallery.helpers.NpNav
import org.fossify.gallery.helpers.NpDiag
import org.fossify.gallery.helpers.NpBurger
import org.fossify.gallery.helpers.NpSettings
import org.fossify.gallery.helpers.NpJalonDecoration
import org.fossify.gallery.helpers.NP_CASCADE
import org.fossify.gallery.helpers.NpJalons
import org.fossify.gallery.helpers.NpJalonDialog
import org.fossify.gallery.helpers.NP_SCROLL_TO
import org.fossify.gallery.helpers.GET_ANY_INTENT
import org.fossify.gallery.helpers.GET_IMAGE_INTENT
import org.fossify.gallery.helpers.GET_VIDEO_INTENT
import org.fossify.gallery.helpers.GridSpacingItemDecoration
import org.fossify.gallery.helpers.IS_IN_RECYCLE_BIN
import org.fossify.gallery.helpers.MAX_COLUMN_COUNT
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.PICKED_PATHS
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.SET_WALLPAPER_INTENT
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SHOW_FAVORITES
import org.fossify.gallery.helpers.SHOW_RECYCLE_BIN
import org.fossify.gallery.helpers.SHOW_TEMP_HIDDEN_DURATION
import org.fossify.gallery.helpers.SKIP_AUTHENTICATION
import org.fossify.gallery.helpers.SLIDESHOW_START_ON_ENTER
import org.fossify.gallery.helpers.VIDEO_PLAYER_APP
import org.fossify.gallery.helpers.VIDEO_PLAYER_SYSTEM
import org.fossify.gallery.interfaces.MediaOperationsListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import java.io.File
import java.io.IOException

open class MediaActivity : SimpleActivity(), MediaOperationsListener {
    override var isSearchBarEnabled = true
    
    private val LAST_MEDIA_CHECK_PERIOD = 3000L

    private var mPath = ""
    private var mIsGetImageIntent = false
    private var mIsGetVideoIntent = false
    private var mIsGetAnyIntent = false
    private var mIsGettingMedia = false
    private var mAllowPickingMultiple = false
    private var mShowAll = false
    private var npTab = NP_TAB_FOLDERS
    private var npOnlyVideos = false
    private var npForceShowAll = false
    private var npScrollTo: String? = null
    private var npRawMedia = ArrayList<ThumbnailItem>()
    private var npFocusCut: String? = null
    private var npCascadePending = false
    private var mLoadedInitialPhotos = false
    private var mShowLoadingIndicator = true
    private var mWasFullscreenViewOpen = false
    private var mLastSearchedText = ""
    private var mLatestMediaId = 0L
    private var mLatestMediaDateId = 0L
    private var mLastMediaHandler = Handler()
    private var mTempShowHiddenHandler = Handler()
    private var mCurrAsyncTask: GetMediaAsynctask? = null
    private var mZoomListener: MyRecyclerView.MyZoomListener? = null

    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mStoredShowFileTypes = true
    private var mStoredRoundedCorners = false
    private var mStoredMarkFavoriteItems = true
    private var mStoredTextColor = 0
    private var mStoredPrimaryColor = 0
    private var mStoredThumbnailSpacing = 0

    private val binding by viewBinding(ActivityMediaBinding::inflate)

    companion object {
        var mMedia = ArrayList<ThumbnailItem>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        intent.apply {
            mIsGetImageIntent = getBooleanExtra(GET_IMAGE_INTENT, false)
            mIsGetVideoIntent = getBooleanExtra(GET_VIDEO_INTENT, false)
            mIsGetAnyIntent = getBooleanExtra(GET_ANY_INTENT, false)
            mAllowPickingMultiple = getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            npOnlyVideos = getBooleanExtra(NP_ONLY_VIDEOS, false)
            npForceShowAll = getBooleanExtra(NP_FORCE_SHOW_ALL, false)
            npTab = getStringExtra(NP_TAB) ?: NP_TAB_FOLDERS
            npScrollTo = getStringExtra(NP_SCROLL_TO)
            npCascadePending = getBooleanExtra(NP_CASCADE, false)
        }

        binding.mediaRefreshLayout.setOnRefreshListener { getMedia() }
        try {
            mPath = intent.getStringExtra(DIRECTORY) ?: ""
        } catch (e: Exception) {
            showErrorToast(e)
            finish()
            return
        }

        setupOptionsMenu()
        refreshMenuItems()
        storeStateVariables()
        // Aura : interface plein écran, sans barre du haut ; les options passent dans le menu burger
        binding.mediaMenu.beGone()
        setupEdgeToEdge(
            padTopSystem = listOf(binding.mediaGrid, binding.npTopButtons.root, binding.npStatusScrim, binding.npDateHolder),
            padBottomImeAndSystem = listOf(binding.mediaGrid),
            moveBottomSystem = listOf(binding.npNav.root, binding.npSouvenirBar.root)
        )
        binding.npStatusScrim.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(androidx.core.graphics.ColorUtils.setAlphaComponent(getProperBackgroundColor(), 0xE6), 0)
        )
        binding.npTopButtons.npBurger.setOnClickListener { npShowBurger(it) }
        binding.npTopButtons.npVideoFilter.setOnClickListener { npToggleVideoFilter() }
        NpDiag.watch(this)

        if (mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent) {
            NpNav.hide(this)
        } else {
            npSetupNav()
        }
        npSetupGrid()
        org.fossify.gallery.helpers.NpCrash.showIfAny(this)

        if (mShowAll) {
            registerFileUpdateListener()
        }

        binding.mediaEmptyTextPlaceholder2.setOnClickListener {
            showFilterMediaDialog()
        }

    }

    override fun onStart() {
        super.onStart()
        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    private var npLastStyle = -1
    private var npLastCount = -1

    override fun onResume() {
        super.onResume()
        NpNav.applyLayout(this)
        updateMenuColors()
        val npStyle = NpSettings.style(this)
        if (npLastStyle != -1 && npLastStyle != npStyle) {
            npApplyGridStyle()
            getMediaAdapter()?.notifyDataSetChanged()
        }
        npLastStyle = npStyle
        if (npPendingStart != null && !binding.npSouvenirBar.root.isVisible()) {
            npShowPendingBar()
            getMediaAdapter()?.notifyDataSetChanged()
        } else if (npPendingStart == null && binding.npSouvenirBar.root.isVisible()) {
            binding.npSouvenirBar.root.beGone()
        }
        if (NpJalons.all(this).size != npLastCount) {
            npLastCount = NpJalons.all(this).size
            if (getMediaAdapter() != null) npRefreshJalons()
        }
        if (mStoredAnimateGifs != config.animateGifs) {
            getMediaAdapter()?.updateAnimateGifs(config.animateGifs)
        }

        if (mStoredCropThumbnails != config.cropThumbnails) {
            getMediaAdapter()?.updateCropThumbnails(config.cropThumbnails)
        }

        if (mStoredScrollHorizontally != config.scrollHorizontally) {
            mLoadedInitialPhotos = false
            binding.mediaGrid.adapter = null
            getMedia()
        }

        if (mStoredShowFileTypes != config.showThumbnailFileTypes) {
            getMediaAdapter()?.updateShowFileTypes(config.showThumbnailFileTypes)
        }

        if (mStoredTextColor != getProperTextColor()) {
            getMediaAdapter()?.updateTextColor(getProperTextColor())
        }

        val primaryColor = getProperPrimaryColor()
        if (mStoredPrimaryColor != primaryColor) {
            getMediaAdapter()?.updatePrimaryColor()
        }

        if (
            mStoredThumbnailSpacing != config.thumbnailSpacing
            || mStoredRoundedCorners != config.fileRoundedCorners
            || mStoredMarkFavoriteItems != config.markFavoriteItems
        ) {
            binding.mediaGrid.adapter = null
            setupAdapter()
        }

        refreshMenuItems()

        binding.mediaFastscroller.updateColors(primaryColor)
        binding.mediaRefreshLayout.isEnabled = config.enablePullToRefresh
        getMediaAdapter()?.apply {
            dateFormat = config.dateFormat
            timeFormat = getTimeFormat()
        }

        binding.loadingIndicator.setIndicatorColor(getProperPrimaryColor())
        binding.mediaEmptyTextPlaceholder.setTextColor(getProperTextColor())
        binding.mediaEmptyTextPlaceholder2.setTextColor(getProperPrimaryColor())
        binding.mediaEmptyTextPlaceholder2.bringToFront()

        // do not refresh Random sorted files after opening a fullscreen image and going Back
        val isRandomSorting = config.getFolderSorting(mPath) and SORT_BY_RANDOM != 0
        if (mMedia.isEmpty() || !isRandomSorting || (isRandomSorting && !mWasFullscreenViewOpen)) {
            if (shouldSkipAuthentication()) {
                tryLoadGallery()
            } else {
                handleLockedFolderOpening(mPath) { success ->
                    if (success) {
                        tryLoadGallery()
                    } else {
                        finish()
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mIsGettingMedia = false
        binding.mediaRefreshLayout.isRefreshing = false
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)

        if (!mMedia.isEmpty()) {
            mCurrAsyncTask?.stopFetching()
        }
    }

    override fun onStop() {
        super.onStop()

        if (config.temporarilyShowHidden || config.tempSkipDeleteConfirmation) {
            mTempShowHiddenHandler.postDelayed({
                config.temporarilyShowHidden = false
                config.tempSkipDeleteConfirmation = false
                config.tempSkipRecycleBin = false
            }, SHOW_TEMP_HIDDEN_DURATION)
        } else {
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (config.showAll && !isChangingConfigurations) {
            config.temporarilyShowHidden = false
            config.tempSkipDeleteConfirmation = false
            config.tempSkipRecycleBin = false
            unregisterFileUpdateListener()
            GalleryDatabase.destroyInstance()
        }

        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mediaMenu.isSearchOpen) {
            binding.mediaMenu.closeSearch()
            true
        } else if (npPendingStart != null) {
            npCancelPending()
            true
        } else if (npFocusCut != null) {
            npToggleFocus(npFocusCut!!)
            true
        } else if (npTab == NP_TAB_VIDEOS || (npTab == NP_TAB_ALL && npForceShowAll)) {
            npSwitchInPlace(NP_TAB_GALLERY)
            true
        } else {
            if (config.showAll) {
                appLockManager.lock()
            }

            when (npTab) {
                NP_TAB_GALLERY -> {
                    finishAffinity()
                    true
                }

                NP_TAB_VIDEOS, NP_TAB_ALL -> {
                    NpNav.go(this, NP_TAB_GALLERY)
                    true
                }

                else -> false
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == REQUEST_EDIT_IMAGE) {
            if (resultCode == RESULT_OK && resultData != null) {
                mMedia.clear()
                refreshItems()
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun refreshMenuItems() {
        val isDefaultFolder = !config.defaultFolder.isEmpty()
                && File(config.defaultFolder).compareTo(File(mPath)) == 0

        binding.mediaMenu.requireToolbar().menu.apply {
            findItem(R.id.group).isVisible = !config.scrollHorizontally

            findItem(R.id.empty_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.empty_disable_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.restore_all_files).isVisible = mPath == RECYCLE_BIN

            findItem(R.id.folder_view).isVisible = mShowAll
            findItem(R.id.open_camera).isVisible = mShowAll
            findItem(R.id.about).isVisible = mShowAll
            findItem(R.id.create_new_folder).isVisible =
                !mShowAll && mPath != RECYCLE_BIN && mPath != FAVORITES
            findItem(R.id.open_recycle_bin).isVisible = config.useRecycleBin && mPath != RECYCLE_BIN

            findItem(R.id.temporarily_show_hidden).isVisible = !config.shouldShowHidden
            findItem(R.id.stop_showing_hidden).isVisible =
                (!isRPlus() || isExternalStorageManager()) && config.temporarilyShowHidden

            findItem(R.id.set_as_default_folder).isVisible = !isDefaultFolder
            findItem(R.id.unset_as_default_folder).isVisible = isDefaultFolder

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            findItem(R.id.column_count).isVisible = viewType == VIEW_TYPE_GRID
            findItem(R.id.toggle_filename).isVisible = viewType == VIEW_TYPE_GRID
        }
    }

    private fun setupOptionsMenu() {
        binding.mediaMenu.requireToolbar().inflateMenu(R.menu.menu_media)
        binding.mediaMenu.toggleHideOnScroll(!config.scrollHorizontally)
        binding.mediaMenu.setupMenu()

        binding.mediaMenu.onSearchTextChangedListener = { text ->
            mLastSearchedText = text
            searchQueryChanged(text)
            binding.mediaRefreshLayout.isEnabled = text.isEmpty() && config.enablePullToRefresh
        }

        binding.mediaMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort -> showSortingDialog()
                R.id.filter -> showFilterMediaDialog()
                R.id.empty_recycle_bin -> emptyRecycleBin()
                R.id.empty_disable_recycle_bin -> emptyAndDisableRecycleBin()
                R.id.restore_all_files -> restoreAllFiles()
                R.id.toggle_filename -> toggleFilenameVisibility()
                R.id.open_camera -> launchCamera()
                R.id.folder_view -> switchToFolderView()
                R.id.change_view_type -> changeViewType()
                R.id.group -> showGroupByDialog()
                R.id.create_new_folder -> createNewFolder()
                R.id.open_recycle_bin -> openRecycleBin()
                R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
                R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
                R.id.column_count -> changeColumnCount()
                R.id.set_as_default_folder -> setAsDefaultFolder()
                R.id.unset_as_default_folder -> unsetAsDefaultFolder()
                R.id.slideshow -> startSlideshow()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun startSlideshow() {
        if (mMedia.isNotEmpty()) {
            hideKeyboard()
            Intent(this, ViewPagerActivity::class.java).apply {
                val item = mMedia.firstOrNull { it is Medium } as? Medium ?: return
                putExtra(SKIP_AUTHENTICATION, shouldSkipAuthentication())
                putExtra(PATH, item.path)
                putExtra(SHOW_ALL, mShowAll)
                putExtra(SLIDESHOW_START_ON_ENTER, true)
                startActivity(this)
            }
        }
    }

    private fun updateMenuColors() {
        binding.mediaMenu.updateColors()
    }

    private fun storeStateVariables() {
        mStoredTextColor = getProperTextColor()
        mStoredPrimaryColor = getProperPrimaryColor()
        config.apply {
            mStoredAnimateGifs = animateGifs
            mStoredCropThumbnails = cropThumbnails
            mStoredScrollHorizontally = scrollHorizontally
            mStoredShowFileTypes = showThumbnailFileTypes
            mStoredMarkFavoriteItems = markFavoriteItems
            mStoredThumbnailSpacing = thumbnailSpacing
            mStoredRoundedCorners = fileRoundedCorners
            mShowAll = (showAll || npForceShowAll) && mPath != RECYCLE_BIN
        }
    }

    private fun searchQueryChanged(text: String) {
        ensureBackgroundThread {
            try {
                val filtered = mMedia
                    .filter { it is Medium && it.name.contains(text, true) } as ArrayList
                filtered.sortBy { it is Medium && !it.name.startsWith(text, true) }
                val grouped = MediaFetcher(applicationContext).groupMedia(
                    media = filtered as ArrayList<Medium>, path = mPath
                )
                runOnUiThread {
                    if (grouped.isEmpty()) {
                        binding.mediaEmptyTextPlaceholder.text =
                            getString(org.fossify.commons.R.string.no_items_found)
                        binding.mediaEmptyTextPlaceholder.beVisible()
                        binding.mediaFastscroller.beGone()
                    } else {
                        binding.mediaEmptyTextPlaceholder.beGone()
                        binding.mediaFastscroller.beVisible()
                    }

                    handleGridSpacing(grouped)
                    getMediaAdapter()?.updateMedia(grouped)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun tryLoadGallery() {
        requestMediaPermissions {
            val dirName = when (mPath) {
                FAVORITES -> getString(org.fossify.commons.R.string.favorites)
                RECYCLE_BIN -> getString(org.fossify.commons.R.string.recycle_bin)
                config.OTGPath -> getString(org.fossify.commons.R.string.usb)
                else -> getHumanizedFilename(mPath)
            }

            val searchHint = if (mShowAll) {
                getString(org.fossify.commons.R.string.search_files)
            } else {
                getString(org.fossify.commons.R.string.search_in_placeholder, dirName)
            }

            binding.mediaMenu.updateHintText(searchHint)
            if (!mShowAll) {
                binding.mediaMenu.toggleForceArrowBackIcon(true)
                binding.mediaMenu.onNavigateBackClickListener = {
                    performDefaultBack()
                }
            }

            if (mShowLoadingIndicator) {
                binding.loadingIndicator.show()
                mShowLoadingIndicator = false
            }

            getMedia()
            setupLayoutManager()
        }
    }

    private fun getMediaAdapter() = binding.mediaGrid.adapter as? MediaAdapter

    // ---------- Aura ----------

    /** Retour sur la Galerie (déjà ouverte) depuis un autre onglet : rien à recharger. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent.getStringExtra(NP_TAB) ?: return
        intent.getStringExtra(NP_SCROLL_TO)?.let {
            npScrollTo = it
            npFocusCut = null
        }
        if (tab != npTab && !npSwitchInPlace(tab)) return
        npSetupNav()
        if (npScrollTo != null) {
            npRefreshJalons()
            binding.mediaGrid.post { npScrollToTarget() }
        } else if (intent.getBooleanExtra(NP_CASCADE, false)) {
            npCascadePending = true
            npCascade()
        }
        NpDiag.mark("retour onglet $tab")
    }

    /** Les souvenirs n'existent que dans la Galerie (photos) et son filtre Vidéos. */
    fun npJalonsActive() = npForceShowAll && NpNav.souvenirsTab(npTab) && !(mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent)

    private fun npShowJalons() = npJalonsActive() && NpSettings.showJalons(this)

    /** Mode « Souvenirs » de la maquette : fond noir, photos libres serrées, souvenirs en cartes blanches. */
    fun npSouvenirLayout() = npShowJalons() && NpSettings.style(this) == NpSettings.STYLE_CARDS

    /** Photo de début choisie (double tap), en attente de la photo de fin. */
    var npPendingStart: Medium?
        get() = NpJalons.pendingStart
        private set(value) {
            NpJalons.pendingStart = value
        }

    fun npSpanSize(position: Int, spanCount: Int): Int {
        val adapter = getMediaAdapter()
        if (adapter?.isASectionTitle(position) == true) return spanCount
        if (!npSouvenirLayout()) return 1
        val code = NpJalons.lastLayout.getOrElse(position) { 0 }
        return if (code >= 10) 2 else 1
    }

    fun npGridColumns() = if (npSouvenirLayout()) 6 else config.mediaColumnCnt

    /** En-tête : logo Aurora, filtre vidéo (icône photo + vidéo quand il est actif), menu. */
    private fun npPaintHeader() {
        val header = binding.npTopButtons
        val gallery = npForceShowAll && org.fossify.gallery.helpers.NpGallery.isGalleryTab(npTab)
        header.npVideoFilter.beVisibleIf(gallery)
        val videosOnly = npTab == NP_TAB_VIDEOS || (npTab == NP_TAB_ALL && npOnlyVideos)
        header.npVideoFilter.setImageResource(if (videosOnly) R.drawable.ic_np_photo_video else R.drawable.ic_np_videos)
        header.npVideoFilter.contentDescription = getString(if (videosOnly) R.string.np_photos_videos else R.string.np_videos_only)
        header.npLogoText.setTextColor(if (npSouvenirLayout()) Color.WHITE else 0xFF3A3330.toInt())
        if (!gallery) {
            header.npLogoText.text = binding.mediaMenu.requireToolbar().title?.takeIf { it.isNotEmpty() } ?: mPath.substringAfterLast('/')
        } else {
            header.npLogoText.setText(R.string.np_brand)
        }
    }

    private fun npToggleVideoFilter() {
        binding.mediaGrid.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        when (npTab) {
            NP_TAB_GALLERY -> npSwitchInPlace(NP_TAB_VIDEOS)
            NP_TAB_VIDEOS -> npSwitchInPlace(NP_TAB_GALLERY)
            NP_TAB_ALL -> {
                npOnlyVideos = !npOnlyVideos
                npPaintHeader()
                npRefreshJalons()
                binding.mediaGrid.scrollToPosition(0)
            }
        }
        val v = binding.npTopButtons.npVideoFilter
        v.animate().rotationY(90f).setDuration(110).withEndAction {
            npPaintHeader()
            v.rotationY = -90f
            v.animate().rotationY(0f).setDuration(140).start()
        }.start()
    }

    private fun npSetupNav(animateBubble: Boolean = false) {
        npPaintHeader()
        NpNav.setup(
            activity = this,
            current = npTab,
            animateBubble = animateBubble,
            onReselect = { binding.mediaGrid.smoothScrollToPosition(0) },
            onSelect = { tab -> npSwitchInPlace(tab) },
            onToggleJalons = {
                npFocusCut = null
                npApplyGridStyle()
                npRefreshJalons()
            }
        )
    }

    /** Galerie ↔ Vidéos ↔ Toutes sans changer d'écran : les vignettes glissent vers leur nouvelle place. */
    private fun npSwitchInPlace(tab: String): Boolean {
        if (!npForceShowAll || !org.fossify.gallery.helpers.NpGallery.isGalleryTab(npTab) || !org.fossify.gallery.helpers.NpGallery.isGalleryTab(tab)) return false
        val wasSouvenir = npSouvenirLayout()
        val needsReload = (npTab == NP_TAB_ALL) != (tab == NP_TAB_ALL)
        npTab = tab
        npOnlyVideos = tab == NP_TAB_VIDEOS
        npFocusCut = null
        npCancelPending()
        if (wasSouvenir != npSouvenirLayout()) setupLayoutManager()
        npSetupNav(animateBubble = true)
        npApplyGridStyle()
        org.fossify.gallery.helpers.NpScrollUi.resetBrand(binding.npTopButtons.npBrand)
        if (needsReload) {
            val cached = npCache[npScope()]
            if (cached != null) {
                // liste déjà prête (préchargée en arrière-plan) : bascule immédiate
                NpDiag.mark("bascule instantanée ${npScope()} ${cached.size}")
                npRawMedia = cached
                mMedia = npDecorate(cached)
                getMediaAdapter()?.let {
                    it.media = mMedia.clone() as ArrayList<ThumbnailItem>
                    it.notifyDataSetChanged()
                }
                binding.mediaGrid.invalidateItemDecorations()
                npCascadePending = true
                npCascade()
                // rafraîchissement discret seulement si la liste date un peu
                if (System.currentTimeMillis() - (npCacheTime[npScope()] ?: 0L) > 60_000L) {
                    mIsGettingMedia = false
                    startAsyncTask()
                }
            } else {
                npCascadePending = true
                mLoadedInitialPhotos = false
                mIsGettingMedia = false
                getMedia()
            }
        } else {
            npRefreshJalons()
        }
        binding.mediaGrid.scrollToPosition(0)
        return true
    }

    /** L'espacement de grille de Fossify, quelle que soit sa position parmi les décorations. */
    private fun npGridDecoration(): GridSpacingItemDecoration? {
        for (i in 0 until binding.mediaGrid.itemDecorationCount) {
            (binding.mediaGrid.getItemDecorationAt(i) as? GridSpacingItemDecoration)?.let { return it }
        }
        return null
    }

    private fun npSetupGrid() {
        binding.mediaGrid.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
            moveDuration = 420
            addDuration = 260
            removeDuration = 180
            changeDuration = 180
        }
        binding.mediaGrid.addItemDecoration(
            NpJalonDecoration(
                styleOf = { NpSettings.style(this) },
                enabled = { npShowJalons() },
                background = { getProperBackgroundColor() }
            )
        )
        npApplyGridStyle()
        org.fossify.gallery.helpers.NpScrollUi.attach(
            binding.mediaGrid,
            binding.npTopButtons.npBrand,
            binding.npDatePill
        ) { position -> npDateAt(position) }
    }

    /** Date de la première photo visible à partir de cette position (pour la pastille de défilement). */
    private fun npDateAt(position: Int): Long? {
        val media = getMediaAdapter()?.media ?: return null
        for (i in position until minOf(media.size, position + 40)) {
            val m = media[i] as? Medium ?: continue
            return if (m.taken > 0) m.taken else m.modified
        }
        return null
    }

    /** Listes brutes déjà chargées, par portée (« gallery » ou « all ») : la bascule Galerie ↔ Toutes est immédiate. */
    private val npCache = java.util.concurrent.ConcurrentHashMap<String, ArrayList<ThumbnailItem>>()
    private val npCacheTime = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var npPrefetching = false

    private fun npScope() = if (npFoldersFilter() == null) "all" else "gallery"

    /** Une fois la Galerie affichée, prépare « Toutes » en arrière-plan (et inversement). */
    private fun npPrefetchOther() {
        if (!npForceShowAll || npPrefetching) return
        val other = if (npScope() == "all") "gallery" else "all"
        if (npCache[other] != null) return
        npPrefetching = true
        val folders = if (other == "all") null else org.fossify.gallery.helpers.NpGallery.folders(this)
        NpDiag.mark("préchargement $other")
        getCachedMedia(mPath, false, false, folders) { cached ->
            if (cached.isNotEmpty() && npCache[other] == null) {
                npCache[other] = cached
                NpDiag.mark("préchargement $other cache ${cached.size}")
            }
            runOnUiThread {
                GetMediaAsynctask(
                    context = applicationContext,
                    mPath = mPath,
                    isPickImage = false,
                    isPickVideo = false,
                    showAll = mShowAll,
                    onlyFolders = folders
                ) { scanned ->
                    npCache[other] = scanned
                    npCacheTime[other] = System.currentTimeMillis()
                    npPrefetching = false
                    NpDiag.mark("préchargement $other scan ${scanned.size}")
                }.execute()
            }
        }
    }

    /** Marges, fond et colonnes de la grille selon l'affichage des souvenirs. */
    private fun npApplyGridStyle() {
        val density = resources.displayMetrics.density
        val souvenir = npSouvenirLayout()
        val (start, end) = when {
            !npShowJalons() || souvenir -> 0 to 0
            else -> (36 * density).toInt() to (6 * density).toInt()
        }
        binding.mediaGrid.setPaddingRelative(start, binding.mediaGrid.paddingTop, end, binding.mediaGrid.paddingBottom)

        // fond noir en mode Souvenirs : on voit tout de suite que les souvenirs sont affichés
        val bg = if (souvenir) Color.BLACK else getProperBackgroundColor()
        binding.mediaCoordinator.setBackgroundColor(bg)
        window.decorView.setBackgroundColor(bg)
        binding.npStatusScrim.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(androidx.core.graphics.ColorUtils.setAlphaComponent(bg, 0xE6), 0)
        )
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            androidx.core.graphics.ColorUtils.calculateLuminance(bg) > 0.5
        getMediaAdapter()?.updateTextColor(if (souvenir) Color.WHITE else getProperTextColor())
        npPaintHeader()

        // l'espacement de Fossify suppose une seule taille de case : on le retire en mode Souvenirs
        if (souvenir) {
            npGridDecoration()?.let { binding.mediaGrid.removeItemDecoration(it) }
        } else {
            handleGridSpacing()
        }
        (binding.mediaGrid.layoutManager as? GridLayoutManager)?.let {
            if (it.spanCount != npGridColumns()) it.spanCount = npGridColumns()
        }
        binding.mediaGrid.invalidateItemDecorations()
    }

    private fun npDecorate(list: ArrayList<ThumbnailItem>): ArrayList<ThumbnailItem> {
        var items: List<ThumbnailItem> = list
        if (npForceShowAll && (npTab == NP_TAB_GALLERY || npTab == NP_TAB_VIDEOS)) {
            val kept = org.fossify.gallery.helpers.NpGallery.filter(this, npTab, items.filterIsInstance<Medium>()).map { it.path }.toHashSet()
            items = NpJalons.dropEmptySections(items.filter { it !is Medium || kept.contains(it.path) })
        } else if (npOnlyVideos) {
            items = NpJalons.dropEmptySections(items.filter { it !is Medium || it.isVideo() })
        }
        return if (npSouvenirLayout()) {
            NpJalons.decorateCards(this, items, npFocusCut, getString(R.string.np_unsorted))
        } else if (npShowJalons()) {
            NpJalons.decorate(this, items, npFocusCut)
        } else {
            NpJalons.plain(items)
        }
    }

    /** Double tap : un nouveau souvenir commence à cette photo ; les suivantes le rejoignent. */
    fun npCutAt(medium: Medium) {
        if (!npJalonsActive()) return
        if (!NpJalons.addStart(this, medium)) {
            toast(R.string.np_souvenir_exists)
            return
        }
        binding.mediaGrid.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        NpDiag.mark("nouveau souvenir")
        if (!NpSettings.showJalons(this)) {
            NpSettings.setShowJalons(this, true)
            npSetupNav()
            npApplyGridStyle()
        }
        npRefreshJalons()
    }

    /** Toucher une photo pendant qu'un début est choisi : c'est la fin du souvenir. */
    fun npFinishSouvenir(end: Medium) {
        val start = npPendingStart ?: return
        when (NpJalons.add(this, start, end)) {
            NpJalons.AddResult.SAME -> {
                npCancelPending()
                return
            }

            NpJalons.AddResult.OVERLAP -> {
                toast(R.string.np_souvenir_overlap)
                return
            }

            NpJalons.AddResult.OK -> {
                binding.mediaGrid.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                npPendingStart = null
                npHidePendingBar()
                npRefreshJalons()
                getMediaAdapter()?.notifyDataSetChanged()
            }
        }
    }

    fun npCancelPending() {
        if (npPendingStart == null) return
        npPendingStart = null
        npHidePendingBar()
        getMediaAdapter()?.notifyDataSetChanged()
    }

    private fun npShowPendingBar() {
        val start = npPendingStart ?: return
        val bar = binding.npSouvenirBar
        bar.npSouvenirBarText.text = getString(R.string.np_souvenir_pending, NpJalons.timeLabel(NpJalons.timeOf(start)))
        bar.npSouvenirBarCancel.setOnClickListener { npCancelPending() }
        bar.root.alpha = 0f
        bar.root.translationY = 24 * resources.displayMetrics.density
        bar.root.beVisible()
        bar.root.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun npHidePendingBar() {
        val bar = binding.npSouvenirBar.root
        bar.animate().alpha(0f).translationY(24 * resources.displayMetrics.density).setDuration(180).withEndAction { bar.beGone() }.start()
    }

    fun npEditJalon(cutPath: String) {
        NpJalonDialog.show(this, cutPath, allowRemove = false) { npRefreshJalons() }
    }

    fun npRemoveJalon(cutPath: String) {
        NpJalons.remove(this, cutPath)
        if (npFocusCut == cutPath) npFocusCut = null
        binding.mediaGrid.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        toast(R.string.np_jalon_removed)
        npRefreshJalons()
    }

    /** Chevron d'un jalon : n'afficher que ses photos (ou revenir à toute la galerie). */
    fun npToggleFocus(cutPath: String) {
        npFocusCut = if (npFocusCut == cutPath) null else cutPath
        val target = cutPath
        npRefreshJalons()
        binding.mediaGrid.post {
            val index = mMedia.indexOfFirst { it is ThumbnailSection && NpJalons.infoOf(it.title)?.cutPath == target }
            if (npFocusCut != null || index < 0) {
                binding.mediaGrid.smoothScrollToPosition(0)
            } else {
                (binding.mediaGrid.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(index, 0)
            }
        }
    }

    private fun npShowBurger(anchor: View) {
        val style = NpSettings.style(this)
        val extras = arrayListOf(
            NpBurger.Extra(getString(R.string.np_diag)) { NpDiag.share(this) },
            NpBurger.Extra(NpNav.layoutLabel(this)) { NpNav.pickLayout(this) { npSetupNav() } },
            NpBurger.Extra(
                getString(R.string.np_jalon_style) + " : " +
                    getString(if (style == NpSettings.STYLE_CARDS) R.string.np_jalon_style_cards else R.string.np_jalon_style_timeline)
            ) { npPickStyle() }
        )
        refreshMenuItems()
        NpBurger.show(this, anchor, binding.mediaMenu.requireToolbar().menu, extras)
    }

    private fun npPickStyle() {
        val labels = arrayOf(getString(R.string.np_jalon_style_timeline), getString(R.string.np_jalon_style_cards))
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.np_jalon_style)
            .setSingleChoiceItems(labels, NpSettings.style(this)) { dialog, which ->
                NpSettings.setStyle(this, which)
                dialog.dismiss()
                npApplyGridStyle()
                getMediaAdapter()?.notifyDataSetChanged()
            }
            .show()
    }

    private fun npKey(item: ThumbnailItem) = when (item) {
        is Medium -> "m:" + item.path
        is ThumbnailSection -> "s:" + NpJalons.keyOf(item.title)
        else -> "x:" + item.hashCode()
    }

    /** Recalcule la liste affichée ; les photos glissent vers leur nouvelle place. */
    private fun npRefreshJalons() {
        val adapter = getMediaAdapter() ?: return
        val oldList = adapter.media
        val newList = npDecorate(npRawMedia.clone() as ArrayList<ThumbnailItem>)
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = npKey(oldList[oldPos]) == npKey(newList[newPos])
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = oldList[oldPos] == newList[newPos]
        })
        mMedia = newList
        adapter.media = newList.clone() as ArrayList<ThumbnailItem>
        diff.dispatchUpdatesTo(adapter)
        binding.mediaGrid.invalidateItemDecorations()
    }

    /** Apparition de la grille en cascade, ligne par ligne. */
    private fun npCascade() {
        if (!npCascadePending) return
        npCascadePending = false
        val grid = binding.mediaGrid
        grid.post {
            val density = resources.displayMetrics.density
            var row = -1
            var lastTop = Int.MIN_VALUE
            for (i in 0 until grid.childCount) {
                val child = grid.getChildAt(i)
                if (child.top != lastTop) {
                    row++
                    lastTop = child.top
                }
                val col = if (child.width > 0) (child.left / child.width.coerceAtLeast(1)) else 0
                child.alpha = 0f
                child.translationY = 22 * density
                child.scaleX = 0.96f
                child.scaleY = 0.96f
                child.animate()
                    .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setStartDelay((row * 45L + col * 12L).coerceAtMost(600L))
                    .setDuration(320)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
                    .start()
            }
        }
    }

    private fun npScrollToTarget() {
        val target = npScrollTo ?: return
        val index = mMedia.indexOfFirst { it is Medium && it.path == target }
        if (index < 0) return
        npScrollTo = null
        val position = (index - 1).coerceAtLeast(0)
        (binding.mediaGrid.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(position, 0)
    }

    private fun setupAdapter() {
        if (!mShowAll && isDirEmpty()) {
            return
        }

        val currAdapter = binding.mediaGrid.adapter
        if (currAdapter == null) {
            initZoomListener()
            MediaAdapter(
                activity = this,
                media = mMedia.clone() as ArrayList<ThumbnailItem>,
                listener = this,
                isAGetIntent = mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent,
                allowMultiplePicks = mAllowPickingMultiple,
                path = mPath,
                recyclerView = binding.mediaGrid
            ) {
                if (it is Medium && !isFinishing) {
                    itemClicked(it.path)
                }
            }.apply {
                setupZoomListener(mZoomListener)
                binding.mediaGrid.adapter = this
            }

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            if (viewType == VIEW_TYPE_LIST && areSystemAnimationsEnabled) {
                binding.mediaGrid.scheduleLayoutAnimation()
            }

            setupLayoutManager()
            handleGridSpacing()
        } else if (mLastSearchedText.isEmpty()) {
            (currAdapter as MediaAdapter).updateMedia(mMedia)
            handleGridSpacing()
        } else {
            searchQueryChanged(mLastSearchedText)
        }

        setupScrollDirection()
    }

    private fun setupScrollDirection() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        val scrollHorizontally = config.scrollHorizontally && viewType == VIEW_TYPE_GRID
        binding.mediaFastscroller.setScrollVertically(!scrollHorizontally)
    }

    private fun checkLastMediaChanged() {
        if (isDestroyed || config.getFolderSorting(mPath) and SORT_BY_RANDOM != 0) {
            return
        }

        mLastMediaHandler.removeCallbacksAndMessages(null)
        mLastMediaHandler.postDelayed({
            ensureBackgroundThread {
                val mediaId = getLatestMediaId()
                val mediaDateId = getLatestMediaByDateId()
                if (mLatestMediaId != mediaId || mLatestMediaDateId != mediaDateId) {
                    mLatestMediaId = mediaId
                    mLatestMediaDateId = mediaDateId
                    runOnUiThread {
                        getMedia()
                    }
                } else {
                    checkLastMediaChanged()
                }
            }
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, false, true, mPath) {
            mLoadedInitialPhotos = false
            binding.mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this) {
            mLoadedInitialPhotos = false
            binding.mediaRefreshLayout.isRefreshing = true
            binding.mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun emptyRecycleBin() {
        showRecycleBinEmptyingDialog {
            emptyTheRecycleBin {
                finish()
            }
        }
    }

    private fun emptyAndDisableRecycleBin() {
        showRecycleBinEmptyingDialog {
            emptyAndDisableTheRecycleBin {
                finish()
            }
        }
    }

    private fun restoreAllFiles() {
        val paths = mMedia.filter { it is Medium }.map { (it as Medium).path } as ArrayList<String>
        showRestoreConfirmationDialog(paths.size) {
            restoreRecycleBinPaths(paths) {
                ensureBackgroundThread {
                    directoryDB.deleteDirPath(RECYCLE_BIN)
                }
                finish()
            }
        }
    }

    private fun toggleFilenameVisibility() {
        config.displayFileNames = !config.displayFileNames
        getMediaAdapter()?.updateDisplayFilenames(config.displayFileNames)
    }

    private fun switchToFolderView() {
        hideKeyboard()
        config.showAll = false
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this, false, mPath) {
            refreshMenuItems()
            setupLayoutManager()
            binding.mediaGrid.adapter = null
            setupAdapter()
        }
    }

    private fun showGroupByDialog() {
        ChangeGroupingDialog(this, mPath) {
            mLoadedInitialPhotos = false
            binding.mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun deleteDirectoryIfEmpty() {
        if (config.deleteEmptyFolders) {
            val fileDirItem = FileDirItem(mPath, mPath.getFilenameFromPath(), true)
            if (!fileDirItem.isDownloadsFolder() && fileDirItem.isDirectory) {
                ensureBackgroundThread {
                    if (fileDirItem.getProperFileCount(this, true) == 0) {
                        tryDeleteFileDirItem(fileDirItem, true, true)
                    }
                }
            }
        }
    }

    private fun getMedia() {
        if (mIsGettingMedia) {
            return
        }

        mIsGettingMedia = true
        if (mLoadedInitialPhotos) {
            startAsyncTask()
        } else {
            NpDiag.mark("cache:start ${npTab}")
            getCachedMedia(
                mPath,
                mIsGetVideoIntent && !mIsGetImageIntent,
                mIsGetImageIntent && !mIsGetVideoIntent,
                npFoldersFilter()
            ) {
                NpDiag.mark("cache:done ${it.size}")
                if (it.isEmpty()) {
                    runOnUiThread {
                        binding.mediaRefreshLayout.isRefreshing = true
                    }
                } else {
                    gotMedia(it, true)
                }
                startAsyncTask()
            }
        }

        mLoadedInitialPhotos = true
    }

    /** Dossiers à charger : ceux de la Galerie (appareil photo + dossiers ajoutés), ou tous pour « Toutes ». */
    private fun npFoldersFilter(): List<String>? =
        if (npForceShowAll && (npTab == NP_TAB_GALLERY || npTab == NP_TAB_VIDEOS)) org.fossify.gallery.helpers.NpGallery.folders(this) else null

    private fun startAsyncTask() {
        NpDiag.mark("scan:start ${npTab}")
        val scope = npScope()
        mCurrAsyncTask?.stopFetching()
        mCurrAsyncTask = GetMediaAsynctask(
            context = applicationContext,
            mPath = mPath,
            isPickImage = mIsGetImageIntent && !mIsGetVideoIntent,
            isPickVideo = mIsGetVideoIntent && !mIsGetImageIntent,
            showAll = mShowAll,
            onlyFolders = npFoldersFilter()
        ) {
            NpDiag.mark("scan:done ${it.size}")
            ensureBackgroundThread {
                if (scope != npScope()) {
                    // l'onglet a changé pendant le scan : on garde la liste pour plus tard sans l'afficher
                    npCache[scope] = it
                    npCacheTime[scope] = System.currentTimeMillis()
                    mIsGettingMedia = false
                    return@ensureBackgroundThread
                }
                val oldMedia = (npCache[scope] ?: mMedia).clone() as ArrayList<ThumbnailItem>
                val newMedia = it
                try {
                    gotMedia(newMedia, false)
                    npCacheTime[scope] = System.currentTimeMillis()
                    runOnUiThread { npPrefetchOther() }

                    // remove cached files that are no longer valid for whatever reason
                    val newPaths = newMedia.mapNotNull { it as? Medium }.map { it.path }.toHashSet()
                    oldMedia
                        .mapNotNull { it as? Medium }
                        .filter { !newPaths.contains(it.path) }
                        .forEach {
                            if (mPath == FAVORITES && getDoesFilePathExist(it.path)) {
                                favoritesDB.deleteFavoritePath(it.path)
                                mediaDB.updateFavorite(it.path, false)
                            } else {
                                mediaDB.deleteMediumPath(it.path)
                            }
                        }
                } catch (e: Exception) {
                }
            }
        }

        mCurrAsyncTask!!.execute()
    }

    private fun isDirEmpty(): Boolean {
        return if (mMedia.isEmpty() && config.filterMedia > 0) {
            if (mPath != FAVORITES && mPath != RECYCLE_BIN) {
                deleteDirectoryIfEmpty()
                deleteDBDirectory()
            }

            if (mPath == FAVORITES) {
                ensureBackgroundThread {
                    directoryDB.deleteDirPath(FAVORITES)
                }
            }

            if (mPath == RECYCLE_BIN) {
                binding.mediaEmptyTextPlaceholder.setText(org.fossify.commons.R.string.no_items_found)
                binding.mediaEmptyTextPlaceholder.beVisible()
                binding.mediaEmptyTextPlaceholder2.beGone()
            } else {
                finish()
            }

            true
        } else {
            false
        }
    }

    private fun deleteDBDirectory() {
        ensureBackgroundThread {
            try {
                directoryDB.deleteDirPath(mPath)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun createNewFolder() {
        CreateNewFolderDialog(this, mPath) {
            config.tempFolderPath = it
        }
    }

    private fun tryToggleTemporarilyShowHidden() {
        if (config.temporarilyShowHidden) {
            toggleTemporarilyShowHidden(false)
        } else {
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(this)
            } else {
                handleHiddenFolderPasswordProtection {
                    toggleTemporarilyShowHidden(true)
                }
            }
        }
    }

    private fun toggleTemporarilyShowHidden(show: Boolean) {
        mLoadedInitialPhotos = false
        config.temporarilyShowHidden = show
        getMedia()
        refreshMenuItems()
    }

    private fun setupLayoutManager() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            binding.mediaRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            binding.mediaRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        layoutManager.spanCount = npGridColumns()
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = npSpanSize(position, layoutManager.spanCount)
        }
    }

    private fun setupListLayoutManager() {
        val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
        binding.mediaRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        mZoomListener = null
    }

    private fun handleGridSpacing(media: ArrayList<ThumbnailItem> = mMedia) {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (npSouvenirLayout()) {
            npGridDecoration()?.let { binding.mediaGrid.removeItemDecoration(it) }
            return
        }
        if (viewType == VIEW_TYPE_GRID) {
            val spanCount = config.mediaColumnCnt
            val spacing = config.thumbnailSpacing
            val useGridPosition = media.firstOrNull() is ThumbnailSection

            val currentGridDecoration = npGridDecoration()
            currentGridDecoration?.items = media

            val newGridDecoration = GridSpacingItemDecoration(
                spanCount = spanCount,
                spacing = spacing,
                isScrollingHorizontally = config.scrollHorizontally,
                addSideSpacing = config.fileRoundedCorners,
                items = media,
                useGridPosition = useGridPosition
            )
            if (currentGridDecoration.toString() != newGridDecoration.toString()) {
                if (currentGridDecoration != null) {
                    binding.mediaGrid.removeItemDecoration(currentGridDecoration)
                }
                binding.mediaGrid.addItemDecoration(newGridDecoration)
            }
        }
    }

    private fun initZoomListener() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
            mZoomListener = object : MyRecyclerView.MyZoomListener {
                override fun zoomIn() {
                    if (layoutManager.spanCount > 1) {
                        reduceColumnCount()
                        getMediaAdapter()?.finishActMode()
                    }
                }

                override fun zoomOut() {
                    if (layoutManager.spanCount < MAX_COLUMN_COUNT) {
                        increaseColumnCount()
                        getMediaAdapter()?.finishActMode()
                    }
                }
            }
        } else {
            mZoomListener = null
        }
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..MAX_COLUMN_COUNT) {
            items.add(
                RadioItem(
                    id = i,
                    title = resources.getQuantityString(
                        org.fossify.commons.R.plurals.column_counts, i, i
                    )
                )
            )
        }

        val currentColumnCount = (binding.mediaGrid.layoutManager as MyGridLayoutManager).spanCount
        RadioGroupDialog(this, items, currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.mediaColumnCnt = newColumnCount
                columnCountChanged()
            }
        }
    }

    private fun increaseColumnCount() {
        config.mediaColumnCnt += 1
        columnCountChanged()
    }

    private fun reduceColumnCount() {
        config.mediaColumnCnt -= 1
        columnCountChanged()
    }

    private fun columnCountChanged() {
        (binding.mediaGrid.layoutManager as MyGridLayoutManager).spanCount = config.mediaColumnCnt
        handleGridSpacing()
        refreshMenuItems()
        getMediaAdapter()?.apply {
            notifyItemRangeChanged(0, media.size)
        }
    }

    private fun isSetWallpaperIntent() = intent.getBooleanExtra(SET_WALLPAPER_INTENT, false)

    private fun itemClicked(path: String) {
        hideKeyboard()
        if (isSetWallpaperIntent()) {
            toast(R.string.setting_wallpaper)

            val wantedWidth = wallpaperDesiredMinimumWidth
            val wantedHeight = wallpaperDesiredMinimumHeight
            val ratio = wantedWidth.toFloat() / wantedHeight

            val options = RequestOptions()
                .override((wantedWidth * ratio).toInt(), wantedHeight)
                .fitCenter()

            Glide.with(this)
                .asBitmap()
                .load(File(path))
                .apply(options)
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        try {
                            WallpaperManager.getInstance(applicationContext).setBitmap(resource)
                            setResult(RESULT_OK)
                        } catch (ignored: IOException) {
                        }

                        finish()
                    }
                })
        } else if (mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent) {
            Intent().apply {
                data = path.toUri()
                setResult(RESULT_OK, this)
            }
            finish()
        } else {
            mWasFullscreenViewOpen = true
            if (!path.isVideoFast()) {
                openInViewPager(path)
                return
            }

            when (config.videoPlayerType) {
                VIDEO_PLAYER_SYSTEM -> openSystemDefaultPlayer(path)
                VIDEO_PLAYER_APP -> if (config.gestureVideoPlayer) launchGesturePlayer(path) else openInViewPager(path)
                else -> openInViewPager(path) // unreachable by design
            }
        }
    }

    private fun openInViewPager(path: String) {
        Intent(this, ViewPagerActivity::class.java).apply {
            putExtra(SKIP_AUTHENTICATION, shouldSkipAuthentication())
            putExtra(PATH, path)
            putExtra(SHOW_ALL, mShowAll)
            putExtra(SHOW_FAVORITES, mPath == FAVORITES)
            putExtra(SHOW_RECYCLE_BIN, mPath == RECYCLE_BIN)
            putExtra(IS_FROM_GALLERY, true)
            if (npForceShowAll) putExtra(NP_TAB, npTab)
            startActivity(this)
        }
    }

    private fun openSystemDefaultPlayer(path: String) {
        openPath(
            path = path,
            forceChooser = false,
            extras = hashMapOf(SHOW_FAVORITES to (mPath == FAVORITES)).apply {
                if (path.startsWith(recycleBinPath)) put(IS_IN_RECYCLE_BIN, true)
                if (shouldSkipAuthentication()) put(SKIP_AUTHENTICATION, true)
            }
        )
    }

    private fun gotMedia(media: ArrayList<ThumbnailItem>, isFromCache: Boolean) {
        mIsGettingMedia = false
        checkLastMediaChanged()
        npRawMedia = media
        npCache[npScope()] = media
        mMedia = npDecorate(media)

        runOnUiThread {
            binding.loadingIndicator.hide()
            binding.mediaRefreshLayout.isRefreshing = false
            binding.mediaEmptyTextPlaceholder.beVisibleIf(media.isEmpty() && !isFromCache)
            binding.mediaEmptyTextPlaceholder2.beVisibleIf(media.isEmpty() && !isFromCache)

            if (binding.mediaEmptyTextPlaceholder.isVisible()) {
                binding.mediaEmptyTextPlaceholder.text = getString(R.string.no_media_with_filters)
            }
            binding.mediaFastscroller.beVisibleIf(binding.mediaEmptyTextPlaceholder.isGone())
            setupAdapter()
            npScrollToTarget()
            npCascade()
        }

        mLatestMediaId = getLatestMediaId()
        mLatestMediaDateId = getLatestMediaByDateId()
        if (!isFromCache) {
            val mediaToInsert = mMedia
                .filter { it is Medium && it.deletedTS == 0L }.map { it as Medium }
            Thread {
                try {
                    mediaDB.insertAll(mediaToInsert)
                } catch (e: Exception) {
                }
            }.start()
        }
    }

    override fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>, skipRecycleBin: Boolean) {
        val filtered = fileDirItems
            .filter { !getIsPathDirectory(it.path) && it.path.isMediaFile() } as ArrayList
        if (filtered.isEmpty()) {
            return
        }

        if (
            config.useRecycleBin
            && !skipRecycleBin
            && !filtered.first().path.startsWith(recycleBinPath)
        ) {
            val movingItems = resources.getQuantityString(
                org.fossify.commons.R.plurals.moving_items_into_bin,
                filtered.size,
                filtered.size
            )
            toast(movingItems)

            movePathsInRecycleBin(filtered.map { it.path } as ArrayList<String>) {
                if (it) {
                    deleteFilteredFiles(filtered)
                } else {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
            }
        } else {
            val deletingItems = resources.getQuantityString(
                org.fossify.commons.R.plurals.deleting_items,
                filtered.size,
                filtered.size
            )
            toast(deletingItems)
            deleteFilteredFiles(filtered)
        }
    }

    private fun shouldSkipAuthentication(): Boolean {
        return intent.getBooleanExtra(SKIP_AUTHENTICATION, false)
    }

    private fun deleteFilteredFiles(filtered: ArrayList<FileDirItem>) {
        deleteFiles(filtered) {
            if (!it) {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                return@deleteFiles
            }

            mMedia.removeAll { filtered.map { it.path }.contains((it as? Medium)?.path) }

            ensureBackgroundThread {
                val useRecycleBin = config.useRecycleBin
                filtered.forEach {
                    if (it.path.startsWith(recycleBinPath) || !useRecycleBin) {
                        deleteDBPath(it.path)
                    }
                }
            }

            if (mMedia.isEmpty()) {
                deleteDirectoryIfEmpty()
                deleteDBDirectory()
                finish()
            }
        }
    }

    override fun refreshItems() {
        getMedia()
    }

    override fun selectedPaths(paths: ArrayList<String>) {
        Intent().apply {
            putExtra(PICKED_PATHS, paths)
            setResult(RESULT_OK, this)
        }
        finish()
    }

    override fun updateMediaGridDecoration(media: ArrayList<ThumbnailItem>) {
        var currentGridPosition = 0
        media.forEach {
            if (it is Medium) {
                it.gridPosition = currentGridPosition++
            } else if (it is ThumbnailSection) {
                currentGridPosition = 0
            }
        }

        npGridDecoration()?.items = media
    }

    private fun setAsDefaultFolder() {
        config.defaultFolder = mPath
        refreshMenuItems()
    }

    private fun unsetAsDefaultFolder() {
        config.defaultFolder = ""
        refreshMenuItems()
    }
}
