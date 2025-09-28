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
import com.example.rise.util.FirestoreUtil
import com.google.firebase.firestore.ListenerRegistration
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import com.xwray.groupie.OnItemClickListener
import com.xwray.groupie.Section
import org.koin.android.ext.android.get

class PeopleFragment : BaseFragment<PeopleViewModel>() {

    private lateinit var userListenerRegistration: ListenerRegistration
    private var shouldInitRecyclerView = true
    private lateinit var peopleSection: Section
    private var _binding: FragmentPeopleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPeopleBinding.inflate(inflater, container, false)
        userListenerRegistration = FirestoreUtil.addUsersListener(requireActivity(), this::updateRecyclerView)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        FirestoreUtil.removeListener(userListenerRegistration)
        shouldInitRecyclerView = true
        _binding = null
    }

    private fun updateRecyclerView(items: List<Item<*>>) {

        fun init() {
            binding.recyclerViewPeople.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = GroupAdapter<GroupieViewHolder>().apply {
                    peopleSection = Section(items)
                    add(peopleSection)
                    setOnItemClickListener(onItemClick)
                }
            }
            shouldInitRecyclerView = false
        }

        fun updateItems() = peopleSection.update(items)

        if (shouldInitRecyclerView) {
            init()
        } else {
            updateItems()
        }
    }

    private val onItemClick = OnItemClickListener { item, _ ->
        if (item is PersonItem) {
            val intent = Intent(context, ChatActivity::class.java).apply {
                putExtra(AppConstants.USER_NAME, item.person.name)
                putExtra(AppConstants.USER_ID, item.userId)
            }
            startActivity(intent)
        }
    }

    override fun createViewModel() {
        viewModel = get()
    }
}
