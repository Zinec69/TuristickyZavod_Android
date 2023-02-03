package com.example.turisticky_zavod

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Person::class, Checkpoint::class], version = 15)
abstract class TZDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun checkpointDao(): CheckpointDao

    companion object {
        @Volatile private var INSTANCE: TZDatabase? = null

        fun getInstance(context: Context): TZDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(context.applicationContext, TZDatabase::class.java, "tz.db")
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            populateCheckpoints(context.applicationContext)
                        }

                        override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                            super.onDestructiveMigration(db)
                            populateCheckpoints(context.applicationContext)
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

private fun populateCheckpoints(context: Context) {
    Thread {
        TZDatabase.getInstance(context).checkpointDao().apply {
            insert(Checkpoint("Stavba stanu", false, null))
            insert(Checkpoint("Orientace mapy", false, null))
            insert(Checkpoint("Lanová lávka", false, null))
            insert(Checkpoint("Uzly", false, null))
            insert(Checkpoint("Míček", false, null))
            insert(Checkpoint("Plížení", false, null))
            insert(Checkpoint("Turistické a topografické", false, null))
            insert(Checkpoint("Určování dřevin", false, null))
            insert(Checkpoint("Kulturně poznávací", false, null))
        }
    }.start()
}
