
package com.example.rise.briar.runtime

import java.io.File

/**
 * Represents the high-level lifecycle state of the embedded Briar runtime.
 *
 * Stage 1 only wires the component in and out; later stages will surface richer
 * runtime metadata on top of this status object.
 */
data class BriarRuntimeStatus(
    val phase: BriarRuntimePhase,
    val storageDir: File? = null,
    val lastError: Throwable? = null
) {

    companion object {
        val stopped = BriarRuntimeStatus(BriarRuntimePhase.STOPPED)
    }
}

enum class BriarRuntimePhase {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED
}
