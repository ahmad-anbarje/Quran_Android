package com.readqurantoday.quran

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts

/** Downloads: per reciter, what is kept for offline listening, and copies saved to the phone. */
class DownloadsActivity : CardsActivity(R.string.set_downloads) {

    /* What to run once the storage permission comes back granted: a save that asked. */
    private var afterGrant: (() -> Unit)? = null

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val next = afterGrant
        afterGrant = null
        if (granted) next?.invoke() else notice(getString(R.string.dl_save_denied))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Surahs.load(this)
        Recite.load(this)

        DownloadsPane(this, cards, ::whenMaySave).build()
        sayBars()
    }

    // Below Android 10 writing to Download needs the storage permission, asked on first save
    private fun whenMaySave(save: () -> Unit) {
        val needed = saveNeedsPermission &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (!needed) return save()
        afterGrant = save
        permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}
