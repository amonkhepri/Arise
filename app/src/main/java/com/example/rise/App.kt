package com.example.rise

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.rise.data.alarm.ConfigReminderPreferences
import com.example.rise.data.alarm.ReminderPreferences
import com.example.rise.data.auth.AuthStateProvider
import com.example.rise.data.auth.FirebaseAuthStateProvider
import com.example.rise.data.auth.FirebaseSignInRepository
import com.example.rise.data.auth.SignInRepository
import com.example.rise.data.chat.ChatLocalCache
import com.example.rise.data.chat.ChatRepository
import com.example.rise.data.chat.FirestoreChatRepository
import com.example.rise.data.chat.SharedPrefsChatCache
import com.example.rise.data.dashboard.AlarmRepository
import com.example.rise.data.dashboard.FirestoreAlarmRepository
import com.example.rise.data.myaccount.FirebaseMyAccountRepository
import com.example.rise.data.myaccount.MyAccountRepository
import com.example.rise.data.people.FirestorePeopleRepository
import com.example.rise.data.people.PeopleRepository
import com.example.rise.helpers.Config
import com.example.rise.ui.SplashActivityViewModel
import com.example.rise.ui.alarm.ReminderViewModel
import com.example.rise.ui.dashboardNavigation.dashboard.DashboardViewModel
import com.example.rise.ui.dashboardNavigation.myAccount.MyAccountViewModel
import com.example.rise.ui.dashboardNavigation.myAccount.signInActivity.SignInViewModel
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatViewModel
import com.example.rise.ui.dashboardNavigation.people.peopleFragment.PeopleViewModel
import com.example.rise.ui.mainActivity.MainActivityViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import timber.log.Timber
import java.time.Clock
import kotlinx.coroutines.Dispatchers

class App: Application() {

    val appModule = module {
        single { FirebaseAuth.getInstance() }
        single { FirebaseFirestore.getInstance() }
        single { FirebaseMessaging.getInstance() }
        single { Config.newInstance(androidContext()) }

        single<AuthStateProvider> { FirebaseAuthStateProvider(get()) }
        single<SignInRepository> { FirebaseSignInRepository(get()) }
        single<ReminderPreferences> { ConfigReminderPreferences(get()) }

        single<ChatLocalCache> { SharedPrefsChatCache(androidContext()) }
        single<ChatRepository> {
            FirestoreChatRepository(
                firestore = get(),
                auth = get(),
                localCache = get(),
            )
        }
        single<PeopleRepository> { FirestorePeopleRepository(get(), get()) }
        single<AlarmRepository> { FirestoreAlarmRepository(get()) }
        single<MyAccountRepository> { FirebaseMyAccountRepository(get(), get(), Dispatchers.IO) }
        single { Clock.systemDefaultZone() }
        single<SignInViewModel.EmailValidator> { SignInViewModel.DefaultEmailValidator }

        viewModelOf(::SplashActivityViewModel)
        viewModelOf(::ReminderViewModel)
        viewModelOf(::MyAccountViewModel)
        viewModelOf(::DashboardViewModel)
        viewModelOf(::ChatViewModel)
        viewModelOf(::PeopleViewModel)
        viewModelOf(::MainActivityViewModel)
        viewModelOf(::SignInViewModel)
    }

    override fun onCreate() {
        super.onCreate()

        // Force dark mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        Timber.plant(Timber.DebugTree())

        FirebaseApp.initializeApp(this)

        startKoin {
            //Koin android logger
            androidLogger()
            // declare used Android context
            androidContext(this@App)
            allowOverride(true)
            // declare modules
            modules(listOf(appModule))
        }
    }
}
