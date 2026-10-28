package com.elkhouly.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import com.elkhouly.commons.extensions.baseConfig
import com.elkhouly.commons.extensions.beGone
import com.elkhouly.commons.extensions.beGoneIf
import com.elkhouly.commons.extensions.beVisible
import com.elkhouly.commons.extensions.hasPermission
import com.elkhouly.commons.extensions.isVisible
import com.elkhouly.commons.extensions.underlineText
import com.elkhouly.commons.helpers.PERMISSION_READ_CALL_LOG
import com.elkhouly.commons.helpers.SMT_PRIVATE
import com.elkhouly.commons.helpers.ensureBackgroundThread
import com.elkhouly.commons.models.contacts.Contact
import com.elkhouly.dialer.R
import com.elkhouly.dialer.activities.MainActivity
import com.elkhouly.dialer.activities.SimpleActivity
import com.elkhouly.dialer.adapters.RecentCallsAdapter
import com.elkhouly.dialer.databinding.FragmentRecentsBinding
import com.elkhouly.dialer.extensions.config
import com.elkhouly.dialer.extensions.runAfterAnimations
import com.elkhouly.dialer.extensions.startAddContactIntent
import com.elkhouly.dialer.extensions.startCallWithConfirmationCheck
import com.elkhouly.dialer.extensions.startContactDetailsIntent
import com.elkhouly.dialer.helpers.ContactsCache
import com.elkhouly.dialer.helpers.RecentsHelper
import com.elkhouly.dialer.interfaces.RefreshItemsListener
import com.elkhouly.dialer.models.CallLogItem
import com.elkhouly.dialer.models.RecentCall

