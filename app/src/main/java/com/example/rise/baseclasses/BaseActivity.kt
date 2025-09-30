package com.example.rise.baseclasses

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelLazy
import kotlin.reflect.KClass
import org.koin.android.ext.android.getKoin
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier

abstract class BaseActivity<VM : BaseViewModel> : AppCompatActivity() {

    protected abstract val viewModelClass: KClass<VM>
    protected open val viewModelParameters: ParametersDefinition? = null
    protected open val viewModelQualifier: Qualifier? = null

    protected val viewModel: VM by viewModelDelegate()

    protected open fun provideViewModelFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (!modelClass.isAssignableFrom(viewModelClass.java)) {
                    throw IllegalArgumentException("Unknown ViewModel class: ${'$'}modelClass")
                }

                val viewModel: VM = getKoin().get(
                    clazz = viewModelClass,
                    qualifier = viewModelQualifier,
                    parameters = viewModelParameters,
                )

                @Suppress("UNCHECKED_CAST")
                return viewModel as T
            }
        }

    private fun viewModelDelegate(): ViewModelLazy<VM> =
        ViewModelLazy(
            viewModelClass,
            { viewModelStore },
            { provideViewModelFactory() },
            { defaultViewModelCreationExtras },
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel
    }
}
