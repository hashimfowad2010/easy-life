package com.easylife.app.core.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import com.easylife.app.BuildConfig
import com.easylife.app.core.data.db.AppDatabase
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.devtools.ErrorLog
import com.easylife.app.core.scan.ScanDraftStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** "Export all my data" and "Delete all my data" (spec rule 12). Nothing is uploaded. */
@Singleton
class DataManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val prefs: PrefsRepository,
    private val drafts: ScanDraftStore,
    private val errorLog: ErrorLog,
) {
    /** Writes every table as JSON to a file the user picked. Returns the number of rows exported. */
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "Easy Life")
        root.put("version", BuildConfig.VERSION_NAME)
        root.put("exportedAt", Instant.now().toString())
        root.put("note", "Your own data from this phone. Keep it private: it can contain bills and bank messages.")
        val tables = JSONObject()
        var rows = 0
        val sqlite = db.openHelper.readableDatabase
        val names = mutableListOf<String>()
        sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table','android_metadata')").use { c ->
            while (c.moveToNext()) names += c.getString(0)
        }
        for (name in names.sorted()) {
            val arr = JSONArray()
            sqlite.query("SELECT * FROM `$name`").use { c ->
                while (c.moveToNext()) {
                    arr.put(rowToJson(c))
                    rows++
                }
            }
            tables.put(name, arr)
        }
        root.put("tables", tables)
        context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) }
        rows
    }

    private fun rowToJson(c: Cursor): JSONObject {
        val o = JSONObject()
        for (i in 0 until c.columnCount) {
            val key = c.getColumnName(i)
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> o.put(key, JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> o.put(key, c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> o.put(key, c.getDouble(i))
                Cursor.FIELD_TYPE_STRING -> o.put(key, c.getString(i))
                else -> o.put(key, "(binary)")
            }
        }
        return o
    }

    /** Erases all app data on this phone. The caller restarts the UI afterwards. */
    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        drafts.deleteAll()
        errorLog.clear()
        prefs.clearAll()
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
    }
}
