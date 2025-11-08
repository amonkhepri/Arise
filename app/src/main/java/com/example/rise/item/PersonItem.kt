package com.example.rise.item

import com.example.rise.R
import com.example.rise.databinding.ItemPersonBinding
import com.example.rise.data.people.PersonSummary
import com.example.rise.ui.common.toDisplayColor
import com.example.rise.ui.common.toDisplayText
import com.xwray.groupie.viewbinding.BindableItem

//TODO Stop using groupie, replace with Jetpack Compose
class PersonItem(
    val summary: PersonSummary,
) : BindableItem<ItemPersonBinding>() {

    override fun bind(viewBinding: ItemPersonBinding, position: Int) {
        viewBinding.textViewName.text = summary.name
        viewBinding.textViewBio.text = summary.bio
        val context = viewBinding.root.context
        viewBinding.textViewPresence.text = summary.presence.toDisplayText(context)
        viewBinding.textViewPresence.setTextColor(summary.presence.toDisplayColor(context))
        // TODO Implement Glide to load profile pictures when backend is ready
    }

    override fun getLayout() = R.layout.item_person

    override fun initializeViewBinding(view: android.view.View): ItemPersonBinding =
        ItemPersonBinding.bind(view)
}
