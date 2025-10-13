package com.example.rise.featureflags

import kotlinx.coroutines.flow.Flow

interface TransportModeProvider {
    fun observeMode(): Flow<BriarTransportMode>
    suspend fun setMode(mode: BriarTransportMode)
    suspend fun getMode(): BriarTransportMode
}
