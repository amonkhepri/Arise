package com.example.rise.transport.store

import androidx.room.TypeConverter
import com.example.rise.transport.router.TransportId
import java.util.Date

class RoomConversationTypeConverters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun toTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromTransport(value: String?): TransportId? = value?.let { TransportId.valueOf(it) }

    @TypeConverter
    fun toTransport(transport: TransportId?): String? = transport?.name

    @TypeConverter
    fun fromParticipants(value: String?): List<String> =
        value?.takeIf { it.isNotEmpty() }?.split(PARTICIPANT_DELIMITER) ?: emptyList()

    @TypeConverter
    fun toParticipants(participants: List<String>?): String =
        participants?.joinToString(PARTICIPANT_DELIMITER) ?: ""

    companion object {
        private const val PARTICIPANT_DELIMITER = "|"
    }
}
