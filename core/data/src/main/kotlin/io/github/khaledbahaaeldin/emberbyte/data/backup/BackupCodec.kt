package io.github.khaledbahaaeldin.emberbyte.data.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

class BackupFormatException(val field: String, message: String) : Exception(message)     // field is "passphrase" or "file"

object BackupCodec {
    const val MAGIC = "EMBBKP01"
    private const val SALT = 16
    private const val NONCE = 12
    private const val HEADER = 8 + SALT + 12 + NONCE      // 48
    data class Params(val memoryKiB: Int = 65_536, val iterations: Int = 3, val parallelism: Int = 1)
    const val MAX_MEMORY_KIB = 65_536
    const val MAX_ITERATIONS = 10
    const val MAX_PARALLELISM = 4

    internal var keyDerivation: (CharArray, ByteArray, Params) -> ByteArray = ::deriveKey

    fun seal(plain: ByteArray, passphrase: CharArray, params: Params = Params(), random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT).also(random::nextBytes)
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER).put(MAGIC.toByteArray(Charsets.US_ASCII)).put(salt)
            .putInt(params.memoryKiB).putInt(params.iterations).putInt(params.parallelism).put(nonce).array()
        val key = try {
            keyDerivation(passphrase, salt, params)
        } catch (_: OutOfMemoryError) {
            throw BackupFormatException("file", "This backup needs more memory than this device can give")
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            return header + cipher.doFinal(plain)
        } finally { key.fill(0) }
    }

    fun open(sealed: ByteArray, passphrase: CharArray): ByteArray {
        if (sealed.size < HEADER + 16) throw BackupFormatException("file", "This is not an Emberbyte backup")
        if (String(sealed, 0, 8, Charsets.US_ASCII) != MAGIC) throw BackupFormatException("file", "This is not an Emberbyte backup")
        val buffer = ByteBuffer.wrap(sealed, 8, HEADER - 8)
        val salt = ByteArray(SALT).also(buffer::get)
        val params = Params(buffer.int, buffer.int, buffer.int)
        if (params.memoryKiB !in (8 * params.parallelism)..MAX_MEMORY_KIB || params.iterations !in 1..MAX_ITERATIONS || params.parallelism !in 1..MAX_PARALLELISM) {
            throw BackupFormatException("file", "The backup uses settings this version does not accept")
        }
        val nonce = ByteArray(NONCE).also(buffer::get)
        val key = try {
            keyDerivation(passphrase, salt, params)
        } catch (_: OutOfMemoryError) {
            throw BackupFormatException("file", "This backup needs more memory than this device can give")
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(sealed, 0, HEADER)
            return cipher.doFinal(sealed, HEADER, sealed.size - HEADER)
        } catch (_: AEADBadTagException) {
            throw BackupFormatException("passphrase", "Wrong passphrase or damaged file")
        } catch (_: GeneralSecurityException) {
            throw BackupFormatException("passphrase", "Wrong passphrase or damaged file")
        } finally { key.fill(0) }
    }

    internal fun deriveKey(passphrase: CharArray, salt: ByteArray, params: Params): ByteArray {
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt).withMemoryAsKB(params.memoryKiB).withIterations(params.iterations).withParallelism(params.parallelism).build(),
        )
        return ByteArray(32).also { generator.generateBytes(passphrase, it) }
    }
}
