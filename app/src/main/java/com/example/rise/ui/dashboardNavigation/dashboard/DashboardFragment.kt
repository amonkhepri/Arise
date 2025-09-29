package com.example.rise.ui.dashboardNavigation.dashboard

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rise.baseclasses.BaseFragment
import com.example.rise.databinding.FragmentDashboardBinding
import com.example.rise.extensions.scheduleNextAlarm
import com.example.rise.models.Alarm
import com.example.rise.models.TextMessage
import com.example.rise.ui.dashboardNavigation.dashboard.recyclerview.MyFireStoreAlarmRecyclerViewAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import java.util.Calendar

class DashboardFragment : BaseFragment<DashboardViewModel>() {

    override val viewModelClass = DashboardViewModel::class

    var byBottomNavigation: Boolean = false

    private var adapter: MyFireStoreAlarmRecyclerViewAdapter? = null

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onStart() {
        super.onStart()
        adapter?.startListening()
    }

    override fun onStop() {
        super.onStop()
        adapter?.stopListening()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.floatingActionButton.setOnClickListener {
            val millis = buildSelectedTimeMillis()
            viewModel.createAlarm(millis)
        }

        val userId: String? = activity?.intent?.getStringExtra("UsrID")
        val chatChannel: String? = activity?.intent?.getStringExtra("ChannelId")
        val message: TextMessage? = activity?.intent?.getParcelableExtra("Message")

        observeViewModel()
        viewModel.initialize(userId, chatChannel, message, byBottomNavigation)
    }

    private fun buildSelectedTimeMillis(): Long {
        val datePicker = DatePicker(requireContext())
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, datePicker.dayOfMonth)
            set(Calendar.MONTH, datePicker.month)
            set(Calendar.YEAR, datePicker.year)
            set(Calendar.HOUR_OF_DAY, binding.simpleTimePicker.hour)
            set(Calendar.MINUTE, binding.simpleTimePicker.minute)
        }.timeInMillis
    }

    private fun observeViewModel() {
        viewModel.alarmQuery.observe(viewLifecycleOwner) { alarmQuery ->
            val query = (alarmQuery as? FirestoreAlarmQuery)?.unwrap()
            if (query != null) {
                initRecyclerView(query)
            }
        }

        viewModel.otherUserId.observe(viewLifecycleOwner) { otherUserId ->
            adapter?.otherUsrId = otherUserId
        }

        viewModel.events.observe(viewLifecycleOwner) { event ->
            when (val content = event.getContentIfNotHandled()) {
                is DashboardViewModel.DashboardEvent.ScheduleAlarm -> scheduleAlarm(content.alarm)
                null -> Unit
            }
        }
    }

    private fun initRecyclerView(query: Query) {
        if (adapter == null) {
            adapter = object : MyFireStoreAlarmRecyclerViewAdapter(query, requireContext()) {
                override fun onError(e: FirebaseFirestoreException) = Snackbar.make(
                    binding.root,
                    "Error: check logs for info.",
                    Snackbar.LENGTH_LONG
                ).show()
            }
            binding.alarmList.layoutManager = LinearLayoutManager(context)
            binding.alarmList.adapter = adapter
        } else {
            adapter?.setQuery(query)
        }
        adapter?.startListening()
    }

    private fun scheduleAlarm(alarm: Alarm) {
        context?.scheduleNextAlarm(alarm, true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter?.stopListening()
        adapter = null
        _binding = null
    }
}
