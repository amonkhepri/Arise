package com.example.rise.ui.dashboardNavigation.dashboard

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rise.baseclasses.BaseFragment
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.databinding.FragmentDashboardBinding
import com.example.rise.extensions.scheduleNextAlarm
import com.example.rise.helpers.CHAT_CHANNEL
import com.example.rise.helpers.MESSAGE_CONTENT
import com.example.rise.models.TextMessage
import com.example.rise.ui.dashboardNavigation.dashboard.recyclerview.MyFireStoreAlarmRecyclerViewAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import java.util.Calendar
import kotlinx.coroutines.flow.collect

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
        setupFab()
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

    private fun setupFab() {
        binding.floatingActionButton.setOnClickListener {
            val timeInMillis = selectedTimeInMillis()
            viewModel.createAlarm(timeInMillis)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.uiState.collect { state ->
                state.alarmQuery?.let { updateAdapter(it, state.activeUserId) }
                state.errorMessage?.let { showError(it) }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.events.collect { event ->
                when (event) {
                    is DashboardViewModel.DashboardEvent.ScheduleAlarm ->
                        context?.scheduleNextAlarm(event.alarm, true)
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

    private fun selectedTimeInMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.set(Calendar.HOUR_OF_DAY, getSelectedHour())
        calendar.set(Calendar.MINUTE, getSelectedMinute())
        return calendar.timeInMillis
    }

    private fun getSelectedHour(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        binding.simpleTimePicker.hour
    } else {
        @Suppress("DEPRECATION")
        binding.simpleTimePicker.currentHour
    }

    private fun getSelectedMinute(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        binding.simpleTimePicker.minute
    } else {
        @Suppress("DEPRECATION")
        binding.simpleTimePicker.currentMinute
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
