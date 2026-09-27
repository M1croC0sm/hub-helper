package app.hubhelper.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File

/** Callers hold StorageCoordinator's lock while promoting or recovering originals. */
class RestoreJournal(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "restore-journal.json"))

    fun read(): JSONArray = try {
        file.openRead().bufferedReader().use { JSONArray(it.readText()) }
    } catch (_: java.io.FileNotFoundException) {
        JSONArray()
    }

    fun write(paths: JSONArray) {
        val output = file.startWrite()
        try {
            output.write(paths.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (failure: Throwable) {
            file.failWrite(output)
            throw failure
        }
    }

    fun delete() = file.delete()
}
