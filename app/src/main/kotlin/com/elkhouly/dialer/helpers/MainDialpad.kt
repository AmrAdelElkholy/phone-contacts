package com.elkhouly.dialer.helpers

import android.annotation.SuppressLint
import android.os.Handler
import android.text.TextUtils
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.elkhouly.commons.extensions.applyColorFilter
import com.elkhouly.commons.extensions.beGone
import com.elkhouly.commons.extensions.beVisibleIf
import com.elkhouly.commons.extensions.getBottomNavigationBackgroundColor
import com.elkhouly.commons.extensions.getColoredDrawableWithColor
import com.elkhouly.commons.extensions.getContrastColor
import com.elkhouly.commons.extensions.getProperPrimaryColor
import com.elkhouly.commons.extensions.getProperTextColor
import com.elkhouly.commons.extensions.hasPermission
import com.elkhouly.commons.extensions.onTextChangeListener
import com.elkhouly.commons.extensions.performHapticFeedback
import com.elkhouly.commons.extensions.value
import com.elkhouly.commons.helpers.LOWER_ALPHA_INT
import com.elkhouly.commons.helpers.PERMISSION_READ_PHONE_STATE
import com.elkhouly.dialer.R
import com.elkhouly.dialer.activities.SimpleActivity
import com.elkhouly.dialer.databinding.ActivityMainBinding
import com.elkhouly.dialer.extensions.addCharacter
import com.elkhouly.dialer.extensions.boundingBox
import com.elkhouly.dialer.extensions.callContactWithSimWithConfirmationCheck
import com.elkhouly.dialer.extensions.config
import com.elkhouly.dialer.extensions.disableKeyboard
import com.elkhouly.dialer.extensions.getAvailableSIMCardLabels
import com.elkhouly.dialer.extensions.getKeyEvent
import com.elkhouly.dialer.extensions.startCallWithConfirmationCheck
import com.elkhouly.dialer.models.SpeedDial
import kotlin.math.roundToInt

/**
 * Dialpad shown under the call history on the main screen, with one call button per SIM.
 * Key handling mirrors DialpadActivity.
 */
