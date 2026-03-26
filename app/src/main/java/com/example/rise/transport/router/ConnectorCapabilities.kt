package com.example.rise.transport.router

/**
 * Describes the set of capabilities a connector exposes. Each entry contains a semantic version
 * and optional metadata (string key/value pairs) so the router/UI can react without knowing the
 * connector implementation details.
 */
data class ConnectorCapabilities(
    val entries: Map<String, CapabilityDescriptor> = emptyMap(),
) {
    companion object {
        val EMPTY = ConnectorCapabilities()
    }
}

data class CapabilityDescriptor(
    val version: Int,
    val properties: Map<String, String> = emptyMap(),
)
