package com.elkhouly.contacts.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elkhouly.commons.helpers.ensureBackgroundThread
import com.elkhouly.contacts.extensions.checkAndBackupContactsOnBoot

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ensureBackgroundThread {
            context.apply {
                checkAndBackupContactsOnBoot()
            }
        }
    }
}
