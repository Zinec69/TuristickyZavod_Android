package com.example.turisticky_zavod

import android.nfc.NfcAdapter
import android.nfc.tech.MifareClassic
import java.nio.charset.Charset

class NFCHelper {

    private val BLOCK_ID = 1
    private val BLOCK_NAME = 2
    private val BLOCK_TEAM = 4

    fun readPerson(tag: MifareClassic): Person {
        val id = readBlock(tag, BLOCK_ID).dropLastWhile { c -> !c.isLetterOrDigit() }.toInt()
        val name = readBlock(tag, BLOCK_NAME).dropLastWhile { c -> !c.isLetterOrDigit() }
        val team = readBlock(tag, BLOCK_TEAM).dropLastWhile { c -> !c.isLetterOrDigit() }
        return Person(id, name, team, null, null, null)
    }

    private fun readBlock(tag: MifareClassic, block: Int): String {
        tag.authenticateSectorWithKeyA(tag.blockToSector(block), MifareClassic.KEY_DEFAULT)
        return tag.readBlock(block)
            .toString(Charset.forName("ISO-8859-2"))
    }

    fun writePersonOnChip(person: Person) {
        val id_str = person.id.toString().toByteArray(Charset.forName("ISO-8859-2"))
        val id_bytes = ByteArray(16) { i -> if (i < id_str.size) id_str[i] else 0 }
        val name_str = person.name!!.toByteArray(Charset.forName("ISO-8859-2"))
        val name_bytes = ByteArray(16) { i -> if (i < name_str.size) name_str[i] else 0 }
        val team_str = person.team!!.toByteArray(Charset.forName("ISO-8859-2"))
        val team_bytes = ByteArray(16) { i -> if (i < team_str.size) team_str[i] else 0 }
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
        return if (nfcAdapter == null) {
            NfcAvailability.NOT_SUPPORTED
        } else {
            if (nfcAdapter.isEnabled)
                NfcAvailability.READY
            else
                NfcAvailability.OFF
        }
    }

    enum class NfcAvailability {
        READY, OFF, NOT_SUPPORTED
    }
}
