package com.elkhouly.commons

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.elkhouly.commons.extensions.appLockManager
import com.elkhouly.commons.extensions.checkUseEnglish

open class EasyApp : Application() {

    open val isAppLockFeatureAvailable = false

    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
        setupAppLockManager()
    }

    private fun setupAppLockManager() {
        if (isAppLockFeatureAvailable) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(appLockManager)
        }
    }
}
