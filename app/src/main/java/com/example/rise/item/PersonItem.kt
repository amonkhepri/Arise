package com.example.rise.item

import android.content.Context
import com.example.rise.R
import com.example.rise.models.User

import com.xwray.groupie.kotlinandroidextensions.Item
import com.xwray.groupie.kotlinandroidextensions.ViewHolder
//TODO watch out for possible issues
import kotlinx.android.synthetic.main.item_person.*
import kotlinx.android.synthetic.main.item_person.view.*


class PersonItem(val person: User,
                 val userId: String,
                 private val context: Context)
    : Item() {

    override fun bind(viewHolder: ViewHolder, position: Int) {
        viewHolder.root.textView_name.text = person.name
        viewHolder.root.textView_bio.text = person.bio

        //TODO Implement Glide
        /* if (person.profilePicturePath != null)
            GlideApp.with(context)
                    .load(StorageUtil.pathToReference(person.profilePicturePath))
                    .placeholder(R.drawable.ic_account_circle_black_24dp)
                    .into(viewHolder.root.imageView_profile_picture)*/
    }

    override fun getLayout() = R.layout.item_person
}
