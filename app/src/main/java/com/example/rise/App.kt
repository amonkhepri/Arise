package com.example.rise

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.example.rise.auth.AuthStateProvider
import com.example.rise.auth.FirebaseAuthStateProvider
import com.example.rise.auth.FirebaseAuthUiSignInIntentProvider
import com.example.rise.auth.FirebaseUserSessionProvider
import com.example.rise.auth.SignInIntentProvider
import com.example.rise.auth.UserSessionProvider
import com.example.rise.ui.SplashActivityViewModel
import com.example.rise.ui.dashboardNavigation.dashboard.DashboardRepository
import com.example.rise.ui.dashboardNavigation.dashboard.DashboardViewModel
import com.example.rise.ui.dashboardNavigation.dashboard.FirestoreDashboardRepository
import com.example.rise.ui.dashboardNavigation.myAccount.MyAccountBaseViewModel
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatViewModel
import com.example.rise.ui.dashboardNavigation.people.peopleFragment.PeopleViewModel
import com.example.rise.ui.mainActivity.MainActivityViewModel
import com.example.rise.ui.dashboardNavigation.myAccount.signInActivity.SignInViewModel
import com.example.rise.ui.dashboardNavigation.people.chatActivity.FirestoreChatRepository
import com.example.rise.ui.dashboardNavigation.people.peopleFragment.FirestorePeopleRepository
import com.example.rise.ui.dashboardNavigation.people.peopleFragment.PeopleRepository
import com.firebase.ui.auth.AuthUI
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import timber.log.Timber

class App : Application() {

    val appModule = module {
        single { FirebaseAuth.getInstance() }
        single { AuthUI.getInstance() }
        single { FirebaseFirestore.getInstance() }
        factory<AuthStateProvider> { FirebaseAuthStateProvider(get()) }
        factory<SignInIntentProvider> { FirebaseAuthUiSignInIntentProvider(get()) }
        single<UserSessionProvider> { FirebaseUserSessionProvider(get()) }
        single<com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatRepository> { FirestoreChatRepository(get()) }
        single<PeopleRepository> { FirestorePeopleRepository(get()) }
        single<DashboardRepository> { FirestoreDashboardRepository(get(), get()) }
        viewModel { SplashActivityViewModel(get()) }
        viewModel { MyAccountBaseViewModel() }
        viewModel { DashboardViewModel(get(), get()) }
        viewModel { ChatViewModel(get()) }
        viewModel { PeopleViewModel(get()) }
        viewModel { MainActivityViewModel(get(), get()) }
        viewModel { SignInViewModel() }
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