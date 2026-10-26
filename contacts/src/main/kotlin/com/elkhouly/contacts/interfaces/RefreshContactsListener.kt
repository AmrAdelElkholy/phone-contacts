package com.elkhouly.contacts.interfaces

import com.elkhouly.commons.models.contacts.Contact

interface RefreshContactsListener {
    fun refreshContacts(refreshTabsMask: Int)

    fun contactClicked(contact: Contact)
}
