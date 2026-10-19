package com.khodiyar.dairybilling.workspace

import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.time.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class DairyProfile(val id: String, val name: String, val createdAt: Long)

@Serializable
data class DairyList(val dairies: List<DairyProfile>, val activeId: String) {
    val active: DairyProfile get() = dairies.firstOrNull { it.id == activeId } ?: dairies.first()
}

/**
 * The dairies (separate businesses) kept on this phone. Each dairy has its own Room database file,
 * so customers, products, bills, staff, audit log and backups never mix between dairies.
 * The first dairy keeps the original database file, so existing installs upgrade in place.
 */
class DairyRegistry(private val file: File, private val databaseDir: File, private val time: TimeProvider) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<DairyList> = _state.asStateFlow()

    val active: DairyProfile get() = _state.value.active

    @Synchronized
    fun add(name: String): DairyProfile {
        val clean = name.trim()
        if (clean.isEmpty()) throw AppException(UiText.res(R.string.err_required))
        if (_state.value.dairies.any { it.name.equals(clean, ignoreCase = true) }) throw AppException(UiText.res(R.string.err_dairy_exists))
        var id: String
        do { id = UUID.randomUUID().toString().replace("-", "").take(10) } while (_state.value.dairies.any { it.id == id })
        val profile = DairyProfile(id, clean, time.nowMillis())
        write(_state.value.copy(dairies = _state.value.dairies + profile))
        return profile
    }

    /** Keeps the list label in step with the business name saved in that dairy's settings. */
    @Synchronized
    fun rename(id: String, name: String) {
        val clean = name.trim()
        val cur = _state.value
        if (clean.isEmpty() || cur.dairies.none { it.id == id && it.name != clean }) return
        write(cur.copy(dairies = cur.dairies.map { if (it.id == id) it.copy(name = clean) else it }))
    }

    @Synchronized
    fun setActive(id: String) {
        val cur = _state.value
        require(cur.dairies.any { it.id == id }) { "Unknown dairy $id" }
        if (cur.activeId != id) write(cur.copy(activeId = id))
    }

    private fun write(next: DairyList) {
        writeFile(next)
        _state.value = next
    }

    /** Writes to a temp file first so a crash mid-write never leaves a half-written list. */
    private fun writeFile(list: DairyList) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(DairyList.serializer(), list))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Could not save dairy list" }
        }
    }

    private fun load(): DairyList {
        val saved = runCatching { json.decodeFromString(DairyList.serializer(), file.readText()) }.getOrNull()
            ?.takeIf { it.dairies.isNotEmpty() }
        val base = saved ?: DairyList(listOf(DairyProfile(MAIN, "", time.nowMillis())), MAIN)
        // Self-healing: a database file that is not in the list (e.g. the list file was lost) is added back, never orphaned.
        val known = base.dairies.map { it.id }.toSet()
        val found = databaseDir.listFiles().orEmpty()
            .mapNotNull { f -> f.name.removePrefix(PREFIX).removeSuffix(".db").takeIf { f.name.startsWith(PREFIX) && f.name.endsWith(".db") && it.isNotEmpty() } }
            .filter { it !in known }
            .map { DairyProfile(it, "", time.nowMillis()) }
        val healed = base.copy(dairies = base.dairies + found).let { if (it.dairies.none { d -> d.id == it.activeId }) it.copy(activeId = it.dairies.first().id) else it }
        if (healed != saved) runCatching { writeFile(healed) }
        return healed
    }

    companion object {
        const val MAIN = "main"
        // Hyphen, not underscore, so the original "dairy_billing.db" is never mistaken for an added dairy.
        private const val PREFIX = "dairy-"

        fun databaseName(id: String): String = if (id == MAIN) AppDatabase.NAME else "$PREFIX$id.db"
    }
}
