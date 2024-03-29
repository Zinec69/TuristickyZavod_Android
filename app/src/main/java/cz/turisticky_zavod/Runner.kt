package cz.turisticky_zavod

import android.app.Application
import android.os.Parcelable
import androidx.lifecycle.*
import androidx.room.*
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonClass(generateAdapter = true)
@Entity(indices = [Index(value = ["startNumber"], unique = true)])
data class Runner(
    val startNumber: Int,
    val name: String,
    val team: String,
    val startTime: Long,
    var finishTime: Long? = null,
    var disqualified: Boolean = false,
    var checkpointInfo: ArrayList<CheckpointInfo> = ArrayList(),
    @PrimaryKey(autoGenerate = true)
    @Json(ignore = true)
    val id: Int? = null
) : Parcelable

@Dao
interface RunnerDao {
    @Query("SELECT * FROM runner ORDER BY id ASC")
    fun getAll(): LiveData<List<Runner>>

    @Query("SELECT * FROM runner WHERE startNumber = :startNumber")
    fun getByStartNumber(startNumber: Int): Runner?

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

    fun getByStartNumber(startNumber: Int): Runner? {
        return runnerDao.getByStartNumber(startNumber)
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

    fun getByStartNumber(startNumber: Int) = repository.getByStartNumber(startNumber)

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

    fun exportToJson(): String {
        val start = System.currentTimeMillis()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .add(CheckpointInfoArrayListMoshiAdapter())
            .build()
        val type = Types.newParameterizedType(List::class.java, Runner::class.java)
        val jsonAdapter: JsonAdapter<List<Runner>> = moshi.adapter<List<Runner>?>(type).indent("\t")

        val json = jsonAdapter.toJson(runners.value)

        // Log.d("JSON EXPORT", "Done in ${System.currentTimeMillis() - start} ms")

        return json
    }
}
