package com.lemonkids.parent.feature.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalServerAddressTest {
    @Test fun acceptsPrivateAndLoopbackAddresses() {
        assertEquals("http://192.168.1.7:54421", parseLocalServerAddress(" 192.168.1.7:54421 "))
        assertEquals("http://10.0.0.1:1", parseLocalServerAddress("10.0.0.1:1"))
        assertEquals("http://172.31.255.255:65535", parseLocalServerAddress("172.31.255.255:65535"))
        assertEquals("http://127.0.0.1:54421", parseLocalServerAddress("127.0.0.1:54421"))
    }

    @Test fun rejectsInvalidAndPublicAddresses() {
        listOf("8.8.8.8:54421", "172.32.0.1:80", "127.0.0.2:80", "192.168.1.1:0",
            "10.0.0.1:65536", "010.0.0.1:80", "192.168.1.256:80", "localhost:80",
            "http://192.168.1.1:80", "192.168.1.1:80/path", "192.168.1.1")
            .forEach { assertNull(it, parseLocalServerAddress(it)) }
    }
}
