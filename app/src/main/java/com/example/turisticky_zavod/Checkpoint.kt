package com.example.turisticky_zavod

import androidx.room.*

@Entity
data class Checkpoint(
    val name: String,
    @ColumnInfo(defaultValue = "0") val active: Boolean = false,
    @PrimaryKey(autoGenerate = true) val id: Int? = null
)

@Dao
interface CheckpointDao {
    @Query("SELECT * FROM checkpoint")
    fun getAll(): List<Checkpoint>

    @Query("SELECT name FROM checkpoint")
    fun getNames(): Array<String>

    @Query("SELECT * FROM checkpoint WHERE active = TRUE LIMIT 1")
    fun getActive(): Checkpoint?

    @Query("UPDATE checkpoint SET active = TRUE WHERE name = :name")
    suspend fun setActive(name: String)

    @Query("UPDATE checkpoint SET active = FALSE WHERE active = TRUE")
    suspend fun reset()

    @Insert
    suspend fun insert(checkpoint: Checkpoint)

    @Update
    suspend fun update(checkpoint: Checkpoint)

    @Delete
    suspend fun delete(checkpoint: Checkpoint)

    @Query("DELETE FROM checkpoint")
    suspend fun deleteAll()
}
