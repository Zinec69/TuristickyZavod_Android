package com.example.turisticky_zavod

import androidx.room.*

@Entity
data class Referee(
    val name: String,
    @PrimaryKey(autoGenerate = false) val id: Int = 1
)

@Dao
interface RefereeDao {
    @Query("SELECT * FROM referee LIMIT 1")
    fun get(): Referee?

    @Insert
    fun insert(referee: Referee)

    @Update
    fun update(referee: Referee)

    @Query("DELETE FROM referee")
    fun reset()
}
