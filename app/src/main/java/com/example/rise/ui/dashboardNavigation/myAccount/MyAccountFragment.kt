package com.example.rise.ui.dashboardNavigation.myAccount

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.rise.baseclasses.BaseFragment
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.databinding.FragmentMyAccountBinding
import com.example.rise.ui.dashboardNavigation.myAccount.signInActivity.SignInActivity
import kotlinx.coroutines.launch

class MyAccountFragment : BaseFragment() {

    private val viewModel: MyAccountBaseViewModel by viewModels {
        koinViewModelFactory(MyAccountBaseViewModel::class)
    }

    private val RC_SELECT_IMAGE = 2
    private var _binding: FragmentMyAccountBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMyAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imageViewProfilePicture.setOnClickListener {
            val intent = Intent().apply {
                type = "image/*"
                action = Intent.ACTION_GET_CONTENT
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))
            }
            startActivityForResult(Intent.createChooser(intent, "Select Image"), RC_SELECT_IMAGE)
        }

        binding.btnSave.setOnClickListener {
            viewModel.updateProfile(
                name = binding.editTextName.text.toString(),
                bio = binding.editTextBio.text.toString(),
            )
        }

        binding.btnSignOut.setOnClickListener {
            viewModel.signOut()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectUiState() }
                launch { collectEvents() }
            }
        }

        viewModel.loadProfile()
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            if (!binding.editTextName.isFocused && binding.editTextName.text.toString() != state.name) {
                binding.editTextName.setText(state.name)
            }
            if (!binding.editTextBio.isFocused && binding.editTextBio.text.toString() != state.bio) {
                binding.editTextBio.setText(state.bio)
            }
            if (state.errorMessage != null) {
                Toast.makeText(requireContext(), state.errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun collectEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is MyAccountBaseViewModel.Event.ShowMessage -> {
                    Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                }

                MyAccountBaseViewModel.Event.NavigateToSignIn -> {
                    val intent = Intent(requireContext(), SignInActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
