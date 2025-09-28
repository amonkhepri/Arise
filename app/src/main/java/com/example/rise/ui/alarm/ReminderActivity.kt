package com.example.rise.ui.alarm

import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.example.rise.R
import com.example.rise.databinding.ActivityReminderBinding
import com.example.rise.extensions.beGone
import com.example.rise.extensions.config
import com.example.rise.extensions.getAdjustedPrimaryColor
import com.example.rise.extensions.performHapticFeedback
import com.example.rise.extensions.scheduleNextAlarm
import com.example.rise.extensions.showErrorToast
import com.example.rise.extensions.showOverLockscreen
import com.example.rise.extensions.showPickSecondsDialog
import com.example.rise.extensions.setupAlarmClock
import com.example.rise.helpers.ALARM_ID
import com.example.rise.helpers.MINUTE_SECONDS
import com.example.rise.helpers.getFormattedTime
import com.example.rise.helpers.getPassedSeconds
import com.example.rise.helpers.getColoredDrawableWithColor
import com.example.rise.models.Alarm

class ReminderActivity : AppCompatActivity() {

    private val increaseVolumeDelay = 3000L
    private val increaseVolumeHandler = Handler()
    private val maxReminderDurationHandler = Handler()
    private val swipeGuideFadeHandler = Handler()
    private var isAlarmReminder = false
    private var didVibrate = false
    private var alarm: Alarm? = null
    private var mediaPlayer: MediaPlayer? = null
    private var lastVolumeValue = 0.1f
    private var dragDownX = 0f
    private lateinit var binding: ActivityReminderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        showOverLockscreen()

        val id = intent.getIntExtra(ALARM_ID, -1)
        isAlarmReminder = id != -1 && alarm != null

        val label = if (isAlarmReminder && alarm != null) {
            if (alarm!!.label.isEmpty()) {
                getString(R.string.alarm)
            } else {
                alarm!!.label
            }
        } else {
            getString(R.string.timer)
        }

        binding.reminderTitle.text = label
        binding.reminderText.text = if (isAlarmReminder) {
            getFormattedTime(getPassedSeconds(), false, false)
        } else {
            getString(R.string.time_expired)
        }

        val maxDuration = if (isAlarmReminder) {
            config.alarmMaxReminderSecs
        } else {
            config.timerMaxReminderSecs
        }
        maxReminderDurationHandler.postDelayed({
            finishActivity()
        }, maxDuration * 1000L)

        setupButtons()
        setupAudio()
    }

    private fun setupButtons() {
        if (isAlarmReminder) {
            setupAlarmButtons()
        } else {
            setupTimerButtons()
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
                                finishActivity()
                            }
                        }
                        binding.reminderDraggable.x <= minDragX + 50f -> {
                            if (!didVibrate) {
                                binding.reminderDraggable.performHapticFeedback()
                                didVibrate = true
                                snoozeAlarm()
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
            finishActivity()
        }
    }

    private fun setupAudio() {
        if (!isAlarmReminder || !config.increaseVolumeGradually) {
            lastVolumeValue = 1f
        }

        val soundUri = Uri.parse(alarm?.soundUri ?: config.timerSoundUri)
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_ALARM)
                setDataSource(this@ReminderActivity, soundUri)
                setVolume(lastVolumeValue, lastVolumeValue)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }

        if (config.increaseVolumeGradually) {
            scheduleVolumeIncrease()
        }
    }

    private fun scheduleVolumeIncrease() {
        increaseVolumeHandler.postDelayed({
            lastVolumeValue = minOf(lastVolumeValue + 0.1f, 1f)
            mediaPlayer?.setVolume(lastVolumeValue, lastVolumeValue)
            scheduleVolumeIncrease()
        }, increaseVolumeDelay)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        finishActivity()
    }

    override fun onDestroy() {
        super.onDestroy()
        increaseVolumeHandler.removeCallbacksAndMessages(null)
        maxReminderDurationHandler.removeCallbacksAndMessages(null)
        swipeGuideFadeHandler.removeCallbacksAndMessages(null)
        destroyPlayer()
    }

    private fun destroyPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun snoozeAlarm() {
        destroyPlayer()
        val currentAlarm = alarm ?: return
        if (config.useSameSnooze) {
            setupAlarmClock(currentAlarm, config.snoozeTime * MINUTE_SECONDS)
            finishActivity()
        } else {
            showPickSecondsDialog(
                config.snoozeTime * MINUTE_SECONDS,
                isSnoozePicker = true,
                cancelCallback = { finishActivity() }
            ) { seconds ->
                config.snoozeTime = seconds / MINUTE_SECONDS
                setupAlarmClock(currentAlarm, seconds)
                finishActivity()
            }
        }
    }

    private fun finishActivity() {
        destroyPlayer()
        finish()
        overridePendingTransition(0, 0)
    }
}
