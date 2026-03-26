package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rise.R
import com.example.rise.baseclasses.BaseFragment
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.databinding.FragmentPeopleBinding
import com.example.rise.helpers.AppConstants
import com.example.rise.item.PersonItem
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatActivity
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatLaunchContract
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal data class AddByRawBriarLinkPromptCopy(
    val title: String,
    val message: String,
    val hint: String,
    val confirmLabel: String,
)

internal sealed interface PeopleInviteUiAction {
    data class ShareRawBriarLink(val chooserIntent: Intent) : PeopleInviteUiAction
    data class PromptAddByLink(val promptCopy: AddByRawBriarLinkPromptCopy) : PeopleInviteUiAction
    data class OpenAddedContactChat(val launchContract: ChatLaunchContract) : PeopleInviteUiAction
    data class ShowMessage(val message: String) : PeopleInviteUiAction
}

internal fun resolvePeopleInviteUiAction(
    event: PeopleViewModel.PeopleEvent,
    chooserTitle: String,
    promptCopy: AddByRawBriarLinkPromptCopy,
): PeopleInviteUiAction? = when (event) {
    is PeopleViewModel.PeopleEvent.OpenChat -> null
    is PeopleViewModel.PeopleEvent.ShareMyRawBriarLink -> {
        val (rawLink) = event
        PeopleInviteUiAction.ShareRawBriarLink(
            createShareRawBriarLinkChooser(rawLink = rawLink, chooserTitle = chooserTitle),
        )
    }
    PeopleViewModel.PeopleEvent.PromptAddByLink -> PeopleInviteUiAction.PromptAddByLink(promptCopy)
    is PeopleViewModel.PeopleEvent.LaunchChatFromAddedLink -> {
        val (launchContract) = event
        PeopleInviteUiAction.OpenAddedContactChat(launchContract)
    }
    is PeopleViewModel.PeopleEvent.ShowMessage -> {
        val (message) = event
        PeopleInviteUiAction.ShowMessage(message)
    }
    else -> null
}

internal fun createShareRawBriarLinkChooser(
    rawLink: String,
    chooserTitle: String,
): Intent {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, rawLink)
    }
    return Intent.createChooser(shareIntent, chooserTitle)
}

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
        setupInviteActions()
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
            val personItem =
                item as? PersonItem ?: return@setOnItemClickListener
            val summary = peopleById[personItem.summary.id] ?: return@setOnItemClickListener
            viewModel.onPersonSelected(summary)
        }
    }

    private fun setupInviteActions() {
        binding.buttonShareRawBriarLink.setOnClickListener {
            viewModel.onShareMyRawBriarLinkRequested()
        }
        binding.buttonAddByRawBriarLink.setOnClickListener {
            viewModel.onAddByLinkRequested()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.uiState.collectLatest { state ->
                val items = state.people.map { summary ->
                    PersonItem(
                        summary = summary,
                    )
                }
                peopleById = state.people.associateBy { it.id }
                peopleSection.update(items)
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is PeopleViewModel.PeopleEvent.OpenChat -> openChat(event)
                        else -> {
                            val inviteAction = resolvePeopleInviteUiAction(
                                event = event,
                                chooserTitle = getString(R.string.people_share_raw_briar_link_chooser_title),
                                promptCopy = createAddByLinkPromptCopy(),
                            ) ?: return@collect
                            handleInviteAction(inviteAction)
                        }
                    }
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

    private fun openChat(launchContract: ChatLaunchContract) {
        startActivity(ChatActivity.createLaunchIntent(requireContext(), launchContract))
    }

    private fun handleInviteAction(action: PeopleInviteUiAction) {
        when (action) {
            is PeopleInviteUiAction.ShareRawBriarLink -> startActivity(action.chooserIntent)
            is PeopleInviteUiAction.PromptAddByLink -> showAddByLinkPrompt(action.promptCopy)
            is PeopleInviteUiAction.OpenAddedContactChat -> openChat(action.launchContract)
            is PeopleInviteUiAction.ShowMessage -> showMessage(action.message)
        }
    }

    private fun createAddByLinkPromptCopy(): AddByRawBriarLinkPromptCopy {
        return AddByRawBriarLinkPromptCopy(
            title = getString(R.string.people_add_by_raw_briar_link_title),
            message = getString(R.string.people_add_by_raw_briar_link_message),
            hint = getString(R.string.people_add_by_raw_briar_link_hint),
            confirmLabel = getString(R.string.people_add_by_raw_briar_link_confirm),
        )
    }

    private fun showAddByLinkPrompt(promptCopy: AddByRawBriarLinkPromptCopy) {
        val input = EditText(requireContext()).apply {
            hint = promptCopy.hint
            setSingleLine()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(promptCopy.title)
            .setMessage(promptCopy.message)
            .setView(input)
            .setPositiveButton(promptCopy.confirmLabel) { _, _ ->
                viewModel.onRawBriarLinkSubmitted(input.text?.toString().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
