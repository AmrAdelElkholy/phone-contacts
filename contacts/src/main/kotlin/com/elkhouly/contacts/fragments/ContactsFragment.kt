package com.elkhouly.contacts.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.elkhouly.commons.extensions.areSystemAnimationsEnabled
import com.elkhouly.commons.extensions.hideKeyboard
import com.elkhouly.commons.models.contacts.Contact
import com.elkhouly.contacts.activities.EditContactActivity
import com.elkhouly.contacts.activities.InsertOrEditContactActivity
import com.elkhouly.contacts.activities.MainActivity
import com.elkhouly.contacts.activities.SimpleActivity
import com.elkhouly.contacts.adapters.ContactsAdapter
import com.elkhouly.contacts.databinding.ContactsFragmentContactsBinding
import com.elkhouly.contacts.databinding.ContactsFragmentLettersLayoutBinding
import com.elkhouly.contacts.extensions.config
import com.elkhouly.contacts.extensions.viewContact
import com.elkhouly.contacts.helpers.LOCATION_CONTACTS_TAB
import com.elkhouly.contacts.interfaces.RefreshContactsListener

class ContactsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.LetterLayout>(context, attributeSet) {

    private lateinit var binding: ContactsFragmentContactsBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = ContactsFragmentContactsBinding.bind(this)
        innerBinding = LetterLayout(ContactsFragmentLettersLayoutBinding.bind(binding.root))
    }

    override fun fabClicked() {
        activity?.hideKeyboard()
        Intent(context, EditContactActivity::class.java).apply {
            context.startActivity(this)
        }
    }

    override fun placeholderClicked() {
        if (activity is MainActivity) {
            (activity as MainActivity).showFilterDialog()
        } else if (activity is InsertOrEditContactActivity) {
            (activity as InsertOrEditContactActivity).showFilterDialog()
        }
    }

    fun setupContactsAdapter(contacts: List<Contact>) {
        setupViewVisibility(contacts.isNotEmpty())
        val currAdapter = innerBinding.fragmentList.adapter

        if (currAdapter == null || forceListRedraw) {
            forceListRedraw = false
            val location = LOCATION_CONTACTS_TAB

            ContactsAdapter(
                activity = activity as SimpleActivity,
                contactItems = contacts.toMutableList(),
                refreshListener = activity as RefreshContactsListener,
                location = location,
                removeListener = null,
                recyclerView = innerBinding.fragmentList,
                enableDrag = false,
                itemClick = {
                    (activity as RefreshContactsListener).contactClicked(it as Contact)
                },
                profileIconClick = {
                    activity?.viewContact(it as Contact)
                }
            ).apply {
                innerBinding.fragmentList.adapter = this
            }

            if (context.areSystemAnimationsEnabled) {
                innerBinding.fragmentList.scheduleLayoutAnimation()
            }
        } else {
            (currAdapter as ContactsAdapter).apply {
                startNameWithSurname = context.config.startNameWithSurname
                showPhoneNumbers = context.config.showPhoneNumbers
                showContactThumbnails = context.config.showContactThumbnails
                updateItems(contacts)
            }
        }
    }
}
