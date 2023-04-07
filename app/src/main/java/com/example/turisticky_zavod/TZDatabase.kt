package com.example.turisticky_zavod

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Database(entities = [Runner::class, Checkpoint::class], version = 31)
@TypeConverters(CheckpointInfoJsonConverter::class)
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

@OptIn(DelicateCoroutinesApi::class)
private fun populateCheckpoints(context: Context) {
    GlobalScope.launch(Dispatchers.IO) {
        TZDatabase.getInstance(context).checkpointDao().apply {
            insert(Checkpoint("Start/cíl", false, 1))
            insert(Checkpoint("Odhad vzdálenosti", false, 2))
            insert(Checkpoint("Stavba stanu", false, 3))
            insert(Checkpoint("Orientace mapy", false, 4))
            insert(Checkpoint("Azimutové úseky", false, 5))
            insert(Checkpoint("Lanová lávka", false, 6))
            insert(Checkpoint("Uzlování", false, 7))
            insert(Checkpoint("Plížení", false, 8))
            insert(Checkpoint("Hod kriketovým míčkem", false, 9))
            insert(Checkpoint("Turistické a topografické značky", false, 10))
            insert(Checkpoint("Poznávání dřevin", false, 11))
            insert(Checkpoint("Kulturně poznávací činnost", false, 12))
            insert(Checkpoint("Kontrola \"X\"", false, 13))
        }
    }
}
