package com.example.rise.models

data class User(
    val name: String,
    val bio: String,
    val profilePicturePath: String?,
    val registrationTokens: MutableList<String>,
    val presence: String? = null,
) {
    constructor() : this("", "", null, mutableListOf(), null)
}
