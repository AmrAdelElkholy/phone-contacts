package com.elkhouly.dialer.helpers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.elkhouly.commons.helpers.ensureBackgroundThread
import com.elkhouly.dialer.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Caller ID rows added or fixed on the server after the APK was built, see docs/caller-id/PLAN.md
 * (phase 2). Only changes are downloaded, the phone never sends its own numbers or contacts.
 */
object CallerIdUpdates {
    class Entry(val category: String, val nameEn: String, val nameAr: String, val deleted: Boolean)

    private const val DATABASE_FILE = "caller_id_updates.db"
    private const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L
    private const val MAX_PAGES = 50
    private const val TIMEOUT_MS = 15_000

    private const val META_VERSION = "version"
    private const val META_AFTER = "after"
    private const val META_LAST_SYNC = "last_sync"

    private var database: SQLiteDatabase? = null
    @Volatile
    private var isSyncing = false

    fun find(context: Context, key: String): Entry? {
        val db = getDatabase(context) ?: return null
        return try {
            db.rawQuery("SELECT category, name_en, name_ar, deleted FROM places WHERE number = ?", arrayOf(key)).use { cursor ->
                if (cursor.moveToFirst()) Entry(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3) == 1) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // called when the app opens, downloads at most once a day in the background
    fun syncIfDue(context: Context, baselineVersion: () -> Long) {
        if (isSyncing) {
            return
        }

        val appContext = context.applicationContext
        ensureBackgroundThread {
            val db = getDatabase(appContext) ?: return@ensureBackgroundThread
            val lastSync = getMeta(db, META_LAST_SYNC)?.toLongOrNull() ?: 0L
            if (isSyncing || System.currentTimeMillis() - lastSync < SYNC_INTERVAL_MS) {
                return@ensureBackgroundThread
            }

            isSyncing = true
            try {
                sync(db, baselineVersion)
            } catch (_: Exception) {
                // no connection or server trouble, try again next time the app opens
            } finally {
                isSyncing = false
            }
        }
    }

    private fun sync(db: SQLiteDatabase, baselineVersion: () -> Long) {
        var version = getMeta(db, META_VERSION)?.toLongOrNull() ?: baselineVersion()
        var after = getMeta(db, META_AFTER).orEmpty()

        for (page in 0 until MAX_PAGES) {
            var url = "${BuildConfig.CALLER_ID_URL}/v1/delta?since=$version"
            if (after.isNotEmpty()) {
                url += "&after=${URLEncoder.encode(after, "UTF-8")}"
            }

            val response = JSONObject(download(url))
            val rows = response.getJSONArray("rows")
            db.beginTransaction()
            try {
                for (i in 0 until rows.length()) {
                    // [number, English name, Arabic name, category, deleted, version]
                    val row = rows.getJSONArray(i)
                    val values = ContentValues().apply {
                        put("number", row.getString(0))
                        put("name_en", row.getString(1))
                        put("name_ar", row.getString(2))
                        put("category", row.getString(3))
                        put("deleted", row.getInt(4))
                    }
                    db.insertWithOnConflict("places", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                    version = row.getLong(5)
                    after = row.getString(0)
                }

                setMeta(db, META_VERSION, version.toString())
                setMeta(db, META_AFTER, after)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            if (!response.getBoolean("more")) {
                break
            }
        }

        setMeta(db, META_LAST_SYNC, System.currentTimeMillis().toString())
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("caller ID server answered ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun getMeta(db: SQLiteDatabase, key: String): String? {
        return db.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun setMeta(db: SQLiteDatabase, key: String, value: String) {
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict("meta", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    private fun getDatabase(context: Context): SQLiteDatabase? {
        database?.let { return it }

        return try {
            val file = File(context.noBackupFilesDir, DATABASE_FILE)
            SQLiteDatabase.openOrCreateDatabase(file, null).apply {
                execSQL(
                    "CREATE TABLE IF NOT EXISTS places (number TEXT PRIMARY KEY, name_en TEXT NOT NULL, name_ar TEXT NOT NULL, " +
                        "category TEXT NOT NULL, deleted INTEGER NOT NULL) WITHOUT ROWID"
                )
                execSQL("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID")
            }.also { database = it }
        } catch (_: Exception) {
            null
        }
    }
}
