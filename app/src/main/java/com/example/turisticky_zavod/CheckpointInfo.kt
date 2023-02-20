package com.example.turisticky_zavod

import android.os.Parcelable
import androidx.room.TypeConverter
import com.squareup.moshi.*
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonClass(generateAdapter = true)
data class CheckpointInfo(
    val checkpointId: Int,
    val refereeName: String,
    var timeArrived: Long,
    var timeDeparted: Long? = null,
    var timeWaitedSeconds: Int = 0,
    var penaltySeconds: Int = 0,
) : Parcelable

class CheckpointInfoJsonConverter {
    @TypeConverter
    fun toJSONString(checkpointInfos: ArrayList<CheckpointInfo>): String {
        val moshi: Moshi = Moshi.Builder().add(CheckpointInfoArrayListMoshiAdapter()).build()
        val type = Types.newParameterizedType(ArrayList::class.java, CheckpointInfo::class.java)
        val jsonAdapter: JsonAdapter<ArrayList<CheckpointInfo>> = moshi.adapter(type)
        return jsonAdapter.toJson(checkpointInfos)
    }

    @TypeConverter
    fun fromJSONString(json: String): ArrayList<CheckpointInfo> {
        val moshi: Moshi = Moshi.Builder().add(CheckpointInfoArrayListMoshiAdapter()).build()
        val type = Types.newParameterizedType(ArrayList::class.java, CheckpointInfo::class.java)
        val jsonAdapter: JsonAdapter<ArrayList<CheckpointInfo>> = moshi.adapter(type)
        return jsonAdapter.fromJson(json)!!
    }
}

class CheckpointInfoArrayListMoshiAdapter {
    @ToJson
    fun arrayListToJson(list: ArrayList<CheckpointInfo>): List<CheckpointInfo> = list

    @FromJson
    fun arrayListFromJson(list: List<CheckpointInfo>): ArrayList<CheckpointInfo> = ArrayList(list)
}
