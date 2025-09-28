package com.example.rise.ui.dialogs

import android.os.Build
import android.view.WindowManager
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.rise.R
import com.example.rise.databinding.DialogCustomIntervalPickerBinding
import com.example.rise.extensions.beVisibleIf
import com.example.rise.extensions.hideKeyboard
import com.example.rise.extensions.onGlobalLayout
import com.example.rise.extensions.setupDialogStuff
import com.example.rise.helpers.DAY_SECONDS
import com.example.rise.helpers.HOUR_SECONDS
import com.example.rise.helpers.MINUTE_SECONDS

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CustomIntervalPickerDialog(
    private val activity: AppCompatActivity,
    private val selectedSeconds: Int = 0,
    private val showSeconds: Boolean = false,
    private val callback: (minutes: Int) -> Unit
) {
    private val binding = DialogCustomIntervalPickerBinding.inflate(activity.layoutInflater)
    private val dialog: AlertDialog

    init {
        binding.apply {
            dialogRadioSeconds.beVisibleIf(showSeconds)
            when {
                selectedSeconds == 0 -> dialogRadioView.check(R.id.dialog_radio_minutes)
                selectedSeconds % DAY_SECONDS == 0 -> {
                    dialogRadioView.check(R.id.dialog_radio_days)
                    dialogCustomIntervalValue.setText((selectedSeconds / DAY_SECONDS).toString())
                }
                selectedSeconds % HOUR_SECONDS == 0 -> {
                    dialogRadioView.check(R.id.dialog_radio_hours)
                    dialogCustomIntervalValue.setText((selectedSeconds / HOUR_SECONDS).toString())
                }
                selectedSeconds % MINUTE_SECONDS == 0 -> {
                    dialogRadioView.check(R.id.dialog_radio_minutes)
                    dialogCustomIntervalValue.setText((selectedSeconds / MINUTE_SECONDS).toString())
                }
                else -> {
                    dialogRadioView.check(R.id.dialog_radio_seconds)
                    dialogCustomIntervalValue.setText(selectedSeconds.toString())
                }
            }
        }

        dialog = AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok) { _, _ -> confirmReminder() }
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(binding.root, this) {
                    showKeyboard(binding.dialogCustomIntervalValue)
                }
            }
    }

    private fun AlertDialog.showKeyboard(editText: EditText) {
        window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        editText.apply {
            requestFocus()
            onGlobalLayout {
                setSelection(text.toString().length)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun confirmReminder() {
        val valueText = binding.dialogCustomIntervalValue.text?.toString()?.trim().orEmpty()
        val multiplier = getMultiplier(binding.dialogRadioView.checkedRadioButtonId)
        val minutes = valueText.toIntOrNull() ?: 0
        callback(minutes * multiplier)
        activity.hideKeyboard()
        dialog.dismiss()
    }

    private fun getMultiplier(id: Int) = when (id) {
        R.id.dialog_radio_days -> DAY_SECONDS
        R.id.dialog_radio_hours -> HOUR_SECONDS
        R.id.dialog_radio_minutes -> MINUTE_SECONDS
        else -> 1
    }
}
