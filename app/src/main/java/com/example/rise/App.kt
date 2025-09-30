package com.example.rise

import android.app.Application
import com.example.rise.data.auth.AuthStateProvider
import com.example.rise.data.auth.FirebaseAuthStateProvider
import com.example.rise.data.auth.FirebaseUiSignInIntentProvider
import com.example.rise.data.auth.SignInIntentProvider
import com.example.rise.data.chat.ChatRepository
import com.example.rise.data.chat.FirestoreChatRepository
import com.example.rise.data.dashboard.AlarmRepository
import com.example.rise.data.dashboard.FirestoreAlarmRepository
import com.example.rise.data.people.FirestorePeopleRepository
import com.example.rise.data.people.PeopleRepository
import com.example.rise.ui.SplashActivityViewModel
import com.example.rise.ui.dashboardNavigation.dashboard.DashboardViewModel
import com.example.rise.ui.dashboardNavigation.myAccount.MyAccountBaseViewModel
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatViewModel
import com.example.rise.ui.dashboardNavigation.people.peopleFragment.PeopleViewModel
import com.example.rise.ui.mainActivity.MainActivityViewModel
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import timber.log.Timber

class App: Application() {

    val appModule = module {
        single { FirebaseAuth.getInstance() }
        single { FirebaseFirestore.getInstance() }
        single { AuthUI.getInstance() }

        single<AuthStateProvider> { FirebaseAuthStateProvider(get()) }
        single<SignInIntentProvider> { FirebaseUiSignInIntentProvider(get()) }

        single<ChatRepository> { FirestoreChatRepository(get(), get()) }
        single<PeopleRepository> { FirestorePeopleRepository(get(), get()) }
        single<AlarmRepository> { FirestoreAlarmRepository(get()) }

        viewModel { SplashActivityViewModel(get()) }
        viewModel { MyAccountBaseViewModel() }
        viewModel { DashboardViewModel(get(), get()) }
        viewModel { ChatViewModel(get()) }
        viewModel { PeopleViewModel(get()) }
        viewModel { MainActivityViewModel(get(), get()) }
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
