package com.elkhouly.dialer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elkhouly.dialer.activities.CallActivity
import com.elkhouly.dialer.helpers.ACCEPT_CALL
import com.elkhouly.dialer.helpers.CallManager
import com.elkhouly.dialer.helpers.CallNotificationManager
import com.elkhouly.dialer.helpers.DECLINE_CALL

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACCEPT_CALL -> {
                context.startActivity(CallActivity.getStartIntent(context))
                CallManager.accept()
            }

            DECLINE_CALL -> {
                CallManager.reject()
                // nothing left to hang up, the notification is stale
                if (CallManager.getPrimaryCall() == null) {
                    CallNotificationManager(context).cancelNotification()
                }
            }
        }
    }
}
