package com.elkhouly.commons.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_SENDTO
import android.content.Intent.EXTRA_EMAIL
import android.content.Intent.EXTRA_SUBJECT
import android.content.Intent.EXTRA_TEXT
import android.content.Intent.createChooser
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.elkhouly.commons.R
import com.elkhouly.commons.compose.alert_dialog.rememberAlertDialogState
import com.elkhouly.commons.compose.extensions.enableEdgeToEdgeSimple
import com.elkhouly.commons.compose.screens.AboutScreen
import com.elkhouly.commons.compose.screens.AboutSection
import com.elkhouly.commons.compose.screens.HelpUsSection
import com.elkhouly.commons.compose.screens.OtherSection
import com.elkhouly.commons.compose.screens.SocialSection
import com.elkhouly.commons.compose.theme.AppThemeSurface
import com.elkhouly.commons.dialogs.ConfirmationAdvancedAlertDialog
import com.elkhouly.commons.extensions.baseConfig
import com.elkhouly.commons.extensions.getStoreUrl
import com.elkhouly.commons.extensions.isThankYouInstalled
import com.elkhouly.commons.extensions.launchAppRatingPage
import com.elkhouly.commons.extensions.launchMoreAppsFromUsIntent
import com.elkhouly.commons.extensions.launchPurchaseThankYouIntent
import com.elkhouly.commons.extensions.launchViewIntent
import com.elkhouly.commons.extensions.showErrorToast
import com.elkhouly.commons.extensions.toast
import com.elkhouly.commons.helpers.APP_FAQ
import com.elkhouly.commons.helpers.PROJECT_URL
import com.elkhouly.commons.helpers.APP_ICON_IDS
import com.elkhouly.commons.helpers.APP_LAUNCHER_NAME
import com.elkhouly.commons.helpers.APP_LICENSES
import com.elkhouly.commons.helpers.APP_NAME
import com.elkhouly.commons.helpers.APP_PACKAGE_NAME
import com.elkhouly.commons.helpers.APP_REPOSITORY_NAME
import com.elkhouly.commons.helpers.APP_VERSION_NAME
import com.elkhouly.commons.helpers.SHOW_FAQ_BEFORE_MAIL
import com.elkhouly.commons.models.FAQItem

class AboutActivity : BaseComposeActivity() {
    private val appName get() = intent.getStringExtra(APP_NAME) ?: ""

    private var firstVersionClickTS = 0L
    private var clicksSinceFirstClick = 0

    companion object {
        private const val EASTER_EGG_TIME_LIMIT = 3000L
        private const val EASTER_EGG_REQUIRED_CLICKS = 7
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeSimple()
        setContent {
            val context = LocalContext.current
            val resources = context.resources
            AppThemeSurface {
                val showGoogleRelations =
                    remember { resources.getBoolean(R.bool.is_google_play_build) }
                val showGithubRelations = showGithubRelations()
                AboutScreen(
                    goBack = ::finish,
                    helpUsSection = {
                        HelpUsSection(
                            onRateThisAppClick = ::onRateThisAppClick,
                            onInviteClick = ::onInviteClick,
                            showDonate = false,
                            onDonateClick = ::onDonateClick,
                            showInvite = showGoogleRelations || showGithubRelations,
                            showRateUs = showGoogleRelations
                        )
                    },
                    aboutSection = {
                        val setupFAQ = showFAQ()
                        if (setupFAQ || showGithubRelations) {
                            AboutSection(
                                setupFAQ = setupFAQ,
                                setupKnownIssues = showGithubRelations,
                                onFAQClick = ::launchFAQActivity,
                                onKnownIssuesClick = ::launchIssueTracker
                            )
                        }
                    },
                    socialSection = {
                        SocialSection(
                            onGithubClick = ::onGithubClick
                        )
                    }
                ) {
                    val (versionName, packageName) = getPackageInfo()
                    OtherSection(
                        showMoreApps = false,
                        onMoreAppsClick = ::launchMoreAppsFromUsIntent,
                        onPrivacyPolicyClick = ::onPrivacyPolicyClick,
                        onLicenseClick = ::onLicenseClick,
                        versionName = versionName,
                        packageName = packageName,
                        onVersionClick = ::onVersionClick
                    )
                }
            }
        }
    }

    private fun getGithubUrl(): String {
        return PROJECT_URL
    }

    @Composable
    private fun showFAQ() =
        remember { !(intent.getSerializableExtra(APP_FAQ) as? ArrayList<FAQItem>).isNullOrEmpty() }

    @Composable
    private fun showGithubRelations() =
        remember { !intent.getStringExtra(APP_REPOSITORY_NAME).isNullOrEmpty() }

    @Composable
    private fun getPackageInfo(): Pair<String, String> {
        val versionName = remember { intent.getStringExtra(APP_VERSION_NAME) ?: "" }
        val packageName = remember { intent.getStringExtra(APP_PACKAGE_NAME) ?: "" }

        val fullVersion = stringResource(R.string.version_placeholder, versionName)
        return Pair(fullVersion, packageName)
    }

