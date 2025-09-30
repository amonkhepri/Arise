package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rise.baseclasses.BaseFragment
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.databinding.FragmentPeopleBinding
import com.example.rise.helpers.AppConstants
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatActivity
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest

class PeopleFragment : BaseFragment() {

    private val viewModel: PeopleViewModel by viewModels {
        koinViewModelFactory(PeopleViewModel::class)
    }

    private var _binding: FragmentPeopleBinding? = null
    private val binding get() = _binding!!

    private val peopleSection = Section()
    private val adapter = GroupAdapter<GroupieViewHolder>().apply { add(peopleSection) }
    private var peopleById: Map<String, com.example.rise.data.people.PersonSummary> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPeopleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeState()
        observeEvents()
        viewModel.start()
    }

    private fun setupRecyclerView() {
        binding.recyclerViewPeople.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PeopleFragment.adapter
        }
        adapter.setOnItemClickListener { item, _ ->
            val personItem = item as? com.example.rise.item.PersonItem ?: return@setOnItemClickListener
            val summary = peopleById[personItem.userId] ?: return@setOnItemClickListener
            viewModel.onPersonSelected(summary)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.uiState.collectLatest { state ->
                val items = state.people.map { summary ->
                    com.example.rise.item.PersonItem(
                        person = com.example.rise.models.User(
                            summary.name,
                            summary.bio,
                            summary.profilePicturePath,
                            mutableListOf(),
                        ),
                        userId = summary.id,
                        context = requireContext(),
                    )
                }
                peopleById = state.people.associateBy { it.id }
                peopleSection.update(items)
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.events.collect { event ->
                when (event) {
                    is PeopleViewModel.PeopleEvent.OpenChat -> openChat(event)
                }
            }
        }
    }

    private fun openChat(event: PeopleViewModel.PeopleEvent.OpenChat) {
        val intent = Intent(context, ChatActivity::class.java).apply {
            putExtra(AppConstants.USER_NAME, event.personName)
            putExtra(AppConstants.USER_ID, event.personId)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
