package com.example.turisticky_zavod

import androidx.room.*

@Entity
data class Person(
    @PrimaryKey(autoGenerate = false) val id: Int,
    val name: String?,
    val team: String?
)

@Entity
data class Checkpoint(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val name: String,
    val active: Boolean = false
)

@Dao
interface PersonDao {
    @Query("SELECT * FROM person")
    fun getAll(): List<Person>

    @Query("SELECT * FROM person WHERE id = :id")
    fun getByID(id: Int): Person

    @Insert
    fun insert(person: Person)

    @Delete
    fun delete(person: Person)

    @Query("DELETE FROM person")
    fun deleteAll()

    @Update
    fun update(person: Person)
}

@Dao
interface CheckpointDao {
    @Query("SELECT * FROM checkpoint")
    fun getAll(): List<Checkpoint>

    @Query("SELECT * FROM checkpoint WHERE active = TRUE")
    fun getActive(): Checkpoint

    @Update
    fun update(checkpoint: Checkpoint)

    @Insert
    fun insert(checkpoint: Checkpoint)
}

@Database(entities = [Person::class, Checkpoint::class], version = 1)
abstract class TZDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun checkpointDao(): CheckpointDao
    override fun init(configuration: DatabaseConfiguration) {
        super.init(configuration)
        Thread {
            if (checkpointDao().getAll().isEmpty()) {
                checkpointDao().apply {
                    insert(Checkpoint(1, "Stavba stanu"))
                    insert(Checkpoint(2, "Orientace mapy"))
                    insert(Checkpoint(3, "Lanová lávka"))
                    insert(Checkpoint(4, "Uzly"))
                    insert(Checkpoint(5, "Míček"))
                    insert(Checkpoint(6, "Plížení"))
                    insert(Checkpoint(7, "Turistické a topografické"))
                    insert(Checkpoint(8, "Určování dřevin"))
                    insert(Checkpoint(9, "Kulturně poznávací"))
                }
            }
        }.start()
    }
}
