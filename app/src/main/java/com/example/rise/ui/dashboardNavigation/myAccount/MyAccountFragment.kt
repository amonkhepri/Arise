package com.example.rise.ui.dashboardNavigation.myAccount

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.example.rise.baseclasses.BaseFragment
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.databinding.FragmentMyAccountBinding
import com.example.rise.ui.dashboardNavigation.myAccount.signInActivity.SignInActivity
import com.example.rise.util.FirestoreUtil
import com.firebase.ui.auth.AuthUI

class MyAccountFragment : BaseFragment() {

    private val viewModel: MyAccountBaseViewModel by viewModels {
        koinViewModelFactory(MyAccountBaseViewModel::class)
    }

    private val RC_SELECT_IMAGE = 2
    private lateinit var selectedImageBytes: ByteArray
    private var pictureJustChanged = false
    private var _binding: FragmentMyAccountBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMyAccountBinding.inflate(inflater, container, false)

        binding.imageViewProfilePicture.setOnClickListener {
            val intent = Intent().apply {
                type = "image/*"
                action = Intent.ACTION_GET_CONTENT
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))
            }
            startActivityForResult(Intent.createChooser(intent, "Select Image"), RC_SELECT_IMAGE)
        }

        binding.btnSave.setOnClickListener {
            if (::selectedImageBytes.isInitialized) {
                // StorageUtil.uploadProfilePhoto(selectedImageBytes) {
                //     FirestoreUtil.updateCurrentUser(binding.editTextName.text.toString(),
                //         binding.editTextBio.text.toString(), imagePath)
                // }
            } else {
                FirestoreUtil.updateCurrentUser(
                    binding.editTextName.text.toString(),
                    binding.editTextBio.text.toString(),
                    null,
                )
            }
            Toast.makeText(requireContext(), "saving", Toast.LENGTH_SHORT).show()
        }

        binding.btnSignOut.setOnClickListener {
            val intent = Intent(context, SignInActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            AuthUI.getInstance()
                .signOut(requireContext())
                .addOnCompleteListener {
                    startActivity(intent)
                }
        }

        return binding.root
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RC_SELECT_IMAGE && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            // Image selection handling can be re-enabled when storage integration is restored.
        }
    }

    override fun onStart() {
        super.onStart()

        FirestoreUtil.getCurrentUser { user ->
            if (isVisible) {
                binding.editTextName.setText(user.name)
                binding.editTextBio.setText(user.bio)
                // if (!pictureJustChanged && user.profilePicturePath != null)
                //     GlideApp.with(this)
                //         .load(StorageUtil.pathToReference(user.profilePicturePath))
                //         .placeholder(R.drawable.ic_account_circle_black_24dp)
                //         .into(binding.imageViewProfilePicture)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
