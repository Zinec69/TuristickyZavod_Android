package com.example.turisticky_zavod

import android.nfc.NfcAdapter
import android.nfc.tech.MifareClassic
import android.util.Log
import java.nio.charset.Charset

class NFCHelper {

    fun readPerson(tag: MifareClassic): Person {
        var allStr = ""

        var i = 1
        var count = -1
        while (i < tag.blockCount) {
            if ((i + 1) % 4 == 0) {
                i++
                continue
            }

            if (tag.authenticateSectorWithKeyA(tag.blockToSector(i), MifareClassic.KEY_DEFAULT)) {
                val block = readBlock(tag, i)
                if (count < 0) {
                    count = block.toInt()
                } else {
                    allStr += block
                }
                Log.d("NFC DEBUG READ", "Block $i: $block")

                if (--count < 0) break
            } else {
                i += 4
            }
            i++
        }

        if (count >= 0)
            throw NFCException("Poškozený čip, ne všechna data se dala přečíst")

        val allStrArray = allStr.split(";")

        Log.d("NFC DEBUG READ", "Whole string: $allStr")
        Log.d("NFC DEBUG READ", "String array: $allStrArray")

        return Person(
            allStrArray[0].toInt(),
            allStrArray[1],
            allStrArray[2],
            allStrArray[3].toInt(),
            allStrArray[4] == "1",
            allStrArray[5].toLong(),
            if (allStrArray[6] == "0") null else allStrArray[6].toLong(),
            allStrArray[7].toInt(),
            null
        )
    }

    private fun readBlock(tag: MifareClassic, block: Int): String {
        tag.authenticateSectorWithKeyA(tag.blockToSector(block), MifareClassic.KEY_DEFAULT)
        return tag.readBlock(block)
            .dropLastWhile { b -> b == 0.toByte() }
            .toByteArray()
            .toString(Charset.forName("ISO-8859-2"))
    }

    fun writePersonOnTag(tag: MifareClassic, person: Person) {
        val allStr = "${person.runnerId};${person.name};${person.team};${person.penaltySeconds};" +
                "${if (person.disqualified) 1 else 0};${person.startTime};${person.finishTime ?: 0};${person.timeWaited}"
        val allByteArrays = stringToByteArraySplits(allStr)
        val arraySizeStr = allByteArrays.size.toString().toByteArray(Charset.forName("ISO-8859-2"))
        val arraySizeBytes = ByteArray(16) { i -> if (i < arraySizeStr.size) arraySizeStr[i] else 0 }

        var stage = -1

        var i = 1
        while (i < tag.blockCount && stage < allByteArrays.size) {
            if ((i + 1) % 4 == 0) {
                i++
                continue
            }

            if (tag.authenticateSectorWithKeyA(tag.blockToSector(i), MifareClassic.KEY_DEFAULT)) {
                if (stage < 0) {
                    Log.d("NFC DEBUG WRITE", "Block $i: ${arraySizeBytes.toString(Charset.forName("ISO-8859-2"))}")
                    tag.writeBlock(i, arraySizeBytes)
                } else {
                    Log.d("NFC DEBUG WRITE", "Block $i: ${allByteArrays[stage].toString(Charset.forName("ISO-8859-2"))}")
                    tag.writeBlock(i, allByteArrays[stage])
                }
                stage++
            } else {
                i += 4
            }
            i++
        }

        if (stage < allByteArrays.size)
            throw NFCException("Málo místa na čipu pro zapsání všech dat")
    }

    private fun stringToByteArraySplits(str: String): List<ByteArray> {
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

class NFCException(message: String) : Exception(message)

