package com.example.turisticky_zavod

import android.nfc.NfcAdapter
import android.nfc.tech.MifareClassic
import android.widget.Toast
import java.nio.charset.Charset

class MyNfcHelper {

    var state = NfcState.READ

    fun readPerson(chip: MifareClassic): Person {
        return Person(1, "", "")
    }

    fun writePersonOnChip(person: Person) {
        val id_bytes = person.id.toString().toByteArray(Charset.forName("ISO-8859-2"))
        val name_bytes = stringToByteArraySplits(person.name)
        val team_bytes = stringToByteArraySplits(person.team)
    }

    fun stringToByteArraySplits(str: String): List<ByteArray> {
        val bytes = str.toByteArray(Charset.forName("ISO-8859-2"))
        val byteList = bytes.toList()
        val splitList = mutableListOf<ByteArray>()

        var start = 0
        while (start < byteList.size) {
            val end = (start + 16).coerceAtMost(byteList.size)
            val split = byteList.subList(start, end).toByteArray()
            if (split.size < 16) {
                val newSplit = ByteArray(16) { i -> if (i < split.size) split[i] else 0 }
                splitList.add(newSplit)
            } else {
                splitList.add(split)
            }
            start += 16
        }

        return splitList
    }

    fun checkNfcAvailability(nfcAdapter: NfcAdapter?): NfcAvailability {
        return if (nfcAdapter == null)
            NfcAvailability.NOT_SUPPORTED
        else {
            if (nfcAdapter.isEnabled)
                NfcAvailability.READY
            else
                NfcAvailability.OFF
        }
    }

    enum class NfcState {
        READ, WRITE
    }
    enum class NfcAvailability {
        READY, OFF, NOT_SUPPORTED
    }
}
