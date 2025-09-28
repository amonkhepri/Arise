package com.example.rise.item

import android.content.Context
import com.example.rise.databinding.ItemTextMessageBinding
import com.example.rise.models.TextMessage

class TextMessageItem(
    val message: TextMessage,
    private val context: Context
) : MessageItem(message) {

    override fun bind(viewBinding: ItemTextMessageBinding, position: Int) {
        viewBinding.textViewMessageText.text = message.text
        super.bind(viewBinding, position)
    }

    override fun isSameAs(other: com.xwray.groupie.Item<*>?): Boolean {
        if (other !is TextMessageItem) {
            return false
        }
        if (message != other.message) {
            return false
        }
        return true
    }

    override fun equals(other: Any?): Boolean {
        return isSameAs(other as? TextMessageItem)
    }

    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + context.hashCode()
        return result
    }
}
