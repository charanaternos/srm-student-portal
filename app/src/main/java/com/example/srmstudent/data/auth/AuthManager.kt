package com.example.srmstudent.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.example.srmstudent.domain.model.UserSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("srm_auth_prefs", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow<UserSession?>(loadSession())
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    fun isLoggedIn(): Boolean = _session.value != null

    private fun loadSession(): UserSession? {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (!isLoggedIn) return null

        val netId = prefs.getString(KEY_NET_ID, "") ?: ""
        if (netId.isEmpty()) return null

        return UserSession(
            netId = netId,
            email = prefs.getString(KEY_EMAIL, "$netId@srmist.edu.in") ?: "$netId@srmist.edu.in",
            name = prefs.getString(KEY_NAME, "SRM Student") ?: "SRM Student",
            regNumber = prefs.getString(KEY_REG_NUMBER, netId) ?: netId,
            token = prefs.getString(KEY_TOKEN, null),
            userId = prefs.getString(KEY_USER_ID, null),
            supabaseUrl = prefs.getString(KEY_SUPABASE_URL, null),
            supabaseAnonKey = prefs.getString(KEY_SUPABASE_ANON_KEY, null)
        )
    }

    fun saveSession(userSession: UserSession) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_NET_ID, userSession.netId)
            .putString(KEY_EMAIL, userSession.email)
            .putString(KEY_NAME, userSession.name)
            .putString(KEY_REG_NUMBER, userSession.regNumber)
            .putString(KEY_TOKEN, userSession.token)
            .putString(KEY_USER_ID, userSession.userId)
            .putString(KEY_SUPABASE_URL, userSession.supabaseUrl)
            .putString(KEY_SUPABASE_ANON_KEY, userSession.supabaseAnonKey)
            .apply()

        _session.value = userSession
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_NET_ID = "net_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_NAME = "name"
        private const val KEY_REG_NUMBER = "reg_number"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_SUPABASE_URL = "supabase_url"
        private const val KEY_SUPABASE_ANON_KEY = "supabase_anon_key"
    }
}

