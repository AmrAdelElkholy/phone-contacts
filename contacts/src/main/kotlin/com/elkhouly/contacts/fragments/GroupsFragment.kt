package com.elkhouly.contacts.fragments

import android.content.Context
import android.util.AttributeSet
import com.elkhouly.commons.helpers.TAB_GROUPS
import com.elkhouly.contacts.activities.MainActivity
import com.elkhouly.contacts.activities.SimpleActivity
import com.elkhouly.contacts.databinding.FragmentGroupsBinding
import com.elkhouly.contacts.databinding.FragmentLayoutBinding
import com.elkhouly.contacts.dialogs.CreateNewGroupDialog

class GroupsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.FragmentLayout>(context, attributeSet) {

    private lateinit var binding: FragmentGroupsBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentGroupsBinding.bind(this)
        innerBinding = FragmentLayout(FragmentLayoutBinding.bind(binding.root))
    }

    override fun fabClicked() {
        finishActMode()
        showNewGroupsDialog()
    }

    override fun placeholderClicked() {
        showNewGroupsDialog()
    }

    private fun showNewGroupsDialog() {
        CreateNewGroupDialog(activity as SimpleActivity) {
            (activity as? MainActivity)?.refreshContacts(TAB_GROUPS)
        }
    }
}