    @Composable
    private fun getOnEmailClickAlertDialogState() =
        rememberAlertDialogState().apply {
            DialogMember {
                ConfirmationAdvancedAlertDialog(
                    alertDialogState = this,
                    message = "${getString(R.string.before_asking_question_read_faq)}\n\n${getString(R.string.make_sure_latest)}",
                    messageId = null,
                    positive = R.string.read_faq,
                    negative = R.string.skip
                ) { success ->
                    if (success) {
                        launchFAQActivity()
                    } else {
                        launchEmailIntent()
                    }
                }
            }
        }

    private fun onEmailClick(
        showConfirmationAdvancedDialog: () -> Unit,
    ) {
        if (intent.getBooleanExtra(SHOW_FAQ_BEFORE_MAIL, false)
            && !baseConfig.wasBeforeAskingShown
        ) {
            baseConfig.wasBeforeAskingShown = true
            showConfirmationAdvancedDialog()
        } else {
            launchEmailIntent()
        }
    }

    private fun launchFAQActivity() {
        val faqItems = intent.getSerializableExtra(APP_FAQ) as ArrayList<FAQItem>
        Intent(applicationContext, FAQActivity::class.java).apply {
            putExtra(
                APP_ICON_IDS,
                intent.getIntegerArrayListExtra(APP_ICON_IDS) ?: ArrayList<String>()
            )
            putExtra(APP_LAUNCHER_NAME, intent.getStringExtra(APP_LAUNCHER_NAME) ?: "")
            putExtra(APP_FAQ, faqItems)
            startActivity(this)
        }
    }

    private fun launchIssueTracker() {
        launchViewIntent(
            "${getGithubUrl()}/issues?q=is:open+is:issue+label:bug"
        )
    }

    private fun launchEmailIntent() {
        val appVersion = getString(R.string.app_version, intent.getStringExtra(APP_VERSION_NAME))
        val deviceOS = getString(R.string.device_os, Build.VERSION.RELEASE)
        val newline = "\n"
        val separator = "------------------------------"
        val body = "$appVersion$newline$deviceOS$newline$separator$newline$newline"

        val address = if (packageName.startsWith("com.elkhouly")) {
            getString(R.string.my_email)
        } else {
            getString(R.string.my_fake_email)
        }

        val selectorIntent = Intent(ACTION_SENDTO)
            .setData("mailto:$address".toUri())
        val emailIntent = Intent(ACTION_SEND).apply {
            putExtra(EXTRA_EMAIL, arrayOf(address))
            putExtra(EXTRA_SUBJECT, appName)
            putExtra(EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            selector = selectorIntent
        }

        try {
            startActivity(emailIntent)
        } catch (e: ActivityNotFoundException) {
            val chooser = createChooser(emailIntent, getString(R.string.send_email))
            try {
                startActivity(chooser)
            } catch (e: Exception) {
                toast(R.string.no_email_client_found)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun onRateThisAppClick() {
        launchAppRatingPage()
    }

    private fun onInviteClick() {
        val storeUrl = when {
            resources.getBoolean(R.bool.is_google_play_build) -> getStoreUrl()
            else -> getGithubUrl()
        }

        val text = String.format(getString(R.string.share_text), appName, storeUrl)
        Intent().apply {
            action = ACTION_SEND
            putExtra(EXTRA_SUBJECT, appName)
            putExtra(EXTRA_TEXT, text)
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(createChooser(this, getString(R.string.invite_via)))
        }
    }

    private fun onContributorsClick() {
        val intent = Intent(applicationContext, ContributorsActivity::class.java)
        startActivity(intent)
    }

    private fun onDonateClick() {
        if (resources.getBoolean(R.bool.is_google_play_build)) {
            launchPurchaseThankYouIntent()
        } else {
            startActivity(Intent(applicationContext, DonationActivity::class.java))
        }
    }

    private fun onGithubClick() {
        launchViewIntent(PROJECT_URL)
    }

    private fun onPrivacyPolicyClick() {
        launchViewIntent("$PROJECT_URL/blob/main/PRIVACY.md")
    }

    private fun onLicenseClick() {
        Intent(applicationContext, LicenseActivity::class.java).apply {
            putExtra(
                APP_ICON_IDS,
                intent.getIntegerArrayListExtra(APP_ICON_IDS) ?: ArrayList<String>()
            )
            putExtra(APP_LAUNCHER_NAME, intent.getStringExtra(APP_LAUNCHER_NAME) ?: "")
            putExtra(APP_LICENSES, intent.getLongExtra(APP_LICENSES, 0))
            startActivity(this)
        }
    }

    private fun onVersionClick() {
        if (firstVersionClickTS == 0L) {
            firstVersionClickTS = System.currentTimeMillis()
            Handler(Looper.getMainLooper()).postDelayed({
                firstVersionClickTS = 0L
                clicksSinceFirstClick = 0
            }, EASTER_EGG_TIME_LIMIT)
        }

        clicksSinceFirstClick++
        if (clicksSinceFirstClick >= EASTER_EGG_REQUIRED_CLICKS) {
            toast(R.string.hello)
            firstVersionClickTS = 0L
            clicksSinceFirstClick = 0
        }
    }
}
