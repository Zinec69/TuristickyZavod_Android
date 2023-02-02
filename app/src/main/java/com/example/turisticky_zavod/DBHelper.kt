package com.example.turisticky_zavod

import android.os.Parcel
import android.os.Parcelable
import androidx.room.*

@Entity(indices = [Index(value = ["runnerId"], unique = true)])
data class Person(
    val runnerId: Int,
    val name: String,
    val team: String,
    var penaltySeconds: Int = 0,
    var disqualified: Boolean = false,
    val startTime: Long,
    val finishTime: Long?,
    @PrimaryKey(autoGenerate = true) val id: Int?
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readInt(),
        parcel.readByte() != 0.toByte(),
        parcel.readLong(),
        parcel.readValue(Long::class.java.classLoader) as Long?,
        parcel.readValue(Int::class.java.classLoader) as? Int
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(runnerId)
        parcel.writeString(name)
        parcel.writeString(team)
        parcel.writeInt(penaltySeconds)
        parcel.writeByte(if (disqualified) 1 else 0)
        parcel.writeLong(startTime)
        parcel.writeValue(finishTime)
        parcel.writeValue(id)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Person> {
        override fun createFromParcel(parcel: Parcel): Person {
            return Person(parcel)
        }

        override fun newArray(size: Int): Array<Person?> {
            return arrayOfNulls(size)
        }
    }
}

@Entity
data class Checkpoint(
    val name: String,
    @ColumnInfo(defaultValue = "0") val active: Boolean? = false,
    @PrimaryKey(autoGenerate = true) val id: Int?
)

@Dao
interface PersonDao {
    @Query("SELECT * FROM person ORDER BY id ASC")
    fun getAll(): List<Person>

    @Query("SELECT * FROM person WHERE runnerId = :id")
    fun getByID(id: Int): Person

    @Query("SELECT * FROM person ORDER BY id DESC LIMIT 1")
    fun getLast(): Person

    @Query("SELECT * FROM person ORDER BY id DESC LIMIT :n")
    fun getNLast(n: Int): Person

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

@Database(entities = [Person::class, Checkpoint::class], version = 12)
abstract class TZDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun checkpointDao(): CheckpointDao
}
