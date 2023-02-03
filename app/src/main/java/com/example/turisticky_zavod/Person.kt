package com.example.turisticky_zavod

import android.app.Application
import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.*
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Entity(indices = [Index(value = ["runnerId"], unique = true)])
data class Person(
    val runnerId: Int,
    val name: String,
    val team: String,
    var penaltySeconds: Int = 0,
    var disqualified: Boolean = false,
    val startTime: Long,
    val finishTime: Long? = null,
    var timeWaited: Int,
    @PrimaryKey(autoGenerate = true) val id: Int? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readInt(),
        parcel.readByte() != 0.toByte(),
        parcel.readLong(),
        parcel.readValue(Long::class.java.classLoader) as Long?,
        parcel.readInt(),
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
        parcel.writeInt(timeWaited)
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

@Dao
interface PersonDao {
    @Query("SELECT * FROM person ORDER BY id ASC")
    fun getAll(): LiveData<List<Person>>

    @Query("SELECT * FROM person WHERE runnerId = :id")
    fun getByID(id: Int): Person?

    @Query("SELECT * FROM person ORDER BY id DESC LIMIT 1")
    fun getLast(): Person?

    @Query("SELECT * FROM person ORDER BY id DESC LIMIT :n")
    fun getNLast(n: Int): List<Person>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(person: Person)

    @Delete
    suspend fun delete(person: Person)

    @Query("DELETE FROM person")
    suspend fun deleteAll()

    @Update
    suspend fun update(person: Person)
}

class PersonRepository(private val personDao: PersonDao) {
    val people: LiveData<List<Person>> = personDao.getAll()

    fun getByID(id: Int): Person? {
        return personDao.getByID(id)
    }

    fun getLast(): Person? {
        return personDao.getLast()
    }

    fun getNLast(n: Int): List<Person> {
        return personDao.getNLast(n)
    }

    suspend fun insert(person: Person) {
        personDao.insert(person)
    }

    suspend fun delete(person: Person) {
        personDao.delete(person)
    }

    suspend fun deleteAll() {
        personDao.deleteAll()
    }

    suspend fun update(person: Person) {
        personDao.update(person)
    }
}

class PersonViewModel(application: Application) : AndroidViewModel(application) {

    val people: LiveData<List<Person>>
    private val repository: PersonRepository

    init {
        val personDao = TZDatabase.getInstance(application).personDao()
        repository = PersonRepository(personDao)
        people = repository.people
    }

    fun getPersonByID(id: Int) = repository.getByID(id)

    fun getLastPerson() = repository.getLast()

    fun getNLastPeople(n: Int) = repository.getNLast(n)

    fun addPerson(person: Person) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(person)
    }

    fun deletePerson(person: Person) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(person)
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAll()
    }

    fun updatePerson(person: Person) = viewModelScope.launch(Dispatchers.IO) {
        repository.update(person)
    }
}
