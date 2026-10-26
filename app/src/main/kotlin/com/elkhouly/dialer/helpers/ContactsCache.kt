package com.elkhouly.dialer.helpers

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.elkhouly.commons.extensions.getMyContactsCursor
import com.elkhouly.commons.helpers.ContactsHelper
import com.elkhouly.commons.helpers.MyContactsContentProvider
import com.elkhouly.commons.models.contacts.Contact

/**
 * Shares one contacts load between the call log, the main screen and the contacts cache.
 * Opening the app used to load every contact around ten times in a row, which is slow on
 * low end phones. Callers that ask while a load is running wait for that same load.
 */
object ContactsCache {
    class Snapshot(val contacts: List<Contact>, val privateContacts: List<Contact>)

    private var snapshot: Snapshot? = null
    private var snapshotTime = 0L
    private var isLoading = false
    private var loadGeneration = 0
    private val waiting = mutableListOf<(Snapshot) -> Unit>()
    private var observer: ContentObserver? = null

    // the main screen refreshes several times while opening, reuse a load that is running or recent
    fun invalidateIfOlderThan(maxAgeMs: Long) {
        synchronized(this) {
            if (isLoading || (snapshot != null && System.currentTimeMillis() - snapshotTime < maxAgeMs)) {
                return
            }
        }
        invalidate()
    }

    fun invalidate() {
        synchronized(this) {
            snapshot = null
            // a load that started before the invalidation must not be cached
            loadGeneration++
            isLoading = false
        }
    }

    /** [callback] runs on a background thread and receives fresh lists it may modify. */
    fun get(context: Context, callback: (contacts: ArrayList<Contact>, privateContacts: ArrayList<Contact>) -> Unit) {
        val appContext = context.applicationContext
        val deliver = { result: Snapshot -> callback(ArrayList(result.contacts), ArrayList(result.privateContacts)) }
        val cached: Snapshot?
        val startLoad: Boolean
        val generation: Int
        synchronized(this) {
            registerObserver(appContext)
            cached = snapshot
            if (cached == null) {
                waiting.add(deliver)
            }
            startLoad = cached == null && !isLoading
            if (startLoad) {
                isLoading = true
            }
            generation = loadGeneration
        }

        if (cached != null) {
            deliver(cached)
            return
        }

        if (startLoad) {
            load(appContext, generation)
        }
    }

    private fun load(context: Context, generation: Int) {
        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ContactsHelper(context).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contacts ->
            val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
            val result = Snapshot(contacts, privateContacts)
            val callbacks: List<(Snapshot) -> Unit>
            synchronized(this) {
                if (generation == loadGeneration) {
                    snapshot = result
                    snapshotTime = System.currentTimeMillis()
                    isLoading = false
                    callbacks = waiting.toList()
                    waiting.clear()
                } else {
                    // invalidated meanwhile, the waiters get this result now and the next call reloads
                    callbacks = waiting.toList()
                    waiting.clear()
                }
            }
            callbacks.forEach { it(result) }
        }
    }

    private fun registerObserver(context: Context) {
        if (observer != null) {
            return
        }

        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                invalidate()
            }
        }.also {
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, it)
        }
    }
}
