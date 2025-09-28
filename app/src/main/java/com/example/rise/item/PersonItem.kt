package com.example.rise.item

import android.content.Context
import com.example.rise.R
import com.example.rise.databinding.ItemPersonBinding
import com.example.rise.models.User
import com.xwray.groupie.viewbinding.BindableItem

class PersonItem(
    val person: User,
    val userId: String,
    private val context: Context
) : BindableItem<ItemPersonBinding>() {

    override fun bind(viewBinding: ItemPersonBinding, position: Int) {
        viewBinding.textViewName.text = person.name
        viewBinding.textViewBio.text = person.bio
        // TODO Implement Glide to load profile pictures when backend is ready
    }

    override fun getLayout() = R.layout.item_person

    override fun initializeViewBinding(view: android.view.View): ItemPersonBinding =
        ItemPersonBinding.bind(view)
}
