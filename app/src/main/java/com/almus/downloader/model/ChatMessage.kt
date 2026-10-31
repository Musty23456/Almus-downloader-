package com.almus.downloader.model

data class ChatMessage(
    val id: Int,
    val text: String,
    val fromUser: Boolean,
)
