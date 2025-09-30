package com.example.rise.ui.alarm

import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.view.MotionEvent
import android.view.animation.AnimationUtils
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.rise.R
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.databinding.ActivityReminderBinding
import com.example.rise.extensions.beGone
import com.example.rise.extensions.getAdjustedPrimaryColor
import com.example.rise.extensions.getFormattedTime
import com.example.rise.extensions.performHapticFeedback
import com.example.rise.extensions.showErrorToast
import com.example.rise.extensions.showOverLockscreen
import com.example.rise.extensions.showPickSecondsDialog
import com.example.rise.extensions.setupAlarmClock
import com.example.rise.helpers.ALARM_ID
import com.example.rise.helpers.MESSAGE_CONTENT
import com.example.rise.helpers.getPassedSeconds
import com.example.rise.helpers.getColoredDrawableWithColor
import com.example.rise.models.Alarm
import kotlinx.coroutines.launch

class ReminderActivity : AppCompatActivity() {

    private val swipeGuideFadeHandler = Handler(Looper.getMainLooper())
    private var didVibrate = false
    private var mediaPlayer: MediaPlayer? = null
    private var dragDownX = 0f
    private var hasConfiguredButtons = false
    private var hasStartedAudio = false
    private lateinit var binding: ActivityReminderBinding

    private val viewModel: ReminderViewModel by viewModels {
        koinViewModelFactory(ReminderViewModel::class)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        showOverLockscreen()

        setupObservers()

        val alarm = extractAlarm(intent)
        val isAlarmReminder = alarm != null || intent.getIntExtra(ALARM_ID, -1) != -1
        viewModel.initialize(
            ReminderViewModel.Args(
                alarm = alarm,
                isAlarmReminder = isAlarmReminder,
                alarmLabelFallback = getString(R.string.alarm),
                timerLabel = getString(R.string.timer),
                timerExpiredText = getString(R.string.time_expired),
                formattedTimeProvider = {
                    applicationContext.getFormattedTime(getPassedSeconds(), false, false)
                },
            ),
        )
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (state.isInitialized) {
                            renderState(state)
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }

    private fun renderState(state: ReminderViewModel.UiState) {
        binding.reminderTitle.text = state.title
        binding.reminderText.text = state.message

        if (!hasConfiguredButtons) {
            if (state.isAlarmReminder) {
                setupAlarmButtons()
            } else {
                setupTimerButtons()
            }
            hasConfiguredButtons = true
        }

        if (!hasStartedAudio && state.soundUri != null) {
            setupAudio(state)
        }
    }

    private fun setupAlarmButtons() {
        binding.reminderStop.beGone()
        binding.reminderDraggableBackground.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.pulsing_animation)
        )

        var minDragX = 0f
        var maxDragX = 0f
        var initialDraggableX = 0f

        binding.reminderDismiss.post {
            minDragX = binding.reminderSnooze.left.toFloat()
            maxDragX = binding.reminderDismiss.left.toFloat()
            initialDraggableX = binding.reminderDraggable.left.toFloat()
        }

        binding.reminderDraggable.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDownX = event.x
                    binding.reminderDraggableBackground.animate().alpha(0f)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragDownX = 0f
                    if (!didVibrate) {
                        binding.reminderDraggable.animate().x(initialDraggableX).withEndAction {
                            binding.reminderDraggableBackground.animate().alpha(0.2f)
                        }

                        binding.reminderGuide.animate().alpha(1f).start()
                        swipeGuideFadeHandler.removeCallbacksAndMessages(null)
                        swipeGuideFadeHandler.postDelayed({
                            binding.reminderGuide.animate().alpha(0f).start()
                        }, 2000L)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = event.rawX - dragDownX
                    binding.reminderDraggable.x = minOf(maxDragX, maxOf(minDragX, newX))
                    when {
                        binding.reminderDraggable.x >= maxDragX - 50f -> {
                            if (!didVibrate) {
                                binding.reminderDraggable.performHapticFeedback()
                                didVibrate = true
                                viewModel.onDismissRequested()
                            }
                        }
                        binding.reminderDraggable.x <= minDragX + 50f -> {
                            if (!didVibrate) {
                                binding.reminderDraggable.performHapticFeedback()
                                didVibrate = true
                                viewModel.onSnoozeRequested()
                            }
                        }
                        else -> {
                            didVibrate = false
                        }
                    }
                }
            }
            true
        }
    }

    private fun setupTimerButtons() {
        binding.reminderStop.background = resources.getColoredDrawableWithColor(
            R.drawable.circle_background_filled,
            getAdjustedPrimaryColor()
        )
        arrayOf(
            binding.reminderSnooze,
            binding.reminderDraggableBackground,
            binding.reminderDraggable,
            binding.reminderDismiss
        ).forEach { it.beGone() }

        binding.reminderStop.setOnClickListener {
            viewModel.onDismissRequested()
        }
    }

    private fun setupAudio(state: ReminderViewModel.UiState) {
        val uriString = state.soundUri ?: return
        val soundUri = Uri.parse(uriString)
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_ALARM)
                setDataSource(this@ReminderActivity, soundUri)
                setVolume(state.currentVolume, state.currentVolume)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
        hasStartedAudio = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.onNewIntent()
    }

    override fun onDestroy() {
        super.onDestroy()
        swipeGuideFadeHandler.removeCallbacksAndMessages(null)
        destroyPlayer()
    }

    private fun destroyPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        hasStartedAudio = false
    }

    private fun finishActivity() {
        destroyPlayer()
        finish()
        overridePendingTransition(0, 0)
    }

    private fun handleEvent(event: ReminderViewModel.Event) {
        when (event) {
            ReminderViewModel.Event.Finish -> finishActivity()
            is ReminderViewModel.Event.ScheduleSnooze -> {
                setupAlarmClock(event.alarm, event.seconds)
                finishActivity()
            }
            is ReminderViewModel.Event.ShowSnoozePicker -> {
                showPickSecondsDialog(
                    event.defaultSeconds,
                    isSnoozePicker = true,
                    cancelCallback = { viewModel.onSnoozePickerCancelled() },
                ) { seconds ->
                    viewModel.onSnoozeDurationSelected(seconds)
                }
            }
            is ReminderViewModel.Event.UpdateVolume -> {
                mediaPlayer?.setVolume(event.volume, event.volume)
            }
        }
    }

    private fun extractAlarm(intent: Intent?): Alarm? {
        if (intent == null) return null
        val bundleAlarm = intent.getBundleExtra(MESSAGE_CONTENT)?.getParcelableCompat("alarm", Alarm::class.java)
        val directAlarm = intent.getParcelableExtraCompat("alarm", Alarm::class.java)
        return bundleAlarm ?: directAlarm
    }

    private fun <T : Parcelable> Intent.getParcelableExtraCompat(key: String, clazz: Class<T>): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, clazz)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }

    private fun <T : Parcelable> Bundle.getParcelableCompat(key: String, clazz: Class<T>): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelable(key, clazz)
        } else {
            @Suppress("DEPRECATION")
            getParcelable(key)
        }
}
