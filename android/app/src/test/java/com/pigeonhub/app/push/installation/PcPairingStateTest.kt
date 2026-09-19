package com.pigeonhub.app.push.installation

import org.junit.Assert.assertEquals
import org.junit.Test

/** BETA-003A: stored pairing-state values never fabricate or lose a pairing. */
class PcPairingStateTest {

    @Test
    fun `known values round-trip`() {
        assertEquals(PcPairingState.NOT_PAIRED, pcPairingStateFrom("NOT_PAIRED"))
        assertEquals(PcPairingState.PAIRED, pcPairingStateFrom("PAIRED"))
        assertEquals(PcPairingState.STALE, pcPairingStateFrom("STALE"))
    }

    @Test
    fun `missing value is not paired`() {
        assertEquals(PcPairingState.NOT_PAIRED, pcPairingStateFrom(null))
        assertEquals(PcPairingState.NOT_PAIRED, pcPairingStateFrom(""))
    }

    @Test
    fun `unknown or corrupted value never fabricates a pairing`() {
        assertEquals(PcPairingState.NOT_PAIRED, pcPairingStateFrom("PAIRED "))
        assertEquals(PcPairingState.NOT_PAIRED, pcPairingStateFrom("paired"))
        assertEquals(PcPairingState.NOT_PAIRED, pcPairingStateFrom("CONNECTED"))
    }
}
