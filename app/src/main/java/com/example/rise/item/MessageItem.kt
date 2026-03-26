package com.example.rise.item

import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import com.example.rise.R
import com.example.rise.databinding.ItemTextMessageBinding
import com.example.rise.models.Message
import com.xwray.groupie.viewbinding.BindableItem
import java.text.SimpleDateFormat

abstract class MessageItem(
    open val message: Message,
    open val currentUserId: String?,
) : BindableItem<ItemTextMessageBinding>() {

    override fun bind(viewBinding: ItemTextMessageBinding, position: Int) {
        setTimeText(viewBinding)
        setMessageRootGravity(viewBinding)
    }

    override fun getLayout() = R.layout.item_text_message

    override fun initializeViewBinding(view: View): ItemTextMessageBinding = ItemTextMessageBinding.bind(view)

    private fun setTimeText(viewBinding: ItemTextMessageBinding) {
        val dateFormat = SimpleDateFormat
            .getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT)
        viewBinding.textViewMessageTime.text = dateFormat.format(message.time)
    }

    private fun setMessageRootGravity(viewBinding: ItemTextMessageBinding) {
        val messageRoot = viewBinding.messageRoot
        val context = viewBinding.root.context
        if (isCurrentUserMessage(senderId = message.senderId, currentUserId = currentUserId)) {
            messageRoot.apply {
                setBackgroundResource(R.drawable.rect_round_white)
                val lParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.END)
                layoutParams = lParams
            }
            // Set text color for my messages
            val textColor = context.getColor(R.color.myMessageTextColor)
            viewBinding.textViewMessageText.setTextColor(textColor)
            viewBinding.textViewMessageTime.setTextColor(textColor)
        } else {
            messageRoot.apply {
                setBackgroundResource(R.drawable.rect_round_primary_color)
                val lParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.START)
                layoutParams = lParams
            }
            // Set text color for other messages
            val textColor = context.getColor(R.color.messageTextColor)
            viewBinding.textViewMessageText.setTextColor(textColor)
            viewBinding.textViewMessageTime.setTextColor(textColor)
        }
    }
}

internal fun isCurrentUserMessage(senderId: String, currentUserId: String?): Boolean {
    return senderId == currentUserId
}
