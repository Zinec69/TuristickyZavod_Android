package cz.turisticky_zavod

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

    @Query("SELECT * FROM checkpoint WHERE active = 1 LIMIT 1")
    fun getActive(): Checkpoint?

    @Query("UPDATE checkpoint SET active = 1 WHERE name = :name")
    suspend fun setActive(name: String)

    @Query("UPDATE checkpoint SET active = 0 WHERE active = 1")
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
