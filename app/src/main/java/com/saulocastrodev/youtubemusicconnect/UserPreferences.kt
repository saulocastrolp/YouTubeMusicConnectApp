package com.saulocastrodev.youtubemusicconnect

import android.content.Context
import android.content.SharedPreferences

data class UserInfo(
    val name: String,
    val email: String,
    val foto: String?,
    val tokenYtmd: String?,
    val token: String?
)

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String): String? {
        return prefs.getString(key, null)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun saveUser(user: UserInfo?, token: String?) {
        user?.let {
            saveString("name", it.name)
            saveString("email", it.email)
            saveString("foto", it.foto ?: "")
            saveString("tokenYtmd", it.tokenYtmd ?: "")
            saveString("token", it.token ?: "")
        }
        if (!token.isNullOrEmpty()) {
            saveString("token", token)
        }
    }

    fun getUser(): UserInfo? {
        val name = prefs.getString("name", null) ?: return null
        val email = prefs.getString("email", null) ?: return null
        val foto = prefs.getString("foto", null)
        val tokenYtmd = prefs.getString("tokenYtmd", null)
        val token = prefs.getString("token", null) ?: return null

        return UserInfo(name, email, foto, tokenYtmd, token)
    }
}
