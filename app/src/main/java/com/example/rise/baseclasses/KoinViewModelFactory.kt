package com.example.rise.baseclasses

import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.koin.android.ext.android.getKoin
import org.koin.core.Koin
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import kotlin.reflect.KClass

fun <VM : ViewModel> ComponentActivity.koinViewModelFactory(
    viewModelClass: KClass<VM>,
    qualifier: Qualifier? = null,
    parameters: ParametersDefinition? = null,
): ViewModelProvider.Factory =
    createKoinViewModelFactory(
        viewModelClass = viewModelClass,
        qualifier = qualifier,
        parameters = parameters,
        koinProvider = { getKoin() },
    )

fun <VM : ViewModel> Fragment.koinViewModelFactory(
    viewModelClass: KClass<VM>,
    qualifier: Qualifier? = null,
    parameters: ParametersDefinition? = null,
): ViewModelProvider.Factory =
    createKoinViewModelFactory(
        viewModelClass = viewModelClass,
        qualifier = qualifier,
        parameters = parameters,
        koinProvider = { getKoin() },
    )

private fun <VM : ViewModel> createKoinViewModelFactory(
    viewModelClass: KClass<VM>,
    qualifier: Qualifier?,
    parameters: ParametersDefinition?,
    koinProvider: () -> Koin,
): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(viewModelClass.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
            }

            val viewModel: VM = koinProvider().get(
                clazz = viewModelClass,
                qualifier = qualifier,
                parameters = parameters,
            )

            @Suppress("UNCHECKED_CAST")
            return viewModel as T
        }
    }
