package com.callcraft.studio.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.telephony.SubscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SimSlotInfo(
    val slotIndex: Int, // 0 for SIM 1, 1 for SIM 2
    val subscriptionId: Int,
    val carrierName: String,
    val isInserted: Boolean = true
)

data class DeviceSimState(
    val sim1: SimSlotInfo?,
    val sim2: SimSlotInfo?
) {
    val hasSim1: Boolean get() = sim1 != null && sim1.isInserted
    val hasSim2: Boolean get() = sim2 != null && sim2.isInserted
    val defaultSlot: Int get() = when {
        hasSim1 -> 1
        hasSim2 -> 2
        else -> 1
    }
}

class CallLogRepository(private val context: Context) {

    fun getDeviceSimState(): DeviceSimState {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val list = sm?.activeSubscriptionInfoList
                var s1: SimSlotInfo? = null
                var s2: SimSlotInfo? = null
                if (!list.isNullOrEmpty()) {
                    for (info in list) {
                        val rawName = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                            ?: info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                            ?: "SIM ${info.simSlotIndex + 1}"
                        if (info.simSlotIndex == 0) {
                            s1 = SimSlotInfo(0, info.subscriptionId, rawName, true)
                        } else if (info.simSlotIndex == 1) {
                            s2 = SimSlotInfo(1, info.subscriptionId, rawName, true)
                        }
                    }
                    return DeviceSimState(s1, s2)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return DeviceSimState(
            sim1 = SimSlotInfo(0, 0, "SIM 1", true),
            sim2 = null
        )
    }

    // Cache active SIM account IDs to accurately map SIM 1 and SIM 2
    private fun getSimAccountMap(): Pair<String?, String?> {
        val state = getDeviceSimState()
        return Pair(state.sim1?.subscriptionId?.toString(), state.sim2?.subscriptionId?.toString())
    }

    private fun resolveSimSlot(phoneAccountId: String?, sim1Id: String?, sim2Id: String?): Int {
        if (phoneAccountId.isNullOrBlank()) return 1
        if (sim2Id != null && phoneAccountId == sim2Id) return 2
        if (sim1Id != null && phoneAccountId == sim1Id) return 1
        // Fallback heuristic: check if account id contains "1" (slot 1 index) or "sim2"
        val lower = phoneAccountId.lowercase()
        return if (lower.contains("sub_1") || lower.contains("sim2") || lower.contains("slot1") || phoneAccountId == "1") 2 else 1
    }

    suspend fun getCallLogs(): List<CallLogItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<CallLogItem>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )

