package com.example.rise.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.android.synthetic.main.recycler_alarm_item.view.*


open class MyAlarmRecyclerViewAdapter(
  //  private val mListener: OnAlarmSelectedListener,
    mQuery: Query

) : FirestoreAdapterBase<MyAlarmRecyclerViewAdapter.ViewHolder>(mQuery) {

    interface OnAlarmSelectedListener {
        fun onAlarmSelected(restaurant: DocumentSnapshot)
    }

    lateinit var mSnapshot: DocumentSnapshot


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(com.example.rise.R.layout.recycler_alarm_item, parent, false)
        itemCount


        return ViewHolder(view)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

            holder.bind(getSnapshot(position)/*, mListener*/)

    }


    inner class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {

        fun bind(
            snapshot: DocumentSnapshot
        ) {
            mSnapshot=snapshot


            //   val alarm:Alarm= snapshot.toObject(Alarm::class.java)!!
            mView.time_remaining.text = snapshot.data!!["myAlarm"].toString()
            mView.time_set.text = snapshot.data!!["myAlarm"].toString()


            mView.imageButton.setOnClickListener{v->
                FirebaseFirestore.getInstance().document("sampleData/user").collection("alarms").document(snapshot.id)
                    .delete()
                    .addOnSuccessListener { /*Log.d(TAG, "DocumentSnapshot successfully deleted!")*/ }
                    .addOnFailureListener { /*e -> Log.w(TAG, "Error deleting document", e) */}}
        }

    }
}
