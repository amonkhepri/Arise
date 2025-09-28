package com.example.rise.ui.dashboardNavigation.dashboard.recyclerview

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.rise.databinding.RecyclerAlarmItemBinding
import com.example.rise.models.Alarm
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date

open class MyFireStoreAlarmRecyclerViewAdapter(
    mQuery: Query,
    context: Context
) : FirestoreAdapterBase<MyFireStoreAlarmRecyclerViewAdapter.ViewHolder>(mQuery, context) {

    lateinit var alarm: Alarm
    open var otherUsrId: String? = null

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getSnapshot(position))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RecyclerAlarmItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    inner class ViewHolder(private val binding: RecyclerAlarmItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(snapshot: DocumentSnapshot) {
            alarm = snapshot.toObject(Alarm::class.java)!!

            val formattedTime = getDateTime(alarm.timeInMiliseconds)
            binding.setTime.text = formattedTime
            binding.displayName.text = alarm.userName

            binding.deleteButton.setOnClickListener {
                if (otherUsrId != null) {
                    FirebaseFirestore.getInstance().collection("/users")
                        .document(otherUsrId!!).collection("/alarms").document(snapshot.id)
                        .delete()
                        .addOnSuccessListener { Timber.d("DocumentSnapshot successfully deleted!") }
                        .addOnFailureListener { Timber.w("Error deleting document") }
                } else {
                    FirebaseFirestore.getInstance()
                        .collection("/users")
                        .document(FirebaseAuth.getInstance().currentUser?.uid.toString())
                        .collection("/alarms")
                        .document(snapshot.id)
                        .delete()
                        .addOnSuccessListener { Timber.d("DocumentSnapshot successfully deleted!") }
                        .addOnFailureListener { Timber.w("Error deleting document") }
                }
            }
        }
    }

    private fun getDateTime(millis: Long): String {
        return try {
            val sdf = SimpleDateFormat("HH:mm")
            val netDate = Date(millis)
            sdf.format(netDate)
        } catch (e: Exception) {
            e.toString()
        }
    }
}
