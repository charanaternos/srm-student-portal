package com.example.srmstudent.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequestDto(
    @SerializedName("email")
    val email: String,

    @SerializedName("password")
    val password: String
)

data class AuthResponseDto(
    @SerializedName("access_token")
    val accessToken: String? = null,

    @SerializedName("token_type")
    val tokenType: String? = null,

    @SerializedName("user")
    val user: AuthUserDto? = null
)

data class AuthUserDto(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("user_metadata")
    val metadata: Map<String, Any?>? = null
)

