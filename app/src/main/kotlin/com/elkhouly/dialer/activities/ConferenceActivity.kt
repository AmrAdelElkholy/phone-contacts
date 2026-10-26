package com.elkhouly.dialer.activities

import android.os.Bundle
import com.elkhouly.commons.extensions.viewBinding
import com.elkhouly.commons.helpers.NavigationIcon
import com.elkhouly.dialer.adapters.ConferenceCallsAdapter
import com.elkhouly.dialer.databinding.ActivityConferenceBinding
import com.elkhouly.dialer.helpers.CallManager

class ConferenceActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityConferenceBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(conferenceList))
            setupMaterialScrollListener(binding.conferenceList, binding.conferenceAppbar)
            conferenceList.adapter = ConferenceCallsAdapter(this@ConferenceActivity, conferenceList, ArrayList(CallManager.getConferenceCalls())) {}
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.conferenceAppbar, NavigationIcon.Arrow)
    }
}
