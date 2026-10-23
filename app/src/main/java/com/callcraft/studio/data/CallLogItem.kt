package com.callcraft.studio.data

import android.provider.CallLog

data class CallLogItem(
    val id: Long,
    val name: String,
    val number: String,
    val type: Int, // CallLog.Calls.INCOMING_TYPE, OUTGOING_TYPE, MISSED_TYPE, REJECTED_TYPE
    val dateMillis: Long,
    val durationSeconds: Long,
    val simSlot: Int = 1, // 1 for SIM 1, 2 for SIM 2
    val phoneAccountId: String? = null
) {
    companion object {
        fun typeLabel(type: Int): String = when (type) {
            CallLog.Calls.INCOMING_TYPE -> "Incoming"
            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
            CallLog.Calls.MISSED_TYPE -> "Missed"
            CallLog.Calls.REJECTED_TYPE -> "Rejected"
            else -> "Other"
        }
    }
}

data class ContactCallGroup(
    val key: String, // Normalized phone number or name
    val name: String,
    val number: String,
    val latestItem: CallLogItem,
    val calls: List<CallLogItem>, // Chronologically sorted (latest first)
    val consecutiveMissedCount: Int = 0 // Count of consecutive missed calls at top
) {
    val id: Long get() = latestItem.id
    val dateMillis: Long get() = latestItem.dateMillis
    val simSlot: Int get() = latestItem.simSlot
    val type: Int get() = latestItem.type
    val durationSeconds: Long get() = latestItem.durationSeconds
}
