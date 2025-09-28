package com.example.rise.ui.dialogs

import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.rise.R
import com.example.rise.databinding.DialogRadioGroupBinding
import com.example.rise.extensions.onGlobalLayout
import com.example.rise.extensions.setupDialogStuff
import com.example.rise.models.RadioItem

class RadioGroupDialog(
    private val activity: AppCompatActivity,
    private val items: ArrayList<RadioItem>,
    private val checkedItemId: Int = -1,
    private val titleId: Int = 0,
    showOKButton: Boolean = false,
    private val cancelCallback: (() -> Unit)? = null,
    private val callback: (newValue: Any) -> Unit
) {
    private val binding = DialogRadioGroupBinding.inflate(activity.layoutInflater)
    private val dialog: AlertDialog
    private var wasInit = false
    private var selectedItemId = -1

    init {
        binding.dialogRadioGroup.apply {
            for (i in 0 until items.size) {
                val radioButton = (activity.layoutInflater.inflate(R.layout.radio_button, null) as RadioButton).apply {
                    text = items[i].title
                    isChecked = items[i].id == checkedItemId
                    id = i
                    setOnClickListener { itemSelected(i) }
                }

                if (items[i].id == checkedItemId) {
                    selectedItemId = i
                }

                addView(radioButton, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }

        val builder = AlertDialog.Builder(activity).setOnCancelListener { cancelCallback?.invoke() }

        if (selectedItemId != -1 && showOKButton) {
            builder.setPositiveButton(R.string.ok) { _, _ -> itemSelected(selectedItemId) }
        }

        dialog = builder.create().apply {
            activity.setupDialogStuff(binding.root, this, titleId)
        }

        if (selectedItemId != -1) {
            binding.dialogRadioHolder.onGlobalLayout {
                binding.dialogRadioGroup.findViewById<View>(selectedItemId)?.let { selectedView ->
                    binding.dialogRadioHolder.scrollTo(0, selectedView.bottom - binding.dialogRadioHolder.height)
                }
            }
        }

        wasInit = true
    }

    private fun itemSelected(checkedId: Int) {
        if (wasInit) {
            callback(items[checkedId].value)
            dialog.dismiss()
        }
    }
}
