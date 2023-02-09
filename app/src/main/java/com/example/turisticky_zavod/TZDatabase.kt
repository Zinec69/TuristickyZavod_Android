package com.example.turisticky_zavod

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Runner::class, Checkpoint::class], version = 24)
abstract class TZDatabase : RoomDatabase() {
    abstract fun runnerDao(): RunnerDao
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
            insert(Checkpoint("Start/cíl"))
            insert(Checkpoint("Odhad vzdálenosti"))
            insert(Checkpoint("Stavba stanu"))
            insert(Checkpoint("Orientace mapy"))
            insert(Checkpoint("Azimutové úseky"))
            insert(Checkpoint("Lanová lávka"))
            insert(Checkpoint("Uzlování"))
            insert(Checkpoint("Plížení"))
            insert(Checkpoint("Hod kriketovým míčkem"))
            insert(Checkpoint("Turistické a topografické značky"))
            insert(Checkpoint("Poznávání dřevin"))
            insert(Checkpoint("Kulturně poznávací činnost"))
        }
    }.start()
}
