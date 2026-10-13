package au.prism.photos.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets (the stored session JSON with the Plex tokens, and the upload settings
 * JSON with the SMB/WebDAV passwords) with an AES-256-GCM key held in the Android Keystore. The
 * key never leaves the device's secure hardware and is not included in backups, so a copied or
 * restored data file is useless on its own. androidx.security-crypto is deprecated, hence this
 * small helper on the platform APIs.
 *
 * Stored form: "enc1:" + base64(12 byte IV + ciphertext + tag). Anything without the prefix is
 * treated as legacy plaintext, which callers re-save encrypted on first read.
 */
object SecretCipher {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "prism_secrets_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "enc1:"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    @Synchronized
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** Encrypts [plain]. Throws if the Keystore is unusable; see [encryptOrPlain]. */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(iv + sealed, Base64.NO_WRAP)
    }

    /** Decrypts a value written by [encrypt]. Throws if the key is gone or the data was altered. */
    fun decrypt(stored: String): String {
        require(isEncrypted(stored)) { "Not an encrypted value" }
        val bytes = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    /**
     * Encrypts [plain], falling back to plaintext (still in app private storage) if the Keystore
     * is broken on this device, so a faulty Keystore cannot lock people out of the app.
     */
    fun encryptOrPlain(plain: String): String = try {
        encrypt(plain)
    } catch (e: Exception) {
        Diagnostics.log("Secure storage unavailable, saving without encryption (${e::class.java.simpleName})")
        plain
    }

    /**
     * Reads a stored value of either form. Returns the plaintext and whether it should be re-saved
     * encrypted (legacy plaintext), or null if an encrypted value can no longer be decrypted.
     */
    fun read(stored: String): Pair<String, Boolean>? {
        if (!isEncrypted(stored)) return stored to true
        return try {
            decrypt(stored) to false
        } catch (e: Exception) {
            Diagnostics.log("Couldn't decrypt stored data (${e::class.java.simpleName}); it will need to be entered again")
            null
        }
    }
}
