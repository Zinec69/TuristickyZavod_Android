package com.example.turisticky_zavod

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Runner::class, Checkpoint::class, Referee::class], version = 19)
abstract class TZDatabase : RoomDatabase() {
    abstract fun runnerDao(): RunnerDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun refereeDao(): RefereeDao

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
            insert(Checkpoint("Start/cíl", false, null))
            insert(Checkpoint("Odhad vzdálenosti", false, null))
            insert(Checkpoint("Stavba stanu", false, null))
            insert(Checkpoint("Orientace mapy", false, null))
            insert(Checkpoint("Azimutové úseky", false, null))
            insert(Checkpoint("Lanová lávka", false, null))
            insert(Checkpoint("Uzlování", false, null))
            insert(Checkpoint("Plížení", false, null))
            insert(Checkpoint("Hod kriketovým míčkem", false, null))
            insert(Checkpoint("Turistické a topografické značky", false, null))
            insert(Checkpoint("Poznávání dřevin", false, null))
            insert(Checkpoint("Kulturně poznávací činnost", false, null))
        }
    }.start()
}
