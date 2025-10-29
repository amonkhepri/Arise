package com.example.rise

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.rise.ui.alarm.data.ConfigReminderPreferences
import com.example.rise.ui.alarm.data.ReminderPreferences
import com.example.rise.data.auth.AuthStateProvider
import com.example.rise.data.auth.DefaultTelegramAuthRepository
import com.example.rise.data.auth.FirebaseAuthStateProvider
import com.example.rise.data.auth.FirebaseSignInRepository
import com.example.rise.data.auth.SignInRepository
import com.example.rise.data.auth.TelegramAuthRepository
import com.example.rise.data.chat.ChatLocalCache
import com.example.rise.data.chat.ChatRepository
import com.example.rise.data.chat.TransportBackedChatRepository
import com.example.rise.data.chat.RoomChatCache
import com.example.rise.data.dashboard.AlarmRepository
import com.example.rise.data.dashboard.FirestoreAlarmRepository
import com.example.rise.data.myaccount.FirebaseMyAccountRepository
import com.example.rise.data.myaccount.MyAccountRepository
import com.example.rise.data.people.FirestorePeopleRepository
import com.example.rise.data.people.PeopleRepository
import com.example.rise.helpers.Config
import com.example.rise.briar.BriarRuntimeEnvironmentImpl
import com.example.rise.briar.runtime.BriarComponentFactory
import com.example.rise.briar.runtime.BriarRuntimeEnvironment
import com.example.rise.briar.runtime.BriarRuntimeManager
import com.example.rise.briar.runtime.BriarComponentFactoryImpl
import com.example.rise.briar.runtime.BriarRuntimeManagerImpl
import com.example.rise.debug.FeatureFlagsViewModel
import com.example.rise.featureflags.DataStoreTransportModeProviderImpl
import com.example.rise.featureflags.TransportModeProvider
import com.example.rise.featureflags.TelegramAuthFlagProvider
import com.example.rise.featureflags.transportModeDataStore
import com.example.rise.auth.AuthenticationService
import com.example.rise.auth.FirebaseAuthenticationService
import com.example.rise.transport.connectors.FirestoreConnector
import com.example.rise.transport.router.ConnectorRegistry
import com.example.rise.transport.router.DefaultConnectorRegistry
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.IdentityRegistryImpl
import com.example.rise.transport.router.IdentityRegistryStore
import com.example.rise.transport.router.SharedPrefsIdentityRegistryStore
import com.example.rise.transport.router.TransportConnector
import com.example.rise.transport.router.TransportRouter
import com.example.rise.transport.router.TransportRouterImpl
import com.example.rise.transport.store.ConversationDatabase
import com.example.rise.transport.store.ConversationStore
import com.example.rise.transport.store.ChatCacheDao
import com.example.rise.transport.store.RoomConversationStore
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.TransportRuntimeBridgeImpl
import com.example.rise.data.firestore.UserRemoteDataSource
import com.example.rise.data.firestore.FirebaseUserRemoteDataSource
import com.example.rise.data.firestore.ChatRemoteDataSource
import com.example.rise.data.firestore.FirebaseChatRemoteDataSource
import com.example.rise.ui.SplashActivityViewModel
import com.example.rise.ui.alarm.ReminderViewModel
import com.example.rise.ui.dashboardNavigation.dashboard.DashboardViewModel
import com.example.rise.ui.dashboardNavigation.myAccount.MyAccountViewModel
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatViewModel
import com.example.rise.ui.dashboardNavigation.people.peopleFragment.PeopleViewModel
import com.example.rise.ui.mainActivity.MainActivityViewModel
import com.example.rise.ui.signInActivity.SignInViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.OkHttpClient
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
        single<DataStore<Preferences>> { androidContext().transportModeDataStore }
        single<TransportModeProvider> { DataStoreTransportModeProviderImpl(get()) }
        single { TelegramAuthFlagProvider(get()) }
        single<BriarRuntimeEnvironment> { BriarRuntimeEnvironmentImpl(androidContext()) }
        single<BriarComponentFactory> { BriarComponentFactoryImpl() }
        single<BriarRuntimeManager> { BriarRuntimeManagerImpl(environment = get(), componentFactory = get()) }
        single<TransportRuntimeBridge> { TransportRuntimeBridgeImpl(get(), get()) }

        single<AuthStateProvider> { FirebaseAuthStateProvider(get()) }
        single<SignInRepository> { FirebaseSignInRepository(get(), get()) }
        single { OkHttpClient.Builder().build() }
        single<TelegramAuthRepository> { DefaultTelegramAuthRepository(get()) }
        single<ReminderPreferences> { ConfigReminderPreferences(get()) }
        single<AuthenticationService> { FirebaseAuthenticationService(get()) }
        single<UserRemoteDataSource> { FirebaseUserRemoteDataSource(get()) }
        single<ChatRemoteDataSource> { FirebaseChatRemoteDataSource(get()) }

        single { ConversationDatabase.build(androidContext()) }
        single { get<ConversationDatabase>().conversationDao() }
        single<ChatCacheDao> { get<ConversationDatabase>().chatCacheDao() }
        single<ChatLocalCache> { RoomChatCache(get()) }
        single<ConversationStore> {
            RoomConversationStore(
                dao = get(),
            )
        }
        single<IdentityRegistryStore> { SharedPrefsIdentityRegistryStore(androidContext()) }
        single<IdentityRegistry> { IdentityRegistryImpl(get()) }
        single<TransportConnector> {
            FirestoreConnector(
                authService = get(),
                chatRemoteDataSource = get(),
                transportBridge = get(),
                localCache = get(),
            )
        }
        single<ConnectorRegistry> {
            DefaultConnectorRegistry(connectors = setOf(get<TransportConnector>()))
        }
        single<TransportRouter> {
            TransportRouterImpl(
                transportBridge = get(),
                connectorRegistry = get(),
                conversationStore = get(),
                identityRegistry = get(),
            )
        }
        single<ChatRepository> { TransportBackedChatRepository(get()) }
        single<PeopleRepository> { FirestorePeopleRepository(get(), get(), get()) }
        single<AlarmRepository> { FirestoreAlarmRepository(get(), get()) }
        single<MyAccountRepository> {
            FirebaseMyAccountRepository(
                authService = get(),
                userRemoteDataSource = get(),
                ioDispatcher = Dispatchers.IO,
                transportBridge = get(),
            )
        }
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
        viewModelOf(::FeatureFlagsViewModel)
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
