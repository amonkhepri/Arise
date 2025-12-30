package com.example.rise.briar.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Default implementation that lazily spins up the embedded Briar runtime when requested.
 * It keeps operations serialized behind a [Mutex] to avoid racing mode flips from the
 * flag watcher.
 */
class BriarRuntimeManagerImpl(
    private val briarRuntimeEnvironment: BriarRuntimeEnvironment,
    private val componentFactory: BriarComponentFactory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BriarRuntimeManager {

    private val mutex = Mutex()
    private var handle: BriarRuntimeHandle? = null

    private val _status = MutableStateFlow(BriarRuntimeStatus.stopped)
    private val _diagnostics = MutableSharedFlow<BriarRuntimeEvent>(replay = 5, extraBufferCapacity = 16)
    private val _chatGateway = MutableStateFlow<BriarChatGateway>(NoOpBriarChatGateway)
    private val _contactService = MutableStateFlow<BriarContactService>(NoOpBriarContactService)

    override val status: StateFlow<BriarRuntimeStatus> = _status.asStateFlow()
    override val diagnostics: SharedFlow<BriarRuntimeEvent> = _diagnostics.asSharedFlow()
    override val chatGateway: StateFlow<BriarChatGateway> = _chatGateway.asStateFlow()
    override val contactService: StateFlow<BriarContactService> = _contactService.asStateFlow()

    override suspend fun ensureStarted() {
        mutex.withLock {
            if (_status.value.phase == BriarRuntimePhase.RUNNING) return

            val config = briarRuntimeEnvironment.createConfig()
            val startingStatus = BriarRuntimeStatus(
                phase = BriarRuntimePhase.STARTING,
                storageDir = config.storageDir
            )
            updateStatus(startingStatus)
            val newHandle = try {
                withContext(ioDispatcher) {
                    componentFactory.create(config)
                }
            } catch (t: Throwable) {
                val failureStatus = BriarRuntimeStatus(
                    phase = BriarRuntimePhase.FAILED,
                    storageDir = config.storageDir,
                    lastError = t
                )
                updateStatus(failureStatus)
                log("Failed to initialise Briar runtime: ${t.message ?: t::class.java.simpleName}")
                throw t
            }

            handle = newHandle

            _chatGateway.value = newHandle.chatGateway
            _contactService.value = newHandle.contactService
            // If a database key already exists, start services immediately so identity is available.
            if (newHandle.accountManager.hasDatabaseKey()) {
                val servicesStarted = newHandle.startServicesWithCurrentKey()
                if (servicesStarted) {
                    val identityExists = newHandle.hasIdentity
                    if (identityExists) {
                        newHandle.markIdentityReady()
                    }
                    _diagnostics.tryEmit(BriarRuntimeEvent.IdentityStatus(identityExists))
                } else {
                    _diagnostics.tryEmit(BriarRuntimeEvent.IdentityStatus(newHandle.hasIdentity))
                }
            } else {
                _diagnostics.tryEmit(BriarRuntimeEvent.IdentityStatus(newHandle.hasIdentity))
            }

            updateStatus(
                BriarRuntimeStatus(
                    phase = BriarRuntimePhase.RUNNING,
                    storageDir = config.storageDir
                )
            )
            log("Briar runtime started")
        }
    }

    override suspend fun createAccount(name: String, password: String): Boolean {
        ensureStarted()
        return mutex.withLock {
            val activeHandle = handle ?: return@withLock false
            val created: Boolean = if(activeHandle.accountManager.accountExists()) {
                val msg = "Account already exists. Please sign in."
                log(msg)
                throw IllegalStateException(msg)
            }else{
                withContext(ioDispatcher) { activeHandle.accountManager.createAccount(name, password) }
            }
            if (!created) return@withLock false
            val servicesStarted = activeHandle.startServicesWithCurrentKey()
            if (!servicesStarted) {
                log("Failed to start services after account creation")
                return@withLock false
            }
            if (!waitForIdentity(activeHandle)) {
                log("Briar identity not available after account creation")
                return@withLock false
            }
            activeHandle.markIdentityReady()
            _diagnostics.tryEmit(BriarRuntimeEvent.IdentityStatus(true))
            true
        }
    }

    override suspend fun signIn(password: String): Boolean {
        ensureStarted()
        return mutex.withLock {
            val activeHandle = handle ?: return@withLock false
            return@withLock try {
                withContext(ioDispatcher) { activeHandle.signIn(password) }
                val servicesStarted = activeHandle.startServicesWithCurrentKey()
                if (!servicesStarted) {
                    log("Failed to start services after sign-in")
                    return@withLock false
                }
                if (waitForIdentity(activeHandle)) {
                    log("Briar identity available after sign-in")
                    activeHandle.markIdentityReady()
                    _diagnostics.tryEmit(BriarRuntimeEvent.IdentityStatus(true))
                    true
                } else {
                    log("Briar identity not available after sign-in")
                    false
                }
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun stop() {
        mutex.withLock {
            val activeHandle = handle ?: run {
                if (_status.value.phase != BriarRuntimePhase.STOPPED) {
                    updateStatus(BriarRuntimeStatus.stopped)
                }
                return
            }

            updateStatus(
                _status.value.copy(
                    phase = BriarRuntimePhase.STOPPING
                )
            )
            log("Stopping Briar runtime")

            val stopResult = runCatching {
                withContext(ioDispatcher) {
                    activeHandle.close()
                }
            }

            handle = null
            _chatGateway.value = NoOpBriarChatGateway
            _contactService.value = NoOpBriarContactService

            stopResult.onFailure { error ->
                val failureStatus = BriarRuntimeStatus(
                    phase = BriarRuntimePhase.FAILED,
                    storageDir = _status.value.storageDir,
                    lastError = error
                )
                updateStatus(failureStatus)
                log("Briar runtime stop failed: ${error.message ?: error::class.java.simpleName}")
                return
            }

            updateStatus(BriarRuntimeStatus.stopped)
            log("Briar runtime stopped")
        }
    }

    private fun updateStatus(status: BriarRuntimeStatus) {
        _status.value = status
        _diagnostics.tryEmit(BriarRuntimeEvent.StatusChanged(status))
    }

    private fun log(message: String) {
        _diagnostics.tryEmit(BriarRuntimeEvent.Message(message))
    }

    private suspend fun waitForIdentity(handle: BriarRuntimeHandle): Boolean {
        repeat(IDENTITY_WAIT_ATTEMPTS) {
            if (handle.hasIdentity) return true
            delay(IDENTITY_WAIT_DELAY_MS)
        }
        return handle.hasIdentity
    }

    companion object {
        private const val IDENTITY_WAIT_ATTEMPTS = 25
        private const val IDENTITY_WAIT_DELAY_MS = 200L
    }
}