class RecentsFragment(
    context: Context, attributeSet: AttributeSet,
) : MyViewPagerFragment<MyViewPagerFragment.RecentsInnerBinding>(context, attributeSet), RefreshItemsListener {

    private lateinit var binding: FragmentRecentsBinding
    private var allRecentCalls = listOf<CallLogItem>()

    // calls from the quick first load, used for searching until the full load lands
    private var quickRecentCalls = listOf<CallLogItem>()
    private var recentsAdapter: RecentCallsAdapter? = null

    private var searchQuery: String? = null
    private var recentsHelper = RecentsHelper(context)

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentRecentsBinding.bind(this)
        innerBinding = RecentsInnerBinding(binding)
    }

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            R.string.no_previous_calls
        } else {
            R.string.could_not_access_the_call_history
        }

        binding.recentsPlaceholder.text = context.getString(placeholderResId)
        binding.recentsPlaceholder2.apply {
            underlineText()
            setOnClickListener {
                requestCallLogPermission()
            }
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.recentsPlaceholder.setTextColor(textColor)
        binding.recentsPlaceholder2.setTextColor(properPrimaryColor)

        recentsAdapter?.apply {
            updateTextColor(textColor)
            initDrawables()
        }
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        if (invalidate) {
            allRecentCalls = emptyList()
        }

        if (allRecentCalls.isEmpty()) {
            showQuickCallLog()
        }

        refreshCallLog(loadAll = false) {
            binding.recentsList.runAfterAnimations {
                refreshCallLog(loadAll = true)
            }
        }
    }

    fun getLastCalledNumber(): String? = allRecentCalls.filterIsInstance<RecentCall>().firstOrNull()?.phoneNumber

    override fun onSearchClosed() {
        searchQuery = null
        val calls = getSearchableCalls()
        if (calls.isEmpty() && allRecentCalls.isEmpty()) {
            // still loading, keep whatever is shown
            return
        }

        showOrHidePlaceholder(calls.isEmpty())
        recentsAdapter?.updateItems(calls)
    }

    override fun onSearchQueryChanged(text: String) {
        if (text.isEmpty()) {
            onSearchClosed()
            return
        }

        searchQuery = text
        updateSearchResult()
    }

    private fun getSearchableCalls() = allRecentCalls.ifEmpty { quickRecentCalls }

    @Suppress("UNCHECKED_CAST")
    private fun updateSearchResult() {
        ensureBackgroundThread {
            val fixedText = searchQuery!!.trim().replace("\\s+".toRegex(), " ")
            val recentCalls = getSearchableCalls()
                .filterIsInstance<RecentCall>()
                .filter {
                    it.name.contains(fixedText, true) || it.doesContainPhoneNumber(fixedText)
                }
                .sortedWith(
                    compareByDescending<RecentCall> { it.dayCode }
                        .thenByDescending { it.name.startsWith(fixedText, true) }
                        .thenByDescending { it.startTS }
                )

            prepareCallLog(recentCalls) {
                activity?.runOnUiThread {
                    if (recentsAdapter == null) {
                        // searching before the first load finished, the list isn't set up yet
                        gotRecents(it)
                    }
                    showOrHidePlaceholder(recentCalls.isEmpty())
                    recentsAdapter?.updateItems(it, fixedText)
                }
            }
        }
    }

    private fun requestCallLogPermission() {
        activity?.handlePermission(PERMISSION_READ_CALL_LOG) {
            if (it) {
                binding.recentsPlaceholder.text = context.getString(R.string.no_previous_calls)
                binding.recentsPlaceholder2.beGone()
                refreshCallLog()
            }
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        if (show && !binding.progressIndicator.isVisible()) {
            binding.recentsPlaceholder.beVisible()
        } else {
            binding.recentsPlaceholder.beGone()
        }
    }

    private fun gotRecents(recents: List<CallLogItem>) {
        binding.progressIndicator.hide()
        if (recents.isEmpty()) {
            binding.apply {
                showOrHidePlaceholder(true)
                recentsPlaceholder2.beGoneIf(context.hasPermission(PERMISSION_READ_CALL_LOG))
                recentsList.beGone()
            }
        } else {
            binding.apply {
                showOrHidePlaceholder(false)
                recentsPlaceholder2.beGone()
                recentsList.beVisible()
            }

            if (binding.recentsList.adapter == null) {
                recentsAdapter = RecentCallsAdapter(
                    activity = activity as SimpleActivity,
                    recyclerView = binding.recentsList,
                    refreshItemsListener = this,
                    showOverflowMenu = true,
                    itemDelete = { deleted ->
                        allRecentCalls = allRecentCalls.filter { it !in deleted }
                    },
                    itemClick = {
                        val recentCall = it as RecentCall
                        activity?.startCallWithConfirmationCheck(recentCall.phoneNumber, recentCall.name)
                    },
                    profileIconClick = {
                        val recentCall = it as RecentCall
                        val contact = findContactByCall(recentCall)
                        if (contact != null) {
                            activity?.startContactDetailsIntent(contact)
                        } else {
                            activity?.startAddContactIntent(recentCall.phoneNumber)
                        }
                    }
                )

                binding.recentsList.adapter = recentsAdapter
                recentsAdapter?.updateItems(recents)
            } else {
                recentsAdapter?.updateItems(recents)
            }
        }
    }

    private fun refreshCallLog(loadAll: Boolean = false, callback: (() -> Unit)? = null) {
        getRecentCalls(loadAll) {
            allRecentCalls = it
            if (searchQuery.isNullOrEmpty()) {
                activity?.runOnUiThread { gotRecents(it) }
            } else {
                // the dialpad already holds a number, show its matches and stop the spinner
                activity?.runOnUiThread { binding.progressIndicator.hide() }
                updateSearchResult()
            }

            callback?.invoke()
        }
    }

    // shows the call log straight from the system before contacts are loaded, the full load
    // that follows adds photos and number types. It isn't kept in allRecentCalls, which the
    // full load merges with
    private fun showQuickCallLog() {
        val onQuickCalls: (List<RecentCall>) -> Unit = { calls ->
            val callLog = groupCallsByDate(calls)
            activity?.runOnUiThread {
                // only the full load may report an empty history, until then keep the spinner
                if (callLog.isNotEmpty() && allRecentCalls.isEmpty()) {
                    quickRecentCalls = callLog
                    if (searchQuery.isNullOrEmpty()) {
                        gotRecents(callLog)
                    }
                }
            }
        }

        // separate helper, the shared one keeps the query limit of the full load
        val quickHelper = RecentsHelper(context)
        if (context.config.groupSubsequentCalls) {
            quickHelper.getGroupedRecentCalls(withContacts = false, callback = onQuickCalls)
        } else {
            quickHelper.getRecentCalls(withContacts = false, callback = onQuickCalls)
        }
    }

    private fun getRecentCalls(loadAll: Boolean, callback: (List<CallLogItem>) -> Unit) {
        val queryCount = if (loadAll) Int.MAX_VALUE else RecentsHelper.QUERY_LIMIT
        val existingRecentCalls = allRecentCalls.filterIsInstance<RecentCall>()

        with(recentsHelper) {
            if (context.config.groupSubsequentCalls) {
                getGroupedRecentCalls(existingRecentCalls, queryCount) {
                    prepareCallLog(it, callback)
                }
            } else {
                getRecentCalls(existingRecentCalls, queryCount) {
                    prepareCallLog(it, callback)
                }
            }
        }
    }

    private fun prepareCallLog(calls: List<RecentCall>, callback: (List<CallLogItem>) -> Unit) {
        if (calls.isEmpty()) {
            callback(emptyList())
            return
        }

        ContactsCache.get(context) { contacts, privateContacts ->
            ensureBackgroundThread {
                val updatedCalls = updateNamesIfEmpty(
                    calls = maybeFilterPrivateCalls(calls, privateContacts),
                    contacts = contacts,
                    privateContacts = privateContacts
                )

                callback(
                    groupCallsByDate(updatedCalls)
                )
            }
        }
    }

    private fun maybeFilterPrivateCalls(calls: List<RecentCall>, privateContacts: List<Contact>): List<RecentCall> {
        val ignoredSources = context.baseConfig.ignoredContactSources
        return if (SMT_PRIVATE in ignoredSources) {
            val privateNumbers = privateContacts.flatMap { it.phoneNumbers }.map { it.value }
            calls.filterNot { it.phoneNumber in privateNumbers }
        } else {
            calls
        }
    }

    private fun updateNamesIfEmpty(calls: List<RecentCall>, contacts: List<Contact>, privateContacts: List<Contact>): List<RecentCall> {
        if (calls.isEmpty()) return mutableListOf()

        val contactsWithNumbers = contacts.filter { it.phoneNumbers.isNotEmpty() }
        return calls.map { call ->
            if (call.phoneNumber == call.name) {
                val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(call.phoneNumber) }
                val contact = contactsWithNumbers.firstOrNull { it.phoneNumbers.first().normalizedNumber == call.phoneNumber }

                when {
                    privateContact != null -> withUpdatedName(call = call, name = privateContact.getNameToDisplay())
                    contact != null -> withUpdatedName(call = call, name = contact.getNameToDisplay())
                    else -> call
                }
            } else {
                call
            }
        }
    }

    private fun withUpdatedName(call: RecentCall, name: String): RecentCall {
        return call.copy(
            name = name,
            groupedCalls = call.groupedCalls
                ?.map { it.copy(name = name) }
                ?.toMutableList()
                ?.ifEmpty { null }
        )
    }

    private fun groupCallsByDate(recentCalls: List<RecentCall>): MutableList<CallLogItem> {
        val callLog = mutableListOf<CallLogItem>()
        var lastDayCode = ""
        for (call in recentCalls) {
            val currentDayCode = call.dayCode
            if (currentDayCode != lastDayCode) {
                callLog += CallLogItem.Date(timestamp = call.startTS, dayCode = currentDayCode)
                lastDayCode = currentDayCode
            }

            callLog += call
        }

        return callLog
    }

    private fun findContactByCall(recentCall: RecentCall): Contact? {
        return (activity as MainActivity).cachedContacts.find { it.name == recentCall.name && it.doesHavePhoneNumber(recentCall.phoneNumber) }
    }
}
