package app.hubhelper.data

import android.content.Context

object AppDataResetter {
    fun clearAll(context: Context) {
        HubHelperDatabase.get(context).clearAllTables()
    }
}