        val (sim1Id, sim2Id) = getSimAccountMap()

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                val nameCol = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val numCol = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val typeCol = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durCol = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val accountCol = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                while (cursor.moveToNext()) {
                    val rawAccountId = if (accountCol != -1) cursor.getString(accountCol) else null
                    val slot = resolveSimSlot(rawAccountId, sim1Id, sim2Id)

                    list.add(
                        CallLogItem(
                            id = cursor.getLong(idCol),
                            name = cursor.getString(nameCol)?.takeIf { it.isNotBlank() } ?: "Unknown",
                            number = cursor.getString(numCol) ?: "",
                            type = cursor.getInt(typeCol),
                            dateMillis = cursor.getLong(dateCol),
                            durationSeconds = cursor.getLong(durCol),
                            simSlot = slot,
                            phoneAccountId = rawAccountId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    // Google Phone Standard: Group consecutive calls by contact / number
    fun groupCallLogs(rawLogs: List<CallLogItem>): List<ContactCallGroup> {
        if (rawLogs.isEmpty()) return emptyList()

        val map = linkedMapOf<String, MutableList<CallLogItem>>()
        for (item in rawLogs) {
            val cleanNumber = item.number.replace("\\s+".toRegex(), "").replace("-", "")
            val key = if (cleanNumber.isNotBlank()) cleanNumber else item.name.trim().lowercase()
            map.getOrPut(key) { mutableListOf() }.add(item)
        }

        return map.values.map { calls ->
            val sortedCalls = calls.sortedByDescending { it.dateMillis }
            val latest = sortedCalls.first()

            // Count consecutive missed calls at the top of history
            var missedCount = 0
            if (latest.type == CallLog.Calls.MISSED_TYPE) {
                for (c in sortedCalls) {
                    if (c.type == CallLog.Calls.MISSED_TYPE) {
                        missedCount++
                    } else {
                        break
                    }
                }
            }

            ContactCallGroup(
                key = latest.number.ifBlank { latest.name },
                name = latest.name,
                number = latest.number,
                latestItem = latest,
                calls = sortedCalls,
                consecutiveMissedCount = missedCount
            )
        }.sortedByDescending { it.latestItem.dateMillis }
    }

    suspend fun insertCallLog(
        name: String,
        number: String,
        type: Int,
        dateMillis: Long,
        durationSeconds: Long,
        simSlot: Int = 1,
        phoneAccountId: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val (sim1Id, sim2Id) = getSimAccountMap()
            val accountToStore = phoneAccountId ?: if (simSlot == 2) (sim2Id ?: "1") else (sim1Id ?: "0")

            val values = ContentValues().apply {
                put(CallLog.Calls.CACHED_NAME, name.takeIf { it.isNotBlank() && it != number && it != "Unknown" })
                put(CallLog.Calls.NUMBER, number)
                put(CallLog.Calls.TYPE, type)
                put(CallLog.Calls.DATE, dateMillis)
                put(CallLog.Calls.DURATION, durationSeconds)
                put(CallLog.Calls.NEW, 0)
                put(CallLog.Calls.IS_READ, 1)
                put(CallLog.Calls.PHONE_ACCOUNT_ID, accountToStore)
            }
            context.contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun updateCallLog(item: CallLogItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val (sim1Id, sim2Id) = getSimAccountMap()
            val accountToStore = item.phoneAccountId ?: if (item.simSlot == 2) (sim2Id ?: "1") else (sim1Id ?: "0")

            val values = ContentValues().apply {
                put(CallLog.Calls.CACHED_NAME, item.name.takeIf { it.isNotBlank() && it != item.number && it != "Unknown" })
                put(CallLog.Calls.NUMBER, item.number)
                put(CallLog.Calls.TYPE, item.type)
                put(CallLog.Calls.DATE, item.dateMillis)
                put(CallLog.Calls.DURATION, item.durationSeconds)
                put(CallLog.Calls.NEW, 0)
                put(CallLog.Calls.IS_READ, 1)
                put(CallLog.Calls.PHONE_ACCOUNT_ID, accountToStore)
            }

            var updatedRows = 0

            // Strategy 1: Targeted update with selection clause (_ID = ?)
            try {
                updatedRows = context.contentResolver.update(
                    CallLog.Calls.CONTENT_URI,
                    values,
                    "${CallLog.Calls._ID} = ?",
                    arrayOf(item.id.toString())
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Strategy 2: Update with appended ID URI
            if (updatedRows == 0) {
                try {
                    val uri = ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI, item.id)
                    updatedRows = context.contentResolver.update(uri, values, null, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Strategy 3: Atomic Replace
            if (updatedRows == 0) {
                val deleted = deleteCallLog(item.id)
                val insertedUri = insertCallLog(
                    name = item.name,
                    number = item.number,
                    type = item.type,
                    dateMillis = item.dateMillis,
                    durationSeconds = item.durationSeconds,
                    simSlot = item.simSlot,
                    phoneAccountId = accountToStore
                )
                return@withContext (deleted || insertedUri != null)
            }

            return@withContext updatedRows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                deleteCallLog(item.id)
                val insertedUri = insertCallLog(
                    name = item.name,
                    number = item.number,
                    type = item.type,
                    dateMillis = item.dateMillis,
                    durationSeconds = item.durationSeconds,
                    simSlot = item.simSlot
                )
                insertedUri != null
            } catch (ex: Exception) {
                ex.printStackTrace()
                false
            }
        }
    }

    suspend fun deleteCallLog(id: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            var rows = 0
            try {
                rows = context.contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls._ID} = ?",
                    arrayOf(id.toString())
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (rows == 0) {
                try {
                    val uri = ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI, id)
                    rows = context.contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // High-performance Batch Deletion
    suspend fun deleteCallLogs(ids: Collection<Long>): Boolean = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext true
        ids.chunked(50).forEach { chunk ->
            try {
                val placeholders = chunk.joinToString(",") { "?" }
                val args = chunk.map { it.toString() }.toTypedArray()
                val deleted = context.contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls._ID} IN ($placeholders)",
                    args
                )
                if (deleted == 0) {
                    for (id in chunk) {
                        deleteCallLog(id)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                for (id in chunk) {
                    deleteCallLog(id)
                }
            }
        }
        true
    }

    // Bulk Update Call Type across multiple logs
    suspend fun bulkUpdateCallType(
        items: List<CallLogItem>,
        targetType: Int,
        defaultDurationSec: Long = 60L
    ): Boolean = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext true
        var successCount = 0

        for (item in items) {
            val adjustedDuration = if (targetType == CallLog.Calls.MISSED_TYPE || targetType == CallLog.Calls.REJECTED_TYPE) {
                0L
            } else {
                if (item.durationSeconds == 0L) defaultDurationSec else item.durationSeconds
            }

            val updatedItem = item.copy(
                type = targetType,
                durationSeconds = adjustedDuration
            )
            if (updateCallLog(updatedItem)) {
                successCount++
            }
        }
        successCount > 0
    }

    // Bulk Update SIM Slot across multiple logs
    suspend fun bulkUpdateSimSlot(
        items: List<CallLogItem>,
        targetSimSlot: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext true
        var successCount = 0

        for (item in items) {
            val updatedItem = item.copy(simSlot = targetSimSlot)
            if (updateCallLog(updatedItem)) {
                successCount++
            }
        }
        successCount > 0
    }

    suspend fun deleteCallsOlderThan(cutoffMillis: Long): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls.DATE} < ?",
                arrayOf(cutoffMillis.toString())
            )
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    suspend fun deleteCallsByNumber(number: String): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls.NUMBER} = ?",
                arrayOf(number)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
}
