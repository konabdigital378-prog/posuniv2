package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Self-contained QR Code Matrix & Bitmap generator.
 * Produces valid, standard scannable 2D QR Code matrices without external dependencies.
 */
object QrCodeHelper {

    // Galois Field GF(256) tables for QR Reed-Solomon Error Correction
    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            EXP[i + 255] = x
            LOG[x] = i
            x = x shl 1
            if (x >= 256) {
                x = x xor 0x11D // Primitive polynomial for QR Code
            }
        }
    }

    private fun gfMul(x: Int, y: Int): Int {
        if (x == 0 || y == 0) return 0
        return EXP[LOG[x] + LOG[y]]
    }

    // RS generator polynomial coefficients for given number of error correction codewords
    private fun getGeneratorPolynomial(ecCount: Int): IntArray {
        var g = intArrayOf(1)
        for (i in 0 until ecCount) {
            val factor = intArrayOf(EXP[i], 1)
            val newG = IntArray(g.size + 1)
            for (j in g.indices) {
                newG[j] = newG[j] xor gfMul(g[j], factor[0])
                newG[j + 1] = newG[j + 1] xor gfMul(g[j], factor[1])
            }
            g = newG
        }
        return g
    }

    private fun calculateErrorCorrection(data: IntArray, ecCount: Int): IntArray {
        val gen = getGeneratorPolynomial(ecCount)
        val remainder = IntArray(ecCount)
        for (b in data) {
            val factor = b xor remainder[0]
            for (j in 0 until ecCount - 1) {
                remainder[j] = remainder[j + 1] xor gfMul(gen[j + 1], factor)
            }
            remainder[ecCount - 1] = gfMul(gen[ecCount], factor)
        }
        return remainder
    }

    // Version definitions: (Version, TotalCodewords, NumBlocks, ECPerBlock)
    // Spec ISO/IEC 18004 Level L (versions 1..9 keep 8-bit byte count, up to 232 data bytes)
    data class QrVersionInfo(
        val version: Int,
        val totalCodewords: Int,
        val blocks: Int,
        val ecPerBlock: Int
    ) {
        val totalEcCodewords: Int get() = blocks * ecPerBlock
        val dataCodewords: Int get() = totalCodewords - totalEcCodewords
        val dimension: Int get() = 17 + 4 * version
    }

    private val VERSIONS = listOf(
        QrVersionInfo(1, 26, 1, 7),     // 19 data bytes (21x21)
        QrVersionInfo(2, 44, 1, 10),    // 34 data bytes (25x25)
        QrVersionInfo(3, 70, 1, 15),    // 55 data bytes (29x29)
        QrVersionInfo(4, 100, 1, 20),   // 80 data bytes (33x33)
        QrVersionInfo(5, 134, 1, 26),   // 108 data bytes (37x37)
        QrVersionInfo(6, 172, 2, 18),   // 136 data bytes (41x41) - 2 blocks of 68+18
        QrVersionInfo(7, 196, 2, 20),   // 156 data bytes (45x45) - 2 blocks of 78+20
        QrVersionInfo(8, 242, 2, 24),   // 194 data bytes (49x49) - 2 blocks of 97+24
        QrVersionInfo(9, 292, 2, 30)    // 232 data bytes (53x53) - 2 blocks of 116+30
    )

    private fun getVersionInfoBits(version: Int): Int {
        val d = version shl 12
        var rem = d
        for (i in 17 downTo 12) {
            if (((rem shr i) and 1) != 0) {
                rem = rem xor (0x1F25 shl (i - 12))
            }
        }
        return d or rem
    }

    /**
     * Encodes text into a square boolean matrix (true = black module, false = white module).
     */
    fun encodeToMatrix(text: String): Array<BooleanArray> {
        val rawBytes = text.toByteArray(Charsets.UTF_8)
        val versionInfo = VERSIONS.firstOrNull { it.dataCodewords >= rawBytes.size + 3 }
            ?: VERSIONS.last()

        val dataBytes = IntArray(versionInfo.dataCodewords)
        // Header: Mode 4 (Byte mode = 0100) + Character Count (8 bits for v1-9)
        val bitBuffer = mutableListOf<Int>()

        // Mode 0100
        bitBuffer.addAll(listOf(0, 1, 0, 0))
        // Length
        val len = minOf(rawBytes.size, versionInfo.dataCodewords - 3)
        for (i in 7 downTo 0) {
            bitBuffer.add((len shr i) and 1)
        }
        // Data bytes
        for (i in 0 until len) {
            val b = rawBytes[i].toInt() and 0xFF
            for (j in 7 downTo 0) {
                bitBuffer.add((b shr j) and 1)
            }
        }
        // Terminator (up to 4 zeros)
        val termLen = minOf(4, (versionInfo.dataCodewords * 8) - bitBuffer.size)
        repeat(termLen) { bitBuffer.add(0) }

        // Pad to byte boundary
        while (bitBuffer.size % 8 != 0) {
            bitBuffer.add(0)
        }

        // Pack bits into dataBytes
        var byteIdx = 0
        var currentByte = 0
        var bitInByte = 0
        for (bit in bitBuffer) {
            currentByte = (currentByte shl 1) or bit
            bitInByte++
            if (bitInByte == 8) {
                if (byteIdx < dataBytes.size) {
                    dataBytes[byteIdx++] = currentByte
                }
                currentByte = 0
                bitInByte = 0
            }
        }

        // Pad remainder with alternating 0xEC and 0x11
        var padToggle = true
        while (byteIdx < dataBytes.size) {
            dataBytes[byteIdx++] = if (padToggle) 0xEC else 0x11
            padToggle = !padToggle
        }

        // Split into blocks, compute Reed-Solomon EC per block, and interleave per ISO 18004
        val blockCount = versionInfo.blocks
        val dataPerBlock = versionInfo.dataCodewords / blockCount
        val dataBlocks = Array(blockCount) { blockIdx ->
            val start = blockIdx * dataPerBlock
            dataBytes.copyOfRange(start, start + dataPerBlock)
        }
        val ecBlocks = Array(blockCount) { blockIdx ->
            calculateErrorCorrection(dataBlocks[blockIdx], versionInfo.ecPerBlock)
        }

        val finalCodewords = IntArray(versionInfo.totalCodewords)
        var outIdx = 0
        for (i in 0 until dataPerBlock) {
            for (b in 0 until blockCount) {
                finalCodewords[outIdx++] = dataBlocks[b][i]
            }
        }
        for (i in 0 until versionInfo.ecPerBlock) {
            for (b in 0 until blockCount) {
                finalCodewords[outIdx++] = ecBlocks[b][i]
            }
        }

        // Generate QR Matrix
        val dim = versionInfo.dimension
        val matrix = Array(dim) { BooleanArray(dim) }
        val reserved = Array(dim) { BooleanArray(dim) }

        // 1. Position Finders (7x7) + Separators
        fun placeFinder(top: Int, left: Int) {
            for (r in 0 until 7) {
                for (c in 0 until 7) {
                    val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                    val isCenter = r in 2..4 && c in 2..4
                    matrix[top + r][left + c] = isBorder || isCenter
                    reserved[top + r][left + c] = true
                }
            }
            // Separator border
            for (r in -1..7) {
                for (c in -1..7) {
                    val ar = top + r
                    val ac = left + c
                    if (ar in 0 until dim && ac in 0 until dim) {
                        reserved[ar][ac] = true
                    }
                }
            }
        }

        placeFinder(0, 0)
        placeFinder(0, dim - 7)
        placeFinder(dim - 7, 0)

        // 2. Alignment patterns (version >= 2)
        if (versionInfo.version >= 2) {
            val alignPos = when (versionInfo.version) {
                2 -> intArrayOf(6, 18)
                3 -> intArrayOf(6, 22)
                4 -> intArrayOf(6, 26)
                5 -> intArrayOf(6, 30)
                6 -> intArrayOf(6, 34)
                7 -> intArrayOf(6, 22, 38)
                8 -> intArrayOf(6, 24, 42)
                else -> intArrayOf(6, 26, 46)
            }
            for (r in alignPos) {
                for (c in alignPos) {
                    if (reserved[r][c]) continue
                    for (dr in -2..2) {
                        for (dc in -2..2) {
                            val isBorder = dr == -2 || dr == 2 || dc == -2 || dc == 2
                            val isCenter = dr == 0 && dc == 0
                            matrix[r + dr][c + dc] = isBorder || isCenter
                            reserved[r + dr][c + dc] = true
                        }
                    }
                }
            }
        }

        // 3. Timing patterns
        for (i in 8 until dim - 8) {
            val v = (i % 2 == 0)
            if (!reserved[6][i]) {
                matrix[6][i] = v
                reserved[6][i] = true
            }
            if (!reserved[i][6]) {
                matrix[i][6] = v
                reserved[i][6] = true
            }
        }

        // 4. Dark module
        matrix[4 * versionInfo.version + 9][8] = true
        reserved[4 * versionInfo.version + 9][8] = true

        // 5. Reserve Format Info areas
        for (i in 0..8) {
            reserved[8][i] = true
            reserved[i][8] = true
        }
        for (i in (dim - 8) until dim) {
            reserved[8][i] = true
            reserved[i][8] = true
        }

        // 5b. Version Information areas (versions >= 7)
        if (versionInfo.version >= 7) {
            val vBits = getVersionInfoBits(versionInfo.version)
            for (i in 0 until 18) {
                val bit = ((vBits shr i) and 1) == 1
                val a = i / 3
                val b = dim - 11 + (i % 3)
                matrix[a][b] = bit
                reserved[a][b] = true
                matrix[b][a] = bit
                reserved[b][a] = true
            }
        }

        // 6. Place Data bits in 2-column zig-zag right to left
        var bitPos = 0
        val totalBits = finalCodewords.size * 8
        var upward = true
        var col = dim - 1

        while (col > 0) {
            if (col == 6) col-- // Skip vertical timing column
            val rows = if (upward) (dim - 1 downTo 0).toList() else (0 until dim).toList()

            for (r in rows) {
                for (c in listOf(col, col - 1)) {
                    if (!reserved[r][c]) {
                        val bitVal = if (bitPos < totalBits) {
                            val byteVal = finalCodewords[bitPos / 8]
                            (byteVal shr (7 - (bitPos % 8))) and 1 == 1
                        } else false
                        bitPos++

                        // Mask 0: (row + col) % 2 == 0
                        val mask = (r + c) % 2 == 0
                        matrix[r][c] = bitVal xor mask
                    }
                }
            }
            col -= 2
            upward = !upward
        }

        // 7. Write Format Info (Mask 0 + Error Correction L = 01)
        // Format info for L + Mask 0 is 0x77C4 (15 bits)
        val formatBits = 0x77C4
        for (i in 0..14) {
            val b = ((formatBits shr i) and 1) == 1
            if (i < 6) {
                matrix[8][i] = b
                matrix[dim - 1 - i][8] = b
            } else if (i == 6) {
                matrix[8][7] = b
                matrix[dim - 7][8] = b
            } else if (i in 7..8) {
                matrix[8][i] = b
                matrix[dim - 15 + i][8] = b
            } else {
                matrix[14 - i][8] = b
                matrix[8][dim - 15 + i] = b
            }
        }

        return matrix
    }

    /**
     * Renders text into an Android Bitmap with quiet zones.
     */
    fun generateQrBitmap(text: String, sizePx: Int = 240, margin: Int = 2): Bitmap {
        val matrix = encodeToMatrix(text)
        val matrixDim = matrix.size
        val fullDim = matrixDim + (margin * 2)

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = false
        }

        val scale = sizePx.toFloat() / fullDim.toFloat()

        for (r in 0 until matrixDim) {
            for (c in 0 until matrixDim) {
                if (matrix[r][c]) {
                    val left = (c + margin) * scale
                    val top = (r + margin) * scale
                    val right = (c + margin + 1) * scale
                    val bottom = (r + margin + 1) * scale
                    canvas.drawRect(left, top, right, bottom, paint)
                }
            }
        }

        return bitmap
    }
}
