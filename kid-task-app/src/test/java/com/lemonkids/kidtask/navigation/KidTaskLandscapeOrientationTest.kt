package com.lemonkids.kidtask.navigation

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element

class KidTaskLandscapeOrientationTest {
    @Test
    fun mainActivityIsLockedToLandscape() {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val activities = document.getElementsByTagName("activity")
        val mainActivity = (0 until activities.length)
            .map { activities.item(it) as Element }
            .firstOrNull { it.getAttributeNS(androidNamespace, "name") == ".MainActivity" }

        assertNotNull(mainActivity)
        assertEquals("landscape", mainActivity!!.getAttributeNS(androidNamespace, "screenOrientation"))
    }
}
