package com.example.rise.baseclasses

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlin.reflect.KClass
import org.koin.androidx.viewmodel.ext.android.viewModelForClass
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier

abstract class BaseActivity<VM : BaseViewModel> : AppCompatActivity() {

    protected abstract val viewModelClass: KClass<VM>
    protected open val viewModelParameters: ParametersDefinition? = null
    protected open val viewModelQualifier: Qualifier? = null

    private val viewModelDelegate: Lazy<VM> = viewModelForClass(
        clazz = viewModelClass,
        qualifier = viewModelQualifier,
        parameters = viewModelParameters,
    )

    protected val viewModel: VM by viewModelDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel
    }
}
