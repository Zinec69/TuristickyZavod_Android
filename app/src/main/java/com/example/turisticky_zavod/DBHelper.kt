package com.example.turisticky_zavod

import androidx.room.*

@Entity(indices = [Index(value = ["runnerId"], unique = true)])
data class Person(
    val runnerId: Int,
    val name: String?,
    val team: String?,
    @PrimaryKey(autoGenerate = true) val id: Int?
)

@Entity
data class Checkpoint(
    val name: String,
    val active: Boolean? = false,
    @PrimaryKey(autoGenerate = true) val id: Int?
)

@Dao
interface PersonDao {
    @Query("SELECT * FROM person ORDER BY id ASC")
    fun getAll(): List<Person>

    @Query("SELECT * FROM person WHERE runnerId = :id")
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

    @Query("DELETE FROM checkpoint")
    fun deleteAll()
}

@Database(entities = [Person::class, Checkpoint::class], version = 6)
abstract class TZDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun checkpointDao(): CheckpointDao
}
