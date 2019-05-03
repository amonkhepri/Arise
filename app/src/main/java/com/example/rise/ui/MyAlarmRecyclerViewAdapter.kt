package com.example.rise.ui

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.rise.extensions.scheduleNextAlarm
import com.example.rise.models.Alarm
import com.google.firebase.firestore.*
import kotlinx.android.synthetic.main.recycler_alarm_item.view.*
import java.util.*
import kotlin.concurrent.schedule


open class MyAlarmRecyclerViewAdapter(
  //  private val mListener: OnAlarmSelectedListener,
    mQuery: Query,
    context: Context

) : FirestoreAdapterBase<MyAlarmRecyclerViewAdapter.ViewHolder>(mQuery,context) {

    lateinit var alarm:Alarm

    interface OnAlarmSelectedListener {
        fun onAlarmSelected(restaurant: DocumentSnapshot)
    }



    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getSnapshot(position)/*, mListener*/)
    }

    override fun onDataChanged() {
        super.onDataChanged()

    }




    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(com.example.rise.R.layout.recycler_alarm_item, parent, false)
        itemCount
        return ViewHolder(view)
    }



    inner class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {

        fun bind(
            snapshot: DocumentSnapshot
        ) {


            alarm = snapshot.toObject(Alarm::class.java)!!

            if(alarm.timeInMinutes.rem(60)>=10) {


                mView.time_remaining.text =
                    alarm.timeInMinutes.div(60).toString() + " : " + alarm.timeInMinutes.rem(60).toString()
                mView.time_set.text = snapshot.data!!["myAlarm"].toString()
            }
            else{
                mView.time_remaining.text =
                    alarm.timeInMinutes.div(60).toString() + " : " +" 0" + alarm.timeInMinutes.rem(60).toString()
                mView.time_set.text = snapshot.data!!["myAlarm"].toString()
            }
            mView.deleteButton.setOnClickListener{ v->
                FirebaseFirestore.getInstance().document("sampleData/user").collection("alarms").document(snapshot.id)
                    .delete()
                    .addOnSuccessListener { /*Log.d(TAG, "DocumentSnapshot successfully deleted!")*/ }
                    .addOnFailureListener { /*e -> Log.w(TAG, "Error deleting document", e) */}}
        }

    }
}
