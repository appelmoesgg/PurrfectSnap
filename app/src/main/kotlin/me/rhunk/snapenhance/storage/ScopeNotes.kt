package me.rhunk.snapenhance.storage

import androidx.core.database.getStringOrNull

fun AppDatabase.getScopeNotes(id: String): String? {
    return database.rawQuery("SELECT content FROM notes WHERE id = ?", arrayOf(id)).use {
        if (it.moveToNext()) {
            it.getStringOrNull(0)
        } else {
            null
        }
    }
}

fun AppDatabase.setScopeNotes(id: String, content: String?) {
    if (content == null || content.isEmpty() == true) {
        executeAsync {
            database.execSQL("DELETE FROM notes WHERE id = ?", arrayOf(id))
        }
        return
    }

    executeAsync {
        database.execSQL("INSERT OR REPLACE INTO notes (id, content) VALUES (?, ?)", arrayOf(id, content))
    }
}

fun AppDatabase.deleteScopeNotes(id: String) {
    executeAsync {
        database.execSQL("DELETE FROM notes WHERE id = ?", arrayOf(id))
    }
}

fun AppDatabase.getAllScopeNotes(): Map<String, String> {
    return database.rawQuery("SELECT id, content FROM notes", null).use {
        val map = mutableMapOf<String, String>()
        while (it.moveToNext()) {
            map[it.getString(0)] = it.getString(1)
        }
        map
    }
}

fun AppDatabase.setAllScopeNotes(notes: Map<String, String>) {
    executeAsync {
        database.beginTransaction()
        try {
            for ((id, content) in notes) {
                database.execSQL("INSERT OR REPLACE INTO notes (id, content) VALUES (?, ?)", arrayOf(id, content))
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }
}
