package de.athena.app

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/** Four-digit, single-use pairing challenge. Only a local touchscreen action opens a new window. */
class PairingGate(private val now: () -> Long = System::currentTimeMillis) {
    private var pin = ""
    private var expires = 0L
    private var attempts = 0
    @Synchronized fun open(): String {
        pin = String.format(Locale.ROOT, "%04d", SecureRandom().nextInt(10000))
        expires = now() + 300000; attempts = 0
        return pin
    }
    @Synchronized fun visible(): String = if (now() < expires && attempts < 5) pin else ""
    @Synchronized fun consume(value: String): Boolean {
        if (visible().isEmpty()) return false
        attempts++
        val valid = value.matches(Regex("[0-9]{4}")) && MessageDigest.isEqual(value.toByteArray(), pin.toByteArray())
        if (valid || attempts >= 5) {pin="";expires=0}
        return valid
    }
}
