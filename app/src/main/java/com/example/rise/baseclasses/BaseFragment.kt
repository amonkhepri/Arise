package com.example.rise.baseclasses

import android.os.Bundle
import androidx.fragment.app.Fragment

abstract class BaseFragment<viewModel: BaseViewModel>: Fragment() {
    lateinit var viewModel: viewModel
    abstract fun createViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createViewModel()
    }
}