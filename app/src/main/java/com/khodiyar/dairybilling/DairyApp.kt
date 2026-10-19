package com.khodiyar.dairybilling

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.khodiyar.dairybilling.domain.time.SystemTimeProvider
import com.khodiyar.dairybilling.pdf.DocumentFiles
import com.khodiyar.dairybilling.workspace.DairyRegistry
import java.io.File

class DairyApp : Application() {
    lateinit var registry: DairyRegistry
        private set

    @Volatile lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val dbDir = getDatabasePath(DairyRegistry.databaseName(DairyRegistry.MAIN)).parentFile ?: File(filesDir.parentFile, "databases")
        registry = DairyRegistry(File(filesDir, "dairies.json"), dbDir, SystemTimeProvider)
        container = buildContainer(registry.active.id)
        DocumentFiles.cleanup(this)
    }

    private fun buildContainer(id: String) =
        AppContainer(this, dbName = DairyRegistry.databaseName(id), dairyId = id, onDairyNamed = { registry.rename(id, it) })

    /**
     * Opens another dairy. The caller restarts the activity afterwards so every screen and
     * ViewModel is rebuilt on the new dairy's database; nobody stays signed in across dairies.
     */
    @Synchronized
    fun switchDairy(id: String) {
        if (id == container.dairyId) return
        registry.setActive(id)
        val old = container
        container = buildContainer(id)
        // Let the old screens' coroutines finish cancelling before their database closes.
        Handler(Looper.getMainLooper()).postDelayed({ old.close() }, 3_000)
    }
}
