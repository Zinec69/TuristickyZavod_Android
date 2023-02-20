package com.example.turisticky_zavod

import android.nfc.NfcAdapter
import android.nfc.tech.MifareClassic
import android.util.Log
import java.nio.charset.Charset

class NFCHelper {

    fun readRunner(tag: MifareClassic): Runner {
        val start = System.currentTimeMillis()
        var allStr = ""

        var i = 1
        var count = -1
        while (i < tag.blockCount) {
            val blocksInSector = tag.getBlockCountInSector(tag.blockToSector(i))
            if ((i + 1) % blocksInSector == 0) {
                i++
                continue
            }

            if (tag.authenticateSectorWithKeyA(tag.blockToSector(i), MifareClassic.KEY_DEFAULT)) {
                val block = readBlock(tag, i)
                if (count < 0) {
                    try {
                        count = block.toInt()
                    } catch (e: java.lang.NumberFormatException) {
                        throw NFCException("Data jsou nekompletní nebo ve špatném formátu")
                    }
                } else {
                    allStr += block
                }
                Log.d("NFC DEBUG READ", "Block $i: $block")

                i++
                if (--count < 0) break
            } else {
                i += if (i == 1) blocksInSector - 1 else blocksInSector
            }
        }

        if (count >= 0)
            throw NFCException("Poškozený čip, ne všechna data se dala přečíst")

        val allStrArray = allStr.split(";")

        if (allStrArray.size < 6)
            throw NFCException("Data jsou nekompletní nebo ve špatném formátu")

        Log.d("NFC DEBUG READ", "Whole string: $allStr")
        Log.d("NFC DEBUG READ", "String array: $allStrArray")
        Log.d("NFC DEBUG READ", "Tag read in: ${System.currentTimeMillis() - start}ms")

        val checkpointInfoArray = ArrayList<CheckpointInfo>()
        if (allStrArray.size > 6) {
            for (j in 6 until allStrArray.size step 6) {
                checkpointInfoArray.add(
                    CheckpointInfo(
                        allStrArray[j].toInt(),
                        allStrArray[j + 1],
                        allStrArray[j + 2].toLong() * 1000,
                        if (allStrArray[j + 3] == "0") null else allStrArray[j + 3].toLong() * 1000,
                        allStrArray[j + 4].toInt(),
                        allStrArray[j + 5].toInt()
                    )
                )
            }
        }

        return Runner(
            allStrArray[0].toInt(),
            allStrArray[1],
            allStrArray[2],
            allStrArray[3].toLong() * 1000,
            if (allStrArray[4] == "0") null else allStrArray[4].toLong() * 1000,
            allStrArray[5] == "1",
            checkpointInfoArray
        )
    }

    private fun readBlock(tag: MifareClassic, block: Int): String {
        tag.authenticateSectorWithKeyA(tag.blockToSector(block), MifareClassic.KEY_DEFAULT)
        return tag.readBlock(block)
            .dropLastWhile { b -> b == 0.toByte() }
            .toByteArray()
            .toString(Charset.forName("ISO-8859-2"))
    }

    fun writeRunnerOnTag(tag: MifareClassic, runner: Runner) {
        val start = System.currentTimeMillis()
        var allStr = "${runner.runnerId};${runner.name};${runner.team};${runner.startTime / 1000};" +
                     "${if (runner.finishTime != null) runner.finishTime!! / 1000 else 0};${if (runner.disqualified) 1 else 0}"
        for (c in runner.checkpointInfo) {
            allStr += ";${c.checkpointId};${c.refereeName};${c.timeArrived / 1000};" +
                    "${if (c.timeDeparted != null) c.timeDeparted!! / 1000 else 0};${c.timeWaitedSeconds};${c.penaltySeconds}"
        }
        val allByteArrays = stringToByteArraySplits(allStr)
        val arraySizeBytes = allByteArrays.size.toString().toByteArray(Charset.forName("ISO-8859-2"))
        val arraySizeBytesFit = ByteArray(16) { i -> if (i < arraySizeBytes.size) arraySizeBytes[i] else 0 }

        var stage = -1

        var i = 1
        while (i < tag.blockCount && stage < allByteArrays.size) {
            val blocksInSector = tag.getBlockCountInSector(tag.blockToSector(i))
            if ((i + 1) % blocksInSector == 0) {
                i++
                continue
            }

            if (tag.authenticateSectorWithKeyA(tag.blockToSector(i), MifareClassic.KEY_DEFAULT)) {
                if (stage < 0) {
                    Log.d("NFC DEBUG WRITE", "Block $i: ${arraySizeBytesFit.toString(Charset.forName("ISO-8859-2"))}")
                    tag.writeBlock(i, arraySizeBytesFit)
                } else {
                    Log.d("NFC DEBUG WRITE", "Block $i: ${allByteArrays[stage].toString(Charset.forName("ISO-8859-2"))}")
                    tag.writeBlock(i, allByteArrays[stage])
                }
                i++
                stage++
            } else {
                i += if (i == 1) blocksInSector - 1 else blocksInSector
            }
        }

        if (stage < allByteArrays.size)
            throw NFCException("Málo místa na čipu pro zapsání všech dat")

        Log.d("NFC DEBUG WRITE", "Tag written to in: ${System.currentTimeMillis() - start}ms")
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
        return when (nfcAdapter) {
            null -> NfcAvailability.NOT_SUPPORTED
            else -> when (nfcAdapter.isEnabled) {
                true -> NfcAvailability.READY
                false -> NfcAvailability.OFF
            }
        }
    }

    enum class NfcAvailability {
        READY, OFF, NOT_SUPPORTED
    }
}

class NFCException(message: String) : Exception(message)