class MainDialpad(
    private val activity: SimpleActivity,
    private val binding: ActivityMainBinding,
    private val onInputChanged: (String) -> Unit,
    private val getLastCalledNumber: () -> String?,
    private val onExpandedChanged: () -> Unit,
) {
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val pressedKeys = mutableSetOf<Char>()
    private val toneGeneratorHelper = ToneGeneratorHelper(activity, DIALPAD_TONE_LENGTH_MS)
    private var speedDialValues = ArrayList<SpeedDial>()

    var isExpanded = true
        private set

    val input: String
        get() = binding.mainDialpadInput.value

    init {
        setupKeys()
        binding.apply {
            mainDialpadInput.disableKeyboard()
            mainDialpadInput.onTextChangeListener {
                mainDialpadInput.beVisibleIf(it.isNotEmpty())
                onInputChanged(it)
            }
            mainDialpadClearChar.setOnClickListener { clearChar(it) }
            mainDialpadClearChar.setOnLongClickListener { clearInput(); true }
            mainDialpadHide.setOnClickListener { collapse() }
        }
    }

    fun onResume() {
        speedDialValues = activity.config.getSpeedDialValues()
        setupCallButtons()
        updateColors()
    }

    fun setVisible(visible: Boolean) {
        binding.mainDialpadPanel.beVisibleIf(visible && isExpanded)
    }

    fun expand() {
        isExpanded = true
        binding.mainDialpadPanel.isVisible = true
        onExpandedChanged()
    }

    fun collapse() {
        isExpanded = false
        binding.mainDialpadPanel.beGone()
        onExpandedChanged()
    }

    fun setInput(number: String) {
        binding.mainDialpadInput.apply {
            setText(number)
            setSelection(text.length)
        }
    }

    fun clearInput() {
        binding.mainDialpadInput.setText("")
    }

    private fun setupKeys() {
        binding.mainDialpadKeys.apply {
            if (activity.config.hideDialpadNumbers) {
                arrayOf(
                    dialpad1Holder, dialpad2Holder, dialpad3Holder, dialpad4Holder, dialpad5Holder,
                    dialpad6Holder, dialpad7Holder, dialpad8Holder, dialpad9Holder
                ).forEach { it.isVisible = false }
                dialpadPlusHolder.isVisible = true
                dialpad0Holder.visibility = View.INVISIBLE
            }

            arrayOf(
                dialpad0Holder, dialpad1Holder, dialpad2Holder, dialpad3Holder, dialpad4Holder,
                dialpad5Holder, dialpad6Holder, dialpad7Holder, dialpad8Holder, dialpad9Holder,
                dialpadPlusHolder, dialpadAsteriskHolder, dialpadHashtagHolder
            ).forEach {
                it.background = ResourcesCompat.getDrawable(activity.resources, R.drawable.pill_background, activity.theme)
                it.background?.alpha = LOWER_ALPHA_INT
            }

            setupCharClick(dialpad1Holder, '1')
            setupCharClick(dialpad2Holder, '2')
            setupCharClick(dialpad3Holder, '3')
            setupCharClick(dialpad4Holder, '4')
            setupCharClick(dialpad5Holder, '5')
            setupCharClick(dialpad6Holder, '6')
            setupCharClick(dialpad7Holder, '7')
            setupCharClick(dialpad8Holder, '8')
            setupCharClick(dialpad9Holder, '9')
            setupCharClick(dialpad0Holder, '0')
            setupCharClick(dialpadPlusHolder, '+', longClickable = false)
            setupCharClick(dialpadAsteriskHolder, '*', longClickable = false)
            setupCharClick(dialpadHashtagHolder, '#', longClickable = false)
        }
    }

    private fun setupCallButtons() {
        val holder = binding.mainDialpadCallButtons
        holder.removeAllViews()

        // one button per SIM, ordered like callContactWithSim picks them
        val sims = if (activity.hasPermission(PERMISSION_READ_PHONE_STATE)) {
            activity.getAvailableSIMCardLabels().sortedBy { it.id }
        } else {
            emptyList()
        }

        if (sims.size < 2) {
            holder.addView(createCallButton(activity.getString(com.elkhouly.commons.R.string.call)) { call(null) })
        } else {
            sims.take(2).forEachIndexed { index, sim ->
                val carrier = sim.label.removeSuffix(" (${sim.phoneNumber})")
                holder.addView(createCallButton("${index + 1} $carrier") { call(index) })
            }
        }
    }

    private fun createCallButton(label: String, onClick: () -> Unit): TextView {
        val primaryColor = activity.getProperPrimaryColor()
        val contentColor = primaryColor.getContrastColor()
        val margin = activity.resources.getDimensionPixelSize(com.elkhouly.commons.R.dimen.small_margin)
        return TextView(activity).apply {
            text = label
            gravity = Gravity.CENTER
            // carrier names can be long, allow two small lines before ellipsizing
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 0.9f)
            setTextColor(contentColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.resources.getDimension(com.elkhouly.commons.R.dimen.smaller_text_size))
            background = ResourcesCompat.getDrawable(activity.resources, R.drawable.pill_background, activity.theme)?.mutate()
            background.applyColorFilter(primaryColor)
            val icon = activity.resources.getColoredDrawableWithColor(R.drawable.ic_phone_vector, contentColor)
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            compoundDrawablePadding = margin
            setPaddingRelative(margin * 2, 0, margin, 0)
            layoutParams = LinearLayout.LayoutParams(
                0,
                activity.resources.getDimensionPixelSize(R.dimen.dialpad_sim_button_height),
                1f
            ).apply {
                marginStart = margin
                marginEnd = margin
            }
            setOnClickListener { onClick() }
        }
    }

    private fun call(simIndex: Int?) {
        val number = input
        if (number.isEmpty()) {
            // like most dialers, an empty call press brings back the last number
            getLastCalledNumber()?.let { binding.mainDialpadInput.setText(it) }
            return
        }

        if (simIndex == null) {
            activity.startCallWithConfirmationCheck(number, number)
        } else {
            activity.callContactWithSimWithConfirmationCheck(number, number, useMainSIM = simIndex == 0)
        }
        clearInput()
    }

    private fun updateColors() {
        val textColor = activity.getProperTextColor()
        binding.mainDialpadPanel.background?.mutate()?.applyColorFilter(activity.getBottomNavigationBackgroundColor())
        binding.mainDialpadInput.setTextColor(textColor)
        binding.mainDialpadClearChar.applyColorFilter(textColor)
        binding.mainDialpadHide.applyColorFilter(textColor)
    }

    private fun dialpadPressed(char: Char, view: View?) {
        binding.mainDialpadInput.addCharacter(char)
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearChar(view: View) {
        binding.mainDialpadInput.dispatchKeyEvent(binding.mainDialpadInput.getKeyEvent(KeyEvent.KEYCODE_DEL))
        maybePerformDialpadHapticFeedback(view)
    }

    private fun speedDial(id: Int): Boolean {
        if (input.length == 1) {
            val speedDial = speedDialValues.firstOrNull { it.id == id }
            if (speedDial?.isValid() == true) {
                activity.startCallWithConfirmationCheck(speedDial.number, speedDial.getName(activity))
                clearInput()
                return true
            }
        }
        return false
    }

    private fun startDialpadTone(char: Char) {
        if (activity.config.dialpadBeeps) {
            pressedKeys.add(char)
            toneGeneratorHelper.startTone(char)
        }
    }

    private fun stopDialpadTone(char: Char) {
        if (activity.config.dialpadBeeps) {
            if (!pressedKeys.remove(char)) return
            if (pressedKeys.isEmpty()) {
                toneGeneratorHelper.stopTone()
            } else {
                startDialpadTone(pressedKeys.last())
            }
        }
    }

    private fun maybePerformDialpadHapticFeedback(view: View?) {
        if (activity.config.dialpadVibration) {
            view?.performHapticFeedback()
        }
    }

    private fun performLongClick(view: View, char: Char) {
        if (char == '0') {
            clearChar(view)
            dialpadPressed('+', view)
        } else if (speedDial(char.digitToInt())) {
            stopDialpadTone(char)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCharClick(view: View, char: Char, longClickable: Boolean = true) {
        view.isClickable = true
        view.isLongClickable = true
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dialpadPressed(char, view)
                    startDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                        longPressHandler.postDelayed({ performLongClick(view, char) }, longPressTimeout)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val viewContainsTouchEvent = if (event.rawX.isNaN() || event.rawY.isNaN()) {
                        false
                    } else {
                        view.boundingBox.contains(event.rawX.roundToInt(), event.rawY.roundToInt())
                    }

                    if (!viewContainsTouchEvent) {
                        stopDialpadTone(char)
                        if (longClickable) {
                            longPressHandler.removeCallbacksAndMessages(null)
                        }
                    }
                }
            }
            false
        }
    }
}
