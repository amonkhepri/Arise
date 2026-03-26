package com.example.rise.ui.dashboardNavigation.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rise.baseclasses.BaseFragment
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.databinding.FragmentDashboardBinding
import com.example.rise.extensions.scheduleNextMessage
import com.example.rise.helpers.CHAT_CHANNEL
import com.example.rise.helpers.MESSAGE_CONTENT
import com.example.rise.models.TextMessage
import com.example.rise.ui.dashboardNavigation.dashboard.recyclerview.MyFireStoreAlarmRecyclerViewAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch

class DashboardFragment : BaseFragment() {

    private val viewModel: DashboardViewModel by viewModels {
        koinViewModelFactory(DashboardViewModel::class)
    }

    var byBottomNavigation: Boolean = false

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var alarmAdapter: MyFireStoreAlarmRecyclerViewAdapter? = null
    private var currentQuery: Query? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FirebaseFirestore.setLoggingEnabled(true)
        setupRecyclerView()
        observeState()
        observeEvents()
        initialiseViewModel()
    }

    override fun onStart() {
        super.onStart()
        alarmAdapter?.startListening()
    }

    override fun onStop() {
        super.onStop()
        alarmAdapter?.stopListening()
    }

    private fun setupRecyclerView() {
        binding.alarmList.layoutManager = LinearLayoutManager(context)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.alarmQuery?.let { updateAdapter(it.asFirestoreQuery(), state.activeUserId) }
                    state.errorMessage?.let { showError(it) }
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is DashboardViewModel.DashboardEvent.ScheduleDelayedMessage ->
                            context?.scheduleNextMessage(event.alarm)
                    }
                }
            }
        }
    }

    private fun initialiseViewModel() {
        val intent = activity?.intent
        val explicitUserId = intent?.getStringExtra("UsrID")
        val chatChannel = intent?.getStringExtra("ChannelId") ?: intent?.getStringExtra(CHAT_CHANNEL)
        val message = intent?.getParcelableExtra<TextMessage>(MESSAGE_CONTENT)
        viewModel.initialise(byBottomNavigation, explicitUserId, chatChannel, message)
    }

    private fun updateAdapter(query: Query, otherUserId: String?) {
        if (alarmAdapter == null) {
            alarmAdapter = object : MyFireStoreAlarmRecyclerViewAdapter(query, requireContext()) {
                override fun onError(e: FirebaseFirestoreException) {
                    Snackbar.make(binding.root, "Error: check logs for info.", Snackbar.LENGTH_LONG).show()
                }
            }
            binding.alarmList.adapter = alarmAdapter
            alarmAdapter?.otherUsrId = otherUserId
            alarmAdapter?.startListening()
        } else if (currentQuery != query) {
            alarmAdapter?.setQuery(query)
            alarmAdapter?.otherUsrId = otherUserId
        } else {
            alarmAdapter?.otherUsrId = otherUserId
        }
        currentQuery = query
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        alarmAdapter?.stopListening()
        alarmAdapter = null
        currentQuery = null
        _binding = null
    }
}
