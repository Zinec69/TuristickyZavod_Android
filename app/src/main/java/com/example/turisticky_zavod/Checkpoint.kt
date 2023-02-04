package com.example.turisticky_zavod

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    @Query("SELECT name FROM checkpoint")
    fun getNames(): Array<String>

    @Query("SELECT * FROM checkpoint WHERE active = TRUE LIMIT 1")
    fun getActive(): Checkpoint?

    @Query("UPDATE checkpoint SET active = TRUE WHERE name = :name")
    fun setActive(name: String)

    @Query("UPDATE checkpoint SET active = FALSE WHERE active = TRUE")
    fun reset()

    @Insert
    fun insert(checkpoint: Checkpoint)

    @Update
    fun update(checkpoint: Checkpoint)

    @Delete
    fun delete(checkpoint: Checkpoint)

    @Query("DELETE FROM checkpoint")
    fun deleteAll()
}
