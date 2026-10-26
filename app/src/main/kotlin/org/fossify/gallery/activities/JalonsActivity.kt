package org.fossify.gallery.activities

import android.os.Bundle
import org.fossify.commons.extensions.viewBinding
import org.fossify.gallery.databinding.ActivityJalonsBinding
import org.fossify.gallery.helpers.NP_TAB_GALLERY
import org.fossify.gallery.helpers.NP_TAB_JALONS
import org.fossify.gallery.helpers.NpNav

class JalonsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityJalonsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(
            padTopSystem = listOf(binding.jalonsList),
            padBottomSystem = listOf(binding.jalonsList),
            moveBottomSystem = listOf(binding.npNav.root)
        )
        NpNav.setup(this, NP_TAB_JALONS)
    }

    override fun onBackPressedCompat(): Boolean {
        NpNav.go(this, NP_TAB_GALLERY)
        return true
    }
}
