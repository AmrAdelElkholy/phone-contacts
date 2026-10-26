package com.elkhouly.contacts.interfaces

import com.elkhouly.commons.models.contacts.Contact

interface RemoveFromGroupListener {
    fun removeFromGroup(contacts: ArrayList<Contact>)
}
