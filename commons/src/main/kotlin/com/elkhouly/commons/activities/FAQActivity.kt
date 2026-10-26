package com.elkhouly.commons.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import kotlinx.collections.immutable.toImmutableList
import com.elkhouly.commons.compose.extensions.enableEdgeToEdgeSimple
import com.elkhouly.commons.compose.screens.FAQScreen
import com.elkhouly.commons.compose.theme.AppThemeSurface
import com.elkhouly.commons.helpers.APP_FAQ
import com.elkhouly.commons.models.FAQItem

class FAQActivity : BaseComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeSimple()
        setContent {
            AppThemeSurface {
                val faqItems = remember { intent.getSerializableExtra(APP_FAQ) as ArrayList<FAQItem> }
                FAQScreen(
                    goBack = ::finish,
                    faqItems = faqItems.toImmutableList()
                )
            }
        }
    }
}
