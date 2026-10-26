package com.elkhouly.commons.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elkhouly.commons.extensions.syncGlobalConfig
import com.elkhouly.commons.helpers.MyContentProvider

class EasyBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == MyContentProvider.ACTION_GLOBAL_CONFIG_UPDATED) {
            context?.let {
                val pendingResult = goAsync()
                it.syncGlobalConfig { pendingResult.finish() }
            }
        }
    }
}
