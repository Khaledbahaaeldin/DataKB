package io.github.khaledbahaaeldin.emberbyte.data.backup

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer

class BackupCodecTest {
    private val fast = BackupCodec.Params(memoryKiB = 64, iterations = 1, parallelism = 1)

    @Test
    fun argon2id_matches_the_rfc_9106_test_vector() {
        // proves the library is used as Argon2id v1.3: RFC 9106 section 5.3
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(3).withMemoryAsKB(32).withParallelism(4)
                .withSalt(ByteArray(16) { 2 }).withSecret(ByteArray(8) { 3 }).withAdditional(ByteArray(12) { 4 }).build(),
        )
        val tag = ByteArray(32)
        generator.generateBytes(ByteArray(32) { 1 }, tag)
        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", tag.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun a_sealed_backup_opens_with_the_same_passphrase() {
        val plain = "hello backup".toByteArray()
        assertArrayEquals(plain, BackupCodec.open(BackupCodec.seal(plain, "correct horse".toCharArray(), fast), "correct horse".toCharArray()))
    }

    @Test
    fun the_default_production_parameters_work_and_are_stored_in_the_header() {
        val sealed = BackupCodec.seal(ByteArray(10) { it.toByte() }, "correct horse".toCharArray())
        val header = ByteBuffer.wrap(sealed, 8 + 16, 12)
        assertEquals(65_536, header.int)
        assertEquals(3, header.int)
        assertEquals(1, header.int)
        assertEquals(10, BackupCodec.open(sealed, "correct horse".toCharArray()).size)
    }

    @Test
    fun two_seals_of_the_same_data_differ() {
        val plain = "repeat data".toByteArray()
        val passphrase = "correct horse".toCharArray()
        val seal1 = BackupCodec.seal(plain, passphrase, fast)
        val seal2 = BackupCodec.seal(plain, passphrase, fast)
        assertFalse(seal1.contentEquals(seal2))
    }

    @Test
    fun a_wrong_passphrase_is_reported_as_the_passphrase() {
        val sealed = BackupCodec.seal(byteArrayOf(1, 2, 3), "correct horse".toCharArray(), fast)
        val e = assertThrows(BackupFormatException::class.java) { BackupCodec.open(sealed, "wrong horse!".toCharArray()) }
        assertEquals("passphrase", e.field)
    }

    @Test
    fun a_flipped_ciphertext_or_header_byte_fails_the_same_way() {
        val sealed = BackupCodec.seal(ByteArray(100) { it.toByte() }, "correct horse".toCharArray(), fast)
        for (index in listOf(sealed.size - 1, sealed.size / 2, 8 + 3, 8 + 16 + 12 + 3)) {     // last byte, middle, salt, nonce
            val copy = sealed.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertEquals("passphrase", assertThrows(BackupFormatException::class.java) { BackupCodec.open(copy, "correct horse".toCharArray()) }.field)
        }
    }

    @Test
    fun a_bad_magic_a_truncated_file_and_empty_input_are_a_file_problem() {
        val sealed = BackupCodec.seal(byteArrayOf(1), "correct horse".toCharArray(), fast)
        for (bad in listOf(ByteArray(0), sealed.copyOf(20), sealed.copyOf().also { it[0] = 'X'.code.toByte() })) {
            assertEquals("file", assertThrows(BackupFormatException::class.java) { BackupCodec.open(bad, "correct horse".toCharArray()) }.field)
        }
    }

    @Test
    fun hostile_parameters_in_the_header_are_refused_before_any_work() {
        fun withParams(memory: Int, iterations: Int, parallelism: Int): ByteArray {
            val sealed = BackupCodec.seal(byteArrayOf(1), "correct horse".toCharArray(), fast)
            ByteBuffer.wrap(sealed, 8 + 16, 12).putInt(memory).putInt(iterations).putInt(parallelism)
            return sealed
        }
        for (bad in listOf(
            withParams(BackupCodec.MAX_MEMORY_KIB + 1, 1, 1),
            withParams(64, BackupCodec.MAX_ITERATIONS + 1, 1),
            withParams(64, 1, 99),
            withParams(0, 1, 1),
            withParams(64, 0, 1),
            withParams(64, 1, 0)
        )) {
            assertEquals("file", assertThrows(BackupFormatException::class.java) { BackupCodec.open(bad, "correct horse".toCharArray()) }.field)
        }
    }

    @Test
    fun the_codec_does_not_modify_the_callers_passphrase() {
        val passphrase = "correct horse".toCharArray()
        val copy = passphrase.copyOf()
        val plain = "sensitive data".toByteArray()
        val sealed = BackupCodec.seal(plain, passphrase, fast)
        assertArrayEquals(copy, passphrase)
        BackupCodec.open(sealed, passphrase)
        assertArrayEquals(copy, passphrase)
    }

    @Test
    fun an_empty_plaintext_round_trips() {
        val plain = ByteArray(0)
        val sealed = BackupCodec.seal(plain, "correct horse".toCharArray(), fast)
        assertArrayEquals(plain, BackupCodec.open(sealed, "correct horse".toCharArray()))
    }
}
