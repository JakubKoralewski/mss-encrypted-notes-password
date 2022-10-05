package com.example.composetest

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
const val ALGORITHM = "PBKDF2WithHmacSHA1"
const val KEY_SPEC = "AES"
const val ITERATIONS = 10000
const val KEY_SIZE = 256
const val SALT_SIZE = KEY_SIZE / 8

class NoteEncoder {
    public val key: SecretKeySpec
    public val salt: ByteArray
    public val iv: ByteArray

    constructor(password: String) {
        salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)
        key = calculateKey(password, salt)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        iv = ByteArray(cipher.blockSize)
        SecureRandom().nextBytes(iv)
    }

    constructor(_key: SecretKeySpec, _salt: ByteArray, _iv: ByteArray) {
        key = _key
        salt = _salt
        iv = _iv
    }

    fun toDb(): ByteArray {
        val key = key.encoded
        val salt = salt
        val iv = iv
        return key.plus(salt).plus(iv)
    }

    fun encode(string: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val ivParams = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, ivParams)
        return cipher.doFinal(string.toByteArray())
    }

    companion object {
        fun fromDb(ba: ByteArray): NoteEncoder {
            val keySizeBytes = KEY_SIZE/8;
            val keyString = ba.copyOfRange(0, keySizeBytes) // 0..32
            val key = SecretKeySpec(keyString, KEY_SPEC)
            val salt = ba.copyOfRange(keySizeBytes, keySizeBytes + SALT_SIZE) // 32..64
            val iv = ba.copyOfRange(keySizeBytes + SALT_SIZE, keySizeBytes + SALT_SIZE + Cipher.getInstance(TRANSFORMATION).blockSize) // 64..80

            return NoteEncoder(key, salt, iv)
        }
    }
}

class EncodedString {
    val encodedString: ByteArray

    constructor(string: String, password: NoteEncoder) {
        encodedString = password.encode(string)
    }

    constructor(es: ByteArray) {
       encodedString = es
    }

    override fun toString(): String = encodedString.toString(Charsets.UTF_8)

    fun decode(password: String, ne: NoteEncoder): String {
        val key = calculateKey(password, ne.salt)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(ne.iv))
        return String(cipher.doFinal(encodedString))
    }
}

private fun calculateKey(
    password: String,
    salt: ByteArray
): SecretKeySpec =
    SecretKeySpec(
        SecretKeyFactory.getInstance(ALGORITHM)
            .generateSecret(
                PBEKeySpec(
                    password.toCharArray(),
                    salt,
                    ITERATIONS,
                    KEY_SIZE
                )
            ).encoded,
        KEY_SPEC
    )

