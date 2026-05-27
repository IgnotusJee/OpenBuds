package dev.ignotus.openbuds.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for QCY TLV frame build/parse correctness against the
 * `references/QCY/outshell/sources/com/qcymall/qcylibrary/dataBean/DataAnalyse.java`
 * reference implementation.
 */
class QcyProtocolFrameTest {

    private fun hex(s: String): ByteArray =
        s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    // ── Frame build ─────────────────────────────────────────────────

    @Test
    fun buildReadRequest_matchesProtocolDocExample() {
        // Doc §3.2: querying CMD 22 (channel balance) → FF 03 FE 01 16
        val frame = QcyProtocol.buildReadRequest(0x16)
        assertEquals(hex("FF 03 FE 01 16").toList(), frame.toList())
    }

    @Test
    fun buildSimpleCommand_matchesProtocolDocExample() {
        // Doc §3.3: setting channel balance to center (0x32) → FF 03 16 01 32
        val frame = QcyProtocol.buildSimpleCommand(0x16, byteArrayOf(0x32))
        assertEquals(hex("FF 03 16 01 32").toList(), frame.toList())
    }

    @Test
    fun buildSingleValueCommand_isShorthandForSingleByte() {
        val a = QcyProtocol.buildSingleValueCommand(0x16, 0x32)
        val b = QcyProtocol.buildSimpleCommand(0x16, byteArrayOf(0x32))
        assertEquals(a.toList(), b.toList())
    }

    @Test
    fun buildEqCommandNew_oneBand_hasCorrectOuterLength() {
        // body = [eqType:1, masterGain:2, freq:2, gain:2, q:2, bandType:1] = 10 bytes
        // outer LEN = CMD(1) + DataLen(1) + body(10) = 12 (0x0C)
        // total frame = SOF(1) + LEN(1) + outer(12) = 14 bytes
        val band = QcyEqBand(frequency = 1000, gain = 0f, q = 1.0f, bandType = 0)
        val frame = QcyProtocol.buildEqCommandNew(eqType = 0, masterGain = 0f, bands = listOf(band))
        assertEquals("Total frame length must be 14 bytes", 14, frame.size)
        assertEquals("SOF byte", 0xFF.toByte(), frame[0])
        assertEquals("Outer LEN must be 0x0C (12)", 0x0C.toByte(), frame[1])
        assertEquals("CMD byte must be CMDID_MULTIEQ2 (0x22)", 0x22.toByte(), frame[2])
        assertEquals("Inner DataLen must be 0x0A (10)", 0x0A.toByte(), frame[3])
    }

    @Test
    fun buildEqCommandOld_oneBand_hasCorrectOuterLength() {
        // body = [eqType:1, masterGain:2, freq:2, gain:2, q:2] = 9 bytes
        // outer LEN = 11 (0x0B); total = 13 bytes
        val band = QcyEqBand(frequency = 1000, gain = 0f, q = 1.0f)
        val frame = QcyProtocol.buildEqCommandOld(eqType = 0, masterGain = 0f, bands = listOf(band))
        assertEquals(13, frame.size)
        assertEquals(0xFF.toByte(), frame[0])
        assertEquals(0x0B.toByte(), frame[1])
        assertEquals(0x20.toByte(), frame[2]) // CMDID_MULTIEQ
        assertEquals(0x09.toByte(), frame[3])
    }

    // ── Frame parse ─────────────────────────────────────────────────

    @Test
    fun parseFrame_singleEntry_returnsOneTlv() {
        // FF 03 16 01 32 → one TLV: cmd=0x16, data=[0x32]
        val entries = QcyProtocol.parseFrame(hex("FF 03 16 01 32"))
        assertEquals(1, entries.size)
        assertEquals(0x16.toByte(), entries[0].cmdId)
        assertEquals(listOf(0x32.toByte()), entries[0].data.toList())
    }

