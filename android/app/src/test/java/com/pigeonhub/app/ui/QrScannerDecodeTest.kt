package com.pigeonhub.app.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BETA-001A: the Windows CLI renders the login QR as a pure black/white PNG
 * (error correction M, 4-module quiet zone). The scanner decodes camera YUV
 * through PlanarYUVLuminanceSource + HybridBinarizer + QRCodeReader; this test
 * round-trips the exact pairing payload through the same decode stack so the
 * CLI rendering contract and the scanner decode contract are pinned together.
 * Optical scanning itself needs a real camera and stays an owner device step.
 */
class QrScannerDecodeTest {

    @Test
    fun scannerDecodeStackReadsTheCliQrContract() {
        val payload = "pigeonhub://login?request_id=plr_0123456789abcdef0123456789abcdef&challenge=phc_0123456789abcdef0123456789abcdef"
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4,
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 400, 400, hints)
        val luminance = ByteArray(matrix.width * matrix.height)
        java.util.Arrays.fill(luminance, 255.toByte())
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) {
                    luminance[y * matrix.width + x] = 0
                }
            }
        }
        val source = PlanarYUVLuminanceSource(luminance, matrix.width, matrix.height, 0, 0, matrix.width, matrix.height, false)
        val text = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
        assertEquals(payload, text)
    }
}
