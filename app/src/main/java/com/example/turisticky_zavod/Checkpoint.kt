package com.example.turisticky_zavod

import androidx.room.*

@Entity
data class Checkpoint(
    val name: String,
    @ColumnInfo(defaultValue = "0") val active: Boolean? = false,
    @PrimaryKey(autoGenerate = true) val id: Int?
)

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
