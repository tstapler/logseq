package com.logseq.kmp.db

actual class PlatformUtils actual constructor() {
    actual fun getDatabaseDirectory(): String {
        // On Android, use app-specific directory
        return android.content.Context::class.java.let { ctxClass ->
            try {
                val activityThread = Class.forName("android.app.ActivityThread")
                val app = activityThread.getMethod("getApplication").invoke(null)
                val getFilesDir = app::class.java.getMethod("getFilesDir")
                getFilesDir.invoke(app) as? String ?: "/data/data/files"
            } catch (e: Exception) {
                "/data/data/files"
            }
        }
    }
    
    actual fun getDatabasePath(graphId: String?): String {
        val dir = getDatabaseDirectory()
        
        return if (graphId != null) {
            "$dir/${GRAPH_DB_PREFIX}${graphId}${GRAPH_DB_SUFFIX}"
        } else {
            "$dir/logseq.db"
        }
    }
    
    actual fun migrateDatabaseFile(oldPath: String, newPath: String): Boolean {
        return try {
            val oldFile = java.io.File(oldPath)
            val newFile = java.io.File(newPath)
            
            if (!oldFile.exists()) return false
            if (newFile.exists()) return true
            
            val renamed = oldFile.renameTo(newFile)
            if (!renamed) {
                oldFile.copyTo(newFile, overwrite = true)
                oldFile.delete()
            }
            true
        } catch (e: Exception) {
            println("Failed to migrate database file: ${e.message}")
            false
        }
    }
}
