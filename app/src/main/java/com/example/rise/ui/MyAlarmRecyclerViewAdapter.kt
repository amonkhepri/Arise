package com.example.rise.ui

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.rise.data.Alarm


import com.example.rise.ui.AlarmListFragment.OnListFragmentInteractionListener
import kotlinx.android.synthetic.main.fragment_alarm_item.view.*
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot




/**
 * [RecyclerView.Adapter] that can display a [DummyItem] and makes a call to the
 * specified [OnListFragmentInteractionListener].
 * TODO: Replace the implementation with code for your data type.
 */
class MyAlarmRecyclerViewAdapter(


    private val mListener: OnAlarmSelectedListener?,
    private val mQuery: Query?

) : FirestoreAdapterBase<MyAlarmRecyclerViewAdapter.ViewHolder>(mQuery) {




    private val mOnClickListener: View.OnClickListener

    interface OnAlarmSelectedListener {

        fun onAlarmSelected(restaurant: DocumentSnapshot)

    }

    init {
        mOnClickListener = View.OnClickListener { v ->
            val item = v.tag as Alarm
            // Notify the active callbacks interface (the activity, if the fragment is attached to
            // one) that an item has been selected.
            mListener?.onListFragmentInteraction(item)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(com.example.rise.R.layout.fragment_alarm_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getSnapshot(position), mListener)
    }



    inner class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {


        fun bind(
            snapshot: DocumentSnapshot,
            listener: OnAlarmSelectedListener
        ) {
           val alarm: Alarm? =snapshot.toObject(Alarm::class.java)
            mView.time_remaining.text = alarm?.timeRemaining.toString()
            mView.time_set.text = alarm?.timeSet.toString()

        }

    }
}
