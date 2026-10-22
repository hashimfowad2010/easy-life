package com.easylife.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the random SQLCipher passphrase once and keeps it wrapped (AES-256-GCM) by a key that lives in the
 * Android Keystore and can never leave the phone. The wrapped passphrase is stored in no-backup storage.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val wrappedFile: File get() = File(context.noBackupFilesDir, "db.key.enc")

    fun getOrCreatePassphrase(): ByteArray {
        val f = wrappedFile
        if (f.exists()) return unwrap(f.readBytes())
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        f.writeBytes(wrap(passphrase))
        return passphrase
    }

    /** True if a wrapped key exists (i.e. a database was created before). */
    fun hasKey(): Boolean = wrappedFile.exists()

    /** Removes the wrapped passphrase and the Keystore key. The old database can no longer be opened. */
    fun destroy() {
        wrappedFile.delete()
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(ALIAS)
        }
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + ct
    }

    private fun unwrap(data: ByteArray): ByteArray {
        val ivLen = data[0].toInt()
        val iv = data.copyOfRange(1, 1 + ivLen)
        val ct = data.copyOfRange(1 + ivLen, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "easylife_db_wrap_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
