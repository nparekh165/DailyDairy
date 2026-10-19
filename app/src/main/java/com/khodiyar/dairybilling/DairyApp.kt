package com.khodiyar.dairybilling

import android.app.Application
import com.khodiyar.dairybilling.pdf.DocumentFiles

class DairyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DocumentFiles.cleanup(this)
    }
}
