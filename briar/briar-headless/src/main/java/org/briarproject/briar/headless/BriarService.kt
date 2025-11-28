package org.briarproject.briar.headless

import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.output.TermUi.echo
import org.briarproject.bramble.api.account.AccountManager
import org.briarproject.bramble.api.crypto.DecryptionException
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_WEAK
import org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH
import org.briarproject.bramble.api.lifecycle.LifecycleManager
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

interface BriarService {
    fun start()
    fun stop()
}

@Immutable
@Singleton
internal class BriarServiceImpl
@Inject
constructor(
    private val accountManager: AccountManager,
    private val lifecycleManager: LifecycleManager,
    private val passwordStrengthEstimator: PasswordStrengthEstimator
) : BriarService {

    private val nickname: String = System.getenv("BRIAR_NICKNAME") ?: "RiseUser"
    private val password: String = System.getenv("BRIAR_PASSWORD") ?: "rise-pass"

    override fun start() {
        if (!accountManager.accountExists()) {
            createAccount(nickname, password)
        } else {
            try {
                accountManager.signIn(password)
            } catch (e: DecryptionException) {
                echo("Error: Password invalid; falling back to account recreation")
                accountManager.deleteAccount()
                createAccount(nickname, password)
            }
        }
        val dbKey = accountManager.databaseKey ?: throw AssertionError()
        lifecycleManager.startServices(dbKey)
        lifecycleManager.waitForStartup()
    }

    override fun stop() {
        lifecycleManager.stopServices()
        lifecycleManager.waitForShutdown()
    }

    private fun createAccount(nickname: String, password: String) {
        if (nickname.length > MAX_AUTHOR_NAME_LENGTH)
            throw UsageError("Please choose a shorter nickname!")
        if (passwordStrengthEstimator.estimateStrength(password) < QUITE_WEAK)
            throw UsageError("Please enter a stronger password!")
        accountManager.createAccount(nickname, password)
    }

}
