package com.elkhouly.dialer.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import com.elkhouly.contacts.activities.MainActivity as ContactsManagerActivity
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import me.grantland.widget.AutofitHelper
import com.elkhouly.commons.activities.ManageBlockedNumbersActivity
import com.elkhouly.commons.dialogs.ChangeViewTypeDialog
import com.elkhouly.commons.dialogs.ConfirmationDialog
import com.elkhouly.commons.dialogs.PermissionRequiredDialog
import com.elkhouly.commons.dialogs.RadioGroupDialog
import com.elkhouly.commons.extensions.*
import com.elkhouly.commons.helpers.*
import com.elkhouly.commons.models.FAQItem
import com.elkhouly.commons.models.RadioItem
import com.elkhouly.commons.models.contacts.Contact
import com.elkhouly.dialer.BuildConfig
import com.elkhouly.dialer.R
import com.elkhouly.dialer.adapters.ViewPagerAdapter
import com.elkhouly.dialer.databinding.ActivityMainBinding
import com.elkhouly.dialer.dialogs.ChangeSortingDialog
import com.elkhouly.dialer.dialogs.FilterContactSourcesDialog
import com.elkhouly.dialer.extensions.clearMissedCalls
import com.elkhouly.dialer.extensions.config
import com.elkhouly.dialer.extensions.handleFullScreenNotificationsPermission
import com.elkhouly.dialer.extensions.launchCreateNewContactIntent
import com.elkhouly.dialer.fragments.ContactsFragment
import com.elkhouly.dialer.fragments.FavoritesFragment
import com.elkhouly.dialer.fragments.MyViewPagerFragment
import com.elkhouly.dialer.fragments.RecentsFragment
import com.elkhouly.dialer.helpers.ContactsCache
import com.elkhouly.dialer.helpers.MainDialpad
import com.elkhouly.dialer.helpers.OPEN_DIAL_PAD_AT_LAUNCH
import com.elkhouly.dialer.helpers.RecentsHelper
import com.elkhouly.dialer.helpers.tabsList
import com.elkhouly.dialer.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : SimpleActivity() {
    companion object {
        private const val CONTACT_TABS_REFRESH_DELAY_MS = 1500L
        private const val CONTACTS_CACHE_MAX_AGE_MS = 5000L
    }

    override var isSearchBarEnabled = true
    
    private val binding by viewBinding(ActivityMainBinding::inflate)

    private var launchedDialer = false
    private var mainDialpad: MainDialpad? = null
    private val scrollHookedFragments = HashSet<Int>()
    private var dialpadFilteredFragment: MyViewPagerFragment<*>? = null
    private var storedShowTabs = 0
    private var storedFontSize = 0
    private var storedStartNameWithSurname = false
    var cachedContacts = ArrayList<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.mainTabsHolder))

        EventBus.getDefault().register(this)
        launchedDialer = savedInstanceState?.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH) ?: false

        if (isDefaultDialer()) {
            checkContactPermissions()

            if (!config.wasOverlaySnackbarConfirmed && !Settings.canDrawOverlays(this)) {
                val snackbar = Snackbar.make(
                    binding.mainHolder,
                    R.string.allow_displaying_over_other_apps,
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.ok) {
                    config.wasOverlaySnackbarConfirmed = true
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }

                snackbar.setBackgroundTint(getProperBackgroundColor().darkenColor())
                snackbar.setTextColor(getProperTextColor())
                snackbar.setActionTextColor(getProperTextColor())
                snackbar.show()
            }

            handleFullScreenNotificationsPermission { granted ->
                if (!granted) {
                    toast(com.elkhouly.commons.R.string.notifications_disabled)
                }
            }
        } else {
            launchSetDefaultDialerIntent()
        }

        if (isQPlus() && (config.blockUnknownNumbers || config.blockHiddenNumbers)) {
            setDefaultCallerIdApp()
        }

        config.ensureCallHistoryDefaultTab()
        setupTabs()
        Contact.sorting = config.sorting
        mainDialpad = MainDialpad(
            activity = this,
            binding = binding,
            onInputChanged = ::applyDialpadFilter,
            getLastCalledNumber = { getRecentsFragment()?.getLastCalledNumber() },
            onExpandedChanged = ::updateDialpadVisibility
        )
    }

    override fun onResume() {
        super.onResume()
        if (storedShowTabs != config.showTabs) {
            config.lastUsedViewPagerPage = 0
            System.exit(0)
            return
        }

        updateMenuColors()
        val properPrimaryColor = getProperPrimaryColor()
        val dialpadIcon = resources.getColoredDrawableWithColor(R.drawable.ic_dialpad_vector, properPrimaryColor.getContrastColor())
        binding.mainDialpadButton.setImageDrawable(dialpadIcon)

        updateTextColors(binding.mainHolder)
        mainDialpad?.onResume()
        updateDialpadVisibility()
        setupTabColors()

        getAllFragments().forEach {
            it?.setupColors(getProperTextColor(), getProperPrimaryColor(), getProperPrimaryColor())
        }

        val configStartNameWithSurname = config.startNameWithSurname
        if (storedStartNameWithSurname != configStartNameWithSurname) {
            getContactsFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
            getFavoritesFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
            storedStartNameWithSurname = config.startNameWithSurname
        }

        if (!binding.mainMenu.isSearchOpen) {
            refreshItems(true)
        }

        val configFontSize = config.fontSize
        if (storedFontSize != configFontSize) {
            getAllFragments().forEach {
                it?.fontSizeChanged()
            }
        }

        checkShortcuts()
        Handler().postDelayed({
            getRecentsFragment()?.refreshItems()
        }, 2000)
    }

    override fun onPause() {
        super.onPause()
        storedShowTabs = config.showTabs
        storedStartNameWithSurname = config.startNameWithSurname
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        // we don't really care about the result, the app can work without being the default Dialer too
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
            checkContactPermissions()
        } else if (requestCode == REQUEST_CODE_SET_DEFAULT_CALLER_ID && resultCode != Activity.RESULT_OK) {
            toast(R.string.must_make_default_caller_id_app, length = Toast.LENGTH_LONG)
            baseConfig.blockUnknownNumbers = false
            baseConfig.blockHiddenNumbers = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, launchedDialer)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshItems()
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            true
        } else if (mainDialpad?.input?.isNotEmpty() == true) {
            mainDialpad?.clearInput()
            true
        } else {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    private fun refreshMenuItems() {
        val currentFragment = getCurrentFragment()
        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.clear_call_history).isVisible = currentFragment == getRecentsFragment()
            findItem(R.id.blocked_numbers).isVisible = currentFragment == getRecentsFragment() && isNougatPlus()
            findItem(R.id.sort).isVisible = currentFragment != getRecentsFragment()
            findItem(R.id.filter).isVisible = currentFragment != getRecentsFragment()
            findItem(R.id.create_new_contact).isVisible = currentFragment == getContactsFragment()
            findItem(R.id.change_view_type).isVisible = currentFragment == getFavoritesFragment()
            findItem(R.id.column_count).isVisible = currentFragment == getFavoritesFragment() && config.viewType == VIEW_TYPE_GRID
            findItem(R.id.more_apps_from_us).isVisible = false
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.apply {
            requireToolbar().inflateMenu(R.menu.menu)
            toggleHideOnScroll(false)
            setupMenu()

            onSearchClosedListener = {
                getAllFragments().forEach {
                    it?.onSearchQueryChanged("")
                }
            }

            onSearchTextChangedListener = { text ->
                getCurrentFragment()?.onSearchQueryChanged(text)
            }

            requireToolbar().setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.clear_call_history -> clearCallHistory()
                    R.id.blocked_numbers -> startActivity(Intent(this@MainActivity, ManageBlockedNumbersActivity::class.java))
                    R.id.create_new_contact -> launchCreateNewContactIntent()
                    R.id.contacts_manager -> startActivity(Intent(this@MainActivity, ContactsManagerActivity::class.java))
                    R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
                    R.id.filter -> showFilterDialog()
                    R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                    R.id.settings -> launchSettings()
                    R.id.change_view_type -> changeViewType()
                    R.id.column_count -> changeColumnCount()
                    R.id.about -> launchAbout()
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }
        }
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..CONTACTS_GRID_MAX_COLUMNS_COUNT) {
            items.add(RadioItem(i, resources.getQuantityString(R.plurals.column_counts, i, i)))
        }

        val currentColumnCount = config.contactsGridColumnCount
        RadioGroupDialog(this, ArrayList(items), currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.contactsGridColumnCount = newColumnCount
                getFavoritesFragment()?.columnCountChanged()
            }
        }
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this) {
            refreshMenuItems()
            getFavoritesFragment()?.refreshItems()
        }
    }

    private fun updateMenuColors() {
        binding.mainMenu.updateColors()
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            initFragments()
        }
    }

    private fun clearCallHistory() {
        val confirmationText = "${getString(R.string.clear_history_confirmation)}\n\n${getString(R.string.cannot_be_undone)}"
        ConfirmationDialog(this, confirmationText) {
            RecentsHelper(this).removeAllRecentCalls(this) {
                runOnUiThread {
                    getRecentsFragment()?.refreshItems(invalidate = true)
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val launchDialpad = getLaunchDialpadShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(launchDialpad)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getLaunchDialpadShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.dialpad)
        val drawable = resources.getDrawable(R.drawable.shortcut_dialpad)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_dialpad_background).applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, DialpadActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "launch_dialpad")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun setupTabColors() {
        val activeView = binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.customView
        updateBottomTabItemColors(activeView, true, getSelectedTabDrawableIds()[binding.viewPager.currentItem])

        getInactiveTabIndexes(binding.viewPager.currentItem).forEach { index ->
            val inactiveView = binding.mainTabsHolder.getTabAt(index)?.customView
            updateBottomTabItemColors(inactiveView, false, getDeselectedTabDrawableIds()[index])
        }

        val bottomBarColor = getBottomNavigationBackgroundColor()
        binding.mainTabsHolder.setBackgroundColor(bottomBarColor)
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until binding.mainTabsHolder.tabCount).filter { it != activeIndex }

    private fun getSelectedTabDrawableIds(): List<Int> {
        val showTabs = config.showTabs
        val icons = mutableListOf<Int>()

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(R.drawable.ic_person_vector)
        }

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(R.drawable.ic_star_vector)
        }

        if (showTabs and TAB_CALL_HISTORY != 0) {
            icons.add(R.drawable.ic_clock_filled_vector)
        }

        return icons
    }

    private fun getDeselectedTabDrawableIds(): ArrayList<Int> {
        val showTabs = config.showTabs
        val icons = ArrayList<Int>()

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(R.drawable.ic_person_outline_vector)
        }

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(R.drawable.ic_star_outline_vector)
        }

        if (showTabs and TAB_CALL_HISTORY != 0) {
            icons.add(R.drawable.ic_clock_vector)
        }

        return icons
    }

    private fun initFragments() {
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                binding.mainTabsHolder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                refreshMenuItems()
                onTabChangedForDialpad()
            }
        })

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        binding.mainTabsHolder.onGlobalLayout {
            Handler().postDelayed({
                var wantedTab = getDefaultTab()

                // open the Recents tab if we got here by clicking a missed call notification
                if (intent.action == Intent.ACTION_VIEW && config.showTabs and TAB_CALL_HISTORY > 0) {
                    wantedTab = binding.mainTabsHolder.tabCount - 1
                }

                binding.mainTabsHolder.getTabAt(wantedTab)?.select()
                refreshMenuItems()
                onTabChangedForDialpad()
            }, 100L)
        }

        binding.mainDialpadButton.setOnClickListener {
            mainDialpad?.expand()
        }

        binding.viewPager.onGlobalLayout {
            refreshMenuItems()
        }

        if (config.openDialPadAtLaunch && !launchedDialer) {
            launchDialpad()
            launchedDialer = true
        }
    }

    private fun setupTabs() {
        binding.viewPager.adapter = null
        binding.mainTabsHolder.removeAllTabs()
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                binding.mainTabsHolder.newTab().setCustomView(R.layout.bottom_tablayout_item).apply {
                    customView?.findViewById<ImageView>(R.id.tab_item_icon)?.setImageDrawable(getTabIcon(index))
                    customView?.findViewById<TextView>(R.id.tab_item_label)?.text = getTabLabel(index)
                    AutofitHelper.create(customView?.findViewById(R.id.tab_item_label))
                    binding.mainTabsHolder.addTab(this)
                }
            }
        }

        binding.mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false, getDeselectedTabDrawableIds()[it.position])
            },
            tabSelectedAction = {
                getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                binding.viewPager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true, getSelectedTabDrawableIds()[it.position])

                val lastPosition = binding.mainTabsHolder.tabCount - 1
                if (it.position == lastPosition && config.showTabs and TAB_CALL_HISTORY > 0) {
                    clearMissedCalls()
                }
            }
        )

        binding.mainTabsHolder.beGoneIf(binding.mainTabsHolder.tabCount == 1)
        storedShowTabs = config.showTabs
        storedStartNameWithSurname = config.startNameWithSurname
    }

    private fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            0 -> R.drawable.ic_person_vector
            1 -> R.drawable.ic_star_vector
            else -> R.drawable.ic_clock_vector
        }

        return resources.getColoredDrawableWithColor(drawableId, getProperTextColor())
    }

    private fun getTabLabel(position: Int): String {
        val stringId = when (position) {
            0 -> R.string.contacts_tab
            1 -> R.string.favorites_tab
            else -> R.string.call_history_tab
        }

        return resources.getString(stringId)
    }

    private fun refreshItems(openLastTab: Boolean = false) {
        if (isDestroyed || isFinishing) {
            return
        }

        binding.apply {
            if (viewPager.adapter == null) {
                viewPager.adapter = ViewPagerAdapter(this@MainActivity)
                viewPager.currentItem = if (openLastTab) config.lastUsedViewPagerPage else getDefaultTab()
                viewPager.onGlobalLayout {
                    refreshFragments()
                }
            } else {
                refreshFragments()
            }
        }
    }

    // decided by position, the fragment views may not exist yet while the app is opening
    private fun isOnRecentsTab(): Boolean {
        val showTabs = config.showTabs
        if (showTabs and TAB_CALL_HISTORY == 0) {
            return false
        }
        val recentsPosition = tabsList.count { showTabs and it != 0 } - 1
        return binding.viewPager.currentItem == recentsPosition
    }

    // the inline dialpad is shown expanded on the call history and opened with the FAB elsewhere
    private fun updateDialpadVisibility() {
        mainDialpad?.setVisible(true)
        binding.mainDialpadButton.beVisibleIf(mainDialpad?.isExpanded == false)

        getAllFragments().filterNotNull().forEach { fragment ->
            if (scrollHookedFragments.add(fragment.id)) {
                fragment.setOnListDraggedListener {
                    if (fragment == getCurrentFragment() && mainDialpad?.input?.isEmpty() == true) {
                        mainDialpad?.collapse()
                    }
                }
            }
        }
    }

    private fun onTabChangedForDialpad() {
        val input = mainDialpad?.input.orEmpty()
        if (isOnRecentsTab()) {
            // the call history always comes with its dialpad, like the stock dialer
            mainDialpad?.expand()
        } else if (input.isEmpty()) {
            // contacts and favorites open with their full list visible
            mainDialpad?.collapse()
        }
        applyDialpadFilter(input)
        updateDialpadVisibility()
    }

    // typing on the dialpad filters whichever tab is open
    private fun applyDialpadFilter(text: String) {
        val current = getCurrentFragment()
        if (dialpadFilteredFragment != current) {
            dialpadFilteredFragment?.onSearchClosed()
        }

        dialpadFilteredFragment = current
        if (text.isEmpty()) {
            current?.onSearchClosed()
        } else {
            current?.onSearchQueryChanged(text)
        }
    }

    // with the dialpad open, picking a contact puts its number on the dialpad instead of calling
    fun pickContactNumberForDialpad(contact: Contact): Boolean {
        if (mainDialpad?.isExpanded != true) {
            return false
        }

        val numbers = contact.phoneNumbers
        when (numbers.size) {
            0 -> toast(com.elkhouly.commons.R.string.no_phone_number_found)
            1 -> mainDialpad?.setInput(getDialableNumber(numbers.first().value))
            else -> {
                val items = numbers.mapIndexed { index, number ->
                    RadioItem(index, "${number.value} (${getPhoneNumberTypeText(number.type, number.label)})")
                } as ArrayList<RadioItem>
                RadioGroupDialog(this, items) {
                    mainDialpad?.setInput(getDialableNumber(numbers[it as Int].value))
                }
            }
        }
        return true
    }

    private fun getDialableNumber(number: String) = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    private fun launchDialpad() {
        Intent(applicationContext, DialpadActivity::class.java).apply {
            startActivity(this)
        }
    }

    fun refreshFragments() {
        // reload contacts for this refresh unless a load is running or just finished,
        // contact edits invalidate the cache on their own
        ContactsCache.invalidateIfOlderThan(CONTACTS_CACHE_MAX_AGE_MS)
        cacheContacts()
        getRecentsFragment()?.refreshItems()

        // give the call history a head start when it's the visible tab, low end phones
        // otherwise load every list at once
        val delay = if (isOnRecentsTab()) CONTACT_TABS_REFRESH_DELAY_MS else 0L
        binding.viewPager.postDelayed({
            if (!isDestroyed && !isFinishing) {
                getContactsFragment()?.refreshItems()
                getFavoritesFragment()?.refreshItems()
            }
        }, delay)
    }

    private fun getAllFragments(): ArrayList<MyViewPagerFragment<*>?> {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment<*>?>()

        if (showTabs and TAB_CONTACTS > 0) {
            fragments.add(getContactsFragment())
        }

        if (showTabs and TAB_FAVORITES > 0) {
            fragments.add(getFavoritesFragment())
        }

        if (showTabs and TAB_CALL_HISTORY > 0) {
            fragments.add(getRecentsFragment())
        }

        return fragments
    }

    private fun getCurrentFragment(): MyViewPagerFragment<*>? = getAllFragments().getOrNull(binding.viewPager.currentItem)

    private fun getContactsFragment(): ContactsFragment? = findViewById(R.id.contacts_fragment)

    private fun getFavoritesFragment(): FavoritesFragment? = findViewById(R.id.favorites_fragment)

    private fun getRecentsFragment(): RecentsFragment? = findViewById(R.id.recents_fragment)

    private fun getDefaultTab(): Int {
        val showTabsMask = config.showTabs
        return when (config.defaultTab) {
            TAB_LAST_USED -> if (config.lastUsedViewPagerPage < binding.mainTabsHolder.tabCount) config.lastUsedViewPagerPage else 0
            TAB_CONTACTS -> 0
            TAB_FAVORITES -> if (showTabsMask and TAB_CONTACTS > 0) 1 else 0
            else -> {
                if (showTabsMask and TAB_CALL_HISTORY > 0) {
                    if (showTabsMask and TAB_CONTACTS > 0) {
                        if (showTabsMask and TAB_FAVORITES > 0) {
                            2
                        } else {
                            1
                        }
                    } else {
                        if (showTabsMask and TAB_FAVORITES > 0) {
                            1
                        } else {
                            0
                        }
                    }
                } else {
                    0
                }
            }
        }
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL or LICENSE_AUTOFITTEXTVIEW

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(R.string.faq_2_title, R.string.faq_2_text),
            FAQItem(R.string.faq_3_title, R.string.faq_3_text),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        if (resources.getBoolean(R.bool.is_google_play_build)) {
            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons))
            faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        ChangeSortingDialog(this, showCustomSorting) {
            getFavoritesFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getContactsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }
        }
    }

    private fun showFilterDialog() {
        FilterContactSourcesDialog(this) {
            getFavoritesFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getContactsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getRecentsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }
        }
    }

    fun cacheContacts() {
        ContactsCache.get(this) { contacts, privateContacts ->
            if (SMT_PRIVATE !in config.ignoredContactSources) {
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                    contacts.sort()
                }
            }

            try {
                cachedContacts.clear()
                cachedContacts.addAll(contacts)
            } catch (ignored: Exception) {
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshCallLog(event: Events.RefreshCallLog) {
        getRecentsFragment()?.refreshItems()
    }
}
