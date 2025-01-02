package com.jfrashu.taskchat

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatApplication : Application() {
    private lateinit var tokenManager: TokenManager
    private val applicationScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager()

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                applicationScope.launch {
                    tokenManager.initializeToken()
                }
            }
        }
    }
}