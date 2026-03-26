package com.example.rise.item

import com.example.rise.databinding.ItemTextMessageBinding
import com.example.rise.models.TextMessage
import com.xwray.groupie.Item

class TextMessageItem(
    override val message: TextMessage,
    override val currentUserId: String?,
) : MessageItem(message, currentUserId) {

    override fun bind(viewBinding: ItemTextMessageBinding, position: Int) {
        viewBinding.textViewMessageText.text = message.text
        super.bind(viewBinding, position)
    }

    override fun isSameAs(other: Item<*>): Boolean {
        return other is TextMessageItem &&
            message == other.message &&
            currentUserId == other.currentUserId
    }

    override fun equals(other: Any?): Boolean {
        return other is TextMessageItem &&
            message == other.message &&
            currentUserId == other.currentUserId
    }

    override fun hashCode(): Int = 31 * message.hashCode() + (currentUserId?.hashCode() ?: 0)
}
