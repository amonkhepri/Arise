package com.example.rise.models


data class ChatChannel(val userIds: MutableList<String>) {
    constructor() : this(mutableListOf())
}