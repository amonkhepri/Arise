package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rise.baseclasses.BaseFragment
import com.example.rise.databinding.FragmentPeopleBinding
import com.example.rise.helpers.AppConstants
import com.example.rise.item.PersonItem
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatActivity
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section

class PeopleFragment : BaseFragment<PeopleViewModel>() {

    private var _binding: FragmentPeopleBinding? = null
    private val binding get() = _binding!!

    private var peopleSection: Section? = null

    override val viewModelClass = PeopleViewModel::class

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPeopleBinding.inflate(inflater, container, false)
        setupRecyclerView()
        observeViewModel()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.startListening()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopListening()
        _binding = null
        peopleSection = null
    }

    private fun setupRecyclerView() {
        val adapter = GroupAdapter<GroupieViewHolder>()
        peopleSection = Section(emptyList()).also(adapter::add)
        adapter.setOnItemClickListener { item, _ ->
            if (item is PersonItem) {
                viewModel.onPersonSelected(PersonRecord(item.userId, item.person))
            }
        }
        binding.recyclerViewPeople.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewPeople.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.people.observe(viewLifecycleOwner) { records ->
            val items = records.map { PersonItem(it.user, it.userId, requireContext()) }
            peopleSection?.update(items)
        }

        viewModel.events.observe(viewLifecycleOwner) { event ->
            when (val content = event.getContentIfNotHandled()) {
                is PeopleViewModel.PeopleEvent.OpenChat -> openChat(content)
                null -> Unit
            }
        }
    }

    private fun openChat(event: PeopleViewModel.PeopleEvent.OpenChat) {
        val intent = Intent(context, ChatActivity::class.java).apply {
            putExtra(AppConstants.USER_NAME, event.userName)
            putExtra(AppConstants.USER_ID, event.userId)
        }
        startActivity(intent)
    }
}
