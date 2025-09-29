package com.example.rise.baseclasses

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.LazyThreadSafetyMode
import kotlin.reflect.KClass
import org.koin.android.ext.android.getKoinScope
import org.koin.androidx.viewmodel.factory.KoinViewModelFactory
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier

@OptIn(KoinInternalApi::class)
abstract class BaseFragment<VM : BaseViewModel> : Fragment() {

    protected abstract val viewModelClass: KClass<VM>
    protected open val viewModelQualifier: Qualifier? = null
    protected open val viewModelParameters: ParametersDefinition? = null
    protected open fun defaultViewModelExtras(): CreationExtras = defaultViewModelCreationExtras

    protected open fun provideViewModelFactory(): ViewModelProvider.Factory {
        return KoinViewModelFactory(
            viewModelClass,
            getKoinScope(),
            viewModelQualifier,
            viewModelParameters
        )
    }

    protected val viewModel: VM by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(
            viewModelStore,
            provideViewModelFactory()
        ).get(viewModelClass.java)
    }
}
