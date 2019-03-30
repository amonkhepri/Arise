package com.example.rise.ui

import android.content.Context

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.rise.data.Alarm
import kotlinx.android.synthetic.main.fragment_alarm_item.view.*
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot




/**
 * [RecyclerView.Adapter] that can display a [DummyItem] and makes a call to the
 * specified [OnListFragmentInteractionListener].
 * TODO: Replace the implementation with code for your data type.
 */
open class MyAlarmRecyclerViewAdapter(
  //  private val mListener: OnAlarmSelectedListener,
    mQuery: Query,
    //TODO adding context for test purpouses, delete later on when unnecessary
val context: Context
) : FirestoreAdapterBase<MyAlarmRecyclerViewAdapter.ViewHolder>(mQuery) {

    interface OnAlarmSelectedListener {
        fun onAlarmSelected(restaurant: DocumentSnapshot)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(com.example.rise.R.layout.fragment_alarm_item, parent, false)
        itemCount
        return ViewHolder(view)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

            holder.bind(getSnapshot(position)/*, mListener*/)

    }


    inner class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {

        fun bind(
            snapshot: DocumentSnapshot/*,
            listener: OnAlarmSelectedListener*/
        ) {
            val alarm:Alarm= snapshot.toObject(Alarm::class.java)!!




            Toast.makeText(context,"test",Toast.LENGTH_LONG).show()
            mView.time_remaining.text = snapshot.get("time").toString()
            mView.time_set.text = snapshot.get("time").toString()
        }

    }
}
