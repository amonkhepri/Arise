package com.example.rise.item

import com.example.rise.databinding.ItemTextMessageBinding
import com.example.rise.models.TextMessage
import com.xwray.groupie.Item

class TextMessageItem(
    override val message: TextMessage
) : MessageItem(message) {

    override fun bind(viewBinding: ItemTextMessageBinding, position: Int) {
        viewBinding.textViewMessageText.text = message.text
        super.bind(viewBinding, position)
    }

    override fun isSameAs(other: Item<*>): Boolean = other is TextMessageItem && message == other.message

    override fun equals(other: Any?): Boolean = other is TextMessageItem && message == other.message

    override fun hashCode(): Int = message.hashCode()
}
