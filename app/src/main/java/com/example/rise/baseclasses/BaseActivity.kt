package com.example.rise.baseclasses

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity


abstract class BaseActivity<viewModel: BaseViewModel>: AppCompatActivity() {
    lateinit var  viewModel: viewModel
    abstract fun createViewModel()

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        createViewModel()
    }
}