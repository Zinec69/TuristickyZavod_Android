package com.example.turisticky_zavod

import android.app.Application
import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.*
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Entity(indices = [Index(value = ["runnerId"], unique = true)])
data class Runner(
    val runnerId: Int,
    val name: String,
    val team: String,
    val startTime: Long,
    var finishTime: Long? = null,
    var timeWaited: Int = 0,
    var penaltySeconds: Int = 0,
    var disqualified: Boolean = false,
    @PrimaryKey(autoGenerate = true) val id: Int? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readLong(),
        parcel.readValue(Long::class.java.classLoader) as Long?,
        parcel.readInt(),
        parcel.readInt(),
        parcel.readByte() != 0.toByte(),
        parcel.readValue(Int::class.java.classLoader) as? Int
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(runnerId)
        parcel.writeString(name)
        parcel.writeString(team)
        parcel.writeLong(startTime)
        parcel.writeValue(finishTime)
        parcel.writeInt(timeWaited)
        parcel.writeInt(penaltySeconds)
        parcel.writeByte(if (disqualified) 1 else 0)
        parcel.writeValue(id)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Runner> {
        override fun createFromParcel(parcel: Parcel): Runner {
            return Runner(parcel)
        }

        override fun newArray(size: Int): Array<Runner?> {
            return arrayOfNulls(size)
        }
    }
}

@Dao
interface RunnerDao {
    @Query("SELECT * FROM runner ORDER BY id ASC")
    fun getAll(): LiveData<List<Runner>>

    @Query("SELECT * FROM runner WHERE runnerId = :id")
    fun getByID(id: Int): Runner?

    @Query("SELECT * FROM runner ORDER BY id DESC LIMIT 1")
    fun getLast(): Runner?

    @Query("SELECT * FROM runner ORDER BY id DESC LIMIT :n")
    fun getNLast(n: Int): List<Runner>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(runner: Runner)

    @Delete
    suspend fun delete(runner: Runner)

    @Query("DELETE FROM runner")
    suspend fun deleteAll()

    @Update
    suspend fun update(runner: Runner)
}

class RunnerRepository(private val runnerDao: RunnerDao) {
    val runners: LiveData<List<Runner>> = runnerDao.getAll()

    fun getByID(id: Int): Runner? {
        return runnerDao.getByID(id)
    }

    fun getLast(): Runner? {
        return runnerDao.getLast()
    }

    fun getNLast(n: Int): List<Runner> {
        return runnerDao.getNLast(n)
    }

    suspend fun insert(runner: Runner) {
        runnerDao.insert(runner)
    }

    suspend fun update(runner: Runner) {
        runnerDao.update(runner)
    }

    suspend fun delete(runner: Runner) {
        runnerDao.delete(runner)
    }

    suspend fun deleteAll() {
        runnerDao.deleteAll()
    }
}

class RunnerViewModel(application: Application) : AndroidViewModel(application) {

    val runners: LiveData<List<Runner>>
    private val repository: RunnerRepository

    init {
        val runnerDao = TZDatabase.getInstance(application).runnerDao()
        repository = RunnerRepository(runnerDao)
        runners = repository.runners
    }

    fun getByID(id: Int) = repository.getByID(id)

    fun getLast() = repository.getLast()

    fun getNLast(n: Int) = repository.getNLast(n)

    fun insert(runner: Runner) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(runner)
    }

    fun update(runner: Runner) = viewModelScope.launch(Dispatchers.IO) {
        repository.update(runner)
    }

    fun delete(runner: Runner) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(runner)
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAll()
    }
}