    @Test
    fun parseFrame_multiEntry_returnsAllTlvs() {
        // FF 06 0C 01 01 07 01 64 → two TLVs:
        //   cmd=0x0C (NOISE_MODE), data=[0x01]
        //   cmd=0x07 (NOISE_VALUE), data=[0x64]
        val entries = QcyProtocol.parseFrame(hex("FF 06 0C 01 01 07 01 64"))
        assertEquals(2, entries.size)
        assertEquals(0x0C.toByte(), entries[0].cmdId)
        assertEquals(listOf(0x01.toByte()), entries[0].data.toList())
        assertEquals(0x07.toByte(), entries[1].cmdId)
        assertEquals(listOf(0x64.toByte()), entries[1].data.toList())
    }

    @Test
    fun parseFrame_strictLength_rejectsTooShort() {
        // Doc parser (DataAnalyse.java:28) requires raw.size == declaredLen + 2.
        // FF 03 FE 01 → claims 3 body bytes but only 2 present.
        assertTrue(QcyProtocol.parseFrame(hex("FF 03 FE 01")).isEmpty())
    }

    @Test
    fun parseFrame_strictLength_rejectsTooLong() {
        // FF 03 FE 01 16 00 → claims 3 body bytes, 4 present → invalid
        assertTrue(QcyProtocol.parseFrame(hex("FF 03 FE 01 16 00")).isEmpty())
    }

    @Test
    fun parseFrame_truncatedTlv_returnsEmpty() {
        // FF 04 16 02 32 → claims 4 body bytes; inner TLV says cmd=0x16 dataLen=2
        // but only 1 data byte fits → whole frame invalid
        assertTrue(QcyProtocol.parseFrame(hex("FF 04 16 02 32")).isEmpty())
    }

    @Test
    fun parseFrame_missingSof_returnsEmpty() {
        assertTrue(QcyProtocol.parseFrame(hex("00 03 FE 01 16")).isEmpty())
    }

    @Test
    fun parseFrame_emptyOrTooSmall_returnsEmpty() {
        assertTrue(QcyProtocol.parseFrame(byteArrayOf()).isEmpty())
        assertTrue(QcyProtocol.parseFrame(byteArrayOf(0xFF.toByte())).isEmpty())
    }

    // ── EQ build/parse roundtrip ────────────────────────────────────

    @Test
    fun parseEqResponseNew_roundtrip_oneBand() {
        val band = QcyEqBand(frequency = 1000, gain = 3.5f, q = 1.2f, bandType = 0)
        val frame = QcyProtocol.buildEqCommandNew(eqType = 0, masterGain = 1.5f, bands = listOf(band))
        // Strip [SOF][LEN][CMD][DataLen] = 4 bytes to get the EQ body
        val body = frame.copyOfRange(4, frame.size)
        val parsed = QcyProtocol.parseEqResponseNew(body)
        assertEquals(0, parsed.eqType)
        assertEquals(1.5f, parsed.masterGain, 0.001f)
        assertEquals(1, parsed.bands.size)
        val parsedBand = parsed.bands[0]
        assertEquals(1000, parsedBand.frequency)
        assertEquals(3.5f, parsedBand.gain, 0.001f)
        assertEquals(1.2f, parsedBand.q, 0.001f)
        assertEquals(0, parsedBand.bandType)
    }

    @Test
    fun parseEqResponseNew_negativeGain_decodedCorrectly() {
        val band = QcyEqBand(frequency = 250, gain = -6.4f, q = 0.7f, bandType = 1)
        val frame = QcyProtocol.buildEqCommandNew(eqType = 0, masterGain = 0f, bands = listOf(band))
        val body = frame.copyOfRange(4, frame.size)
        val parsed = QcyProtocol.parseEqResponseNew(body)
        assertEquals(-6.4f, parsed.bands[0].gain, 0.001f)
        assertEquals(1, parsed.bands[0].bandType)
    }
}
