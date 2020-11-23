package com.example.rise

import android.app.Application
import android.provider.Contacts
import com.example.rise.ui.SplashActivityViewModel
import com.example.rise.ui.dashboardNavigation.dashboard.DashboardViewModel
import com.example.rise.ui.dashboardNavigation.myAccount.MyAccountBaseViewModel
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatViewModel
import com.example.rise.ui.dashboardNavigation.people.peopleFragment.PeopleViewModel
import com.example.rise.ui.mainActivity.MainActivityViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import timber.log.Timber

class App: Application() {

    val appModule = module {
        viewModel { SplashActivityViewModel() }
        viewModel { MyAccountBaseViewModel() }
        viewModel { DashboardViewModel()}
        viewModel { ChatViewModel() }
        viewModel { PeopleViewModel() }
        viewModel { MainActivityViewModel() }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

        startKoin {
            //Koin android logger
            androidLogger()
            // declare used Android context
            androidContext(this@App)
            // declare modules
            modules(listOf(appModule))
        }
    }
}