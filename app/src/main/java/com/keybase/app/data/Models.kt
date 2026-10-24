package com.keybase.app.data

import java.util.UUID

data class HistoryItem(val password: String, val replaced: Long)

data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val groupId: String? = null,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val appId: String = "", // Android package name, for exact app matching in autofill
    val notes: String = "",
    val favorite: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    val updated: Long = created,
    val pwChanged: Long = created,
    val history: List<HistoryItem> = emptyList(),
)

data class Group(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,
)
