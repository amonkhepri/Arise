package com.example.rise.transport.router

import com.example.rise.featureflags.BriarTransportMode

/**
 * Snapshot describing which transport currently acts as the primary path plus the reason for the
 * decision. BridgeOrchestrator exposes this so diagnostics/QA tooling can confirm HYBRID gating
 * works as expected.
 *
 * @property timestampMs Wall-clock milliseconds since epoch when the snapshot was generated. The
 *   orchestrator stamps this with `System.currentTimeMillis()` so downstream tooling can order
 *   snapshots and correlate them with logs.
 */
data class PrimaryRoutingSnapshot(
    val mode: BriarTransportMode,
    val primary: TransportId,
    val preferred: TransportId,
    val fallbackTarget: TransportId?,
    val reason: PrimaryRoutingReason,
    val preferredLifecycle: ConnectorLifecycleState?,
    val trigger: PrimarySelectionTrigger,
    val timestampMs: Long,
)

enum class PrimaryRoutingReason {
    PreferredReady,
    PreferredNotReady,
    PreferredMissing,
    FlagForcesFirestore,
    ExplicitFallback,
    Initial,
}

enum class PrimarySelectionTrigger {
    INITIAL,
    MODE_CHANGED,
    LIFECYCLE_CHANGED,
    EXPLICIT_FALLBACK,
}
