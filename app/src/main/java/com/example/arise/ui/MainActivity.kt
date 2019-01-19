package com.example.arise.ui

import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->

        when (item.itemId) {
            com.example.arise.R.id.navigation_home -> {
                message.setText(com.example.arise.R.string.title_home)
                return@OnNavigationItemSelectedListener true
            }
            com.example.arise.R.id.navigation_dashboard -> {
                message.setText(com.example.arise.R.string.title_dashboard)
                return@OnNavigationItemSelectedListener true
            }
            com.example.arise.R.id.navigation_notifications -> {
                message.setText(com.example.arise.R.string.title_notifications)
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.arise.R.layout.activity_main)

        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)
    }
}
