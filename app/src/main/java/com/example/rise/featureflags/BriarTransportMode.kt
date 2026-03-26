package com.example.rise.featureflags

enum class BriarTransportMode {
    FIRESTORE,
    HYBRID,
    BRIAR_ONLY;

    companion object {
        fun fromValue(value: Int?): BriarTransportMode {
            if (value == null) return BRIAR_ONLY
            return entries.getOrNull(value) ?: BRIAR_ONLY
        }
    }
}
