package com.example.rise.transport.router

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

interface IdentityRegistryStore {

    data class StoredState(
        val records: Map<String, IdentityRecord>,
        val currentIdentityId: String?,
    )

    fun load(): StoredState

    fun persist(records: Map<String, IdentityRecord>, currentIdentityId: String?)
}

class SharedPrefsIdentityRegistryStore(
    context: Context,
) : IdentityRegistryStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): IdentityRegistryStore.StoredState {
        val raw = prefs.getString(KEY_IDENTITIES, null)
        if (raw.isNullOrEmpty()) {
            return IdentityRegistryStore.StoredState(emptyMap(), null)
        }
        val identities = mutableMapOf<String, IdentityRecord>()
        runCatching { JSONArray(raw) }
            .onSuccess { array ->
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val canonicalId = obj.optString(KEY_CANONICAL_ID)
                    val displayName = obj.optString(KEY_DISPLAY_NAME)
                    if (canonicalId.isNullOrBlank()) continue
                    val aliasesJson = obj.optJSONObject(KEY_ALIASES) ?: JSONObject()
                    val aliases = mutableMapOf<TransportId, String>()
                    aliasesJson.keys().forEachRemaining { transportName ->
                        val alias = aliasesJson.optString(transportName)
                        if (!alias.isNullOrBlank()) {
                            runCatching { TransportId.valueOf(transportName) }
                                .onSuccess { aliases[it] = alias }
                        }
                    }
                    identities[canonicalId] = IdentityRecord(
                        identity = CanonicalIdentity(
                            id = canonicalId,
                            displayName = displayName,
                        ),
                        aliases = aliases,
                    )
                }
            }
        val currentId = prefs.getString(KEY_CURRENT_IDENTITY, null)
        return IdentityRegistryStore.StoredState(
            records = identities,
            currentIdentityId = currentId?.takeIf { identities.containsKey(it) },
        )
    }

    override fun persist(records: Map<String, IdentityRecord>, currentIdentityId: String?) {
        val array = JSONArray()
        records.values.forEach { record ->
            val aliasesJson = JSONObject()
            record.aliases.forEach { (transport, alias) ->
                aliasesJson.put(transport.name, alias)
            }
            array.put(
                JSONObject().apply {
                    put(KEY_CANONICAL_ID, record.identity.id)
                    put(KEY_DISPLAY_NAME, record.identity.displayName)
                    put(KEY_ALIASES, aliasesJson)
                }
            )
        }
        prefs.edit {
            putString(KEY_IDENTITIES, array.toString())
            if (currentIdentityId.isNullOrBlank()) {
                remove(KEY_CURRENT_IDENTITY)
            } else {
                putString(KEY_CURRENT_IDENTITY, currentIdentityId)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "identity_registry_store"
        private const val KEY_IDENTITIES = "identities"
        private const val KEY_CURRENT_IDENTITY = "current_identity"
        private const val KEY_CANONICAL_ID = "canonical_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_ALIASES = "aliases"
    }
}
