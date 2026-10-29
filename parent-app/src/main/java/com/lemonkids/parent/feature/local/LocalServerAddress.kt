package com.lemonkids.parent.feature.local

/** Accept only a literal loopback or private IPv4 address with an explicit TCP port. */
internal fun parseLocalServerAddress(input: String): String? {
    val parts = input.trim().split(':')
    if (parts.size != 2 || !parts[1].matches(Regex("[0-9]{1,5}"))) return null
    val port = parts[1].toIntOrNull() ?: return null
    if (port !in 1..65535) return null

    val octets = parts[0].split('.')
    if (octets.size != 4 || octets.any { !it.matches(Regex("0|[1-9][0-9]{0,2}")) }) return null
    val numbers = octets.map { it.toIntOrNull() ?: return null }
    if (numbers.any { it !in 0..255 }) return null
    val privateAddress = numbers[0] == 10 ||
        (numbers[0] == 172 && numbers[1] in 16..31) ||
        (numbers[0] == 192 && numbers[1] == 168) ||
        numbers == listOf(127, 0, 0, 1)
    return if (privateAddress) "http://${parts[0]}:$port" else null
}
