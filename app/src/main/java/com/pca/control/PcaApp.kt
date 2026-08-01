package com.pca.control

import android.app.Application
import com.google.firebase.FirebaseApp
import com.pca.control.data.AppPreferences
import com.pca.control.pairing.PairingRepository

class PcaApp : Application() {
    lateinit var preferences: AppPreferences
        private set
    lateinit var pairingRepository: PairingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        preferences = AppPreferences(this)
        pairingRepository = PairingRepository(preferences)
    }
}
