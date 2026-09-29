package au.prism.photos.data.upload

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SubFolderPatternTest {
    /** 15 March 2026, 10:00 local time, used for every test so the expected strings are fixed. */
    private val whenMillis: Long = Calendar.getInstance(TimeZone.getDefault()).apply {
        set(2026, Calendar.MARCH, 15, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `resolves device, year and month tokens`() {
        val resolved = SubFolderPattern.resolve("Uploads/{device}/{yyyy}/{MM}", "Pixel 9", whenMillis)
        assertEquals("Uploads/Pixel 9/2026/03", resolved)
    }

    @Test
    fun `resolves day token`() {
        val resolved = SubFolderPattern.resolve("{yyyy}-{MM}-{dd}", "Pixel 9", whenMillis)
        assertEquals("2026-03-15", resolved)
    }

    @Test
    fun `sanitises characters that are not valid in a path segment`() {
        val resolved = SubFolderPattern.resolve("Uploads/{device}", "My/Phone: \"Test\"", whenMillis)
        assertEquals("Uploads/My_Phone_ _Test_", resolved)
    }

    @Test
    fun `blank device name falls back to device`() {
        val resolved = SubFolderPattern.resolve("{device}", "   ", whenMillis)
        assertEquals("device", resolved)
    }

    @Test
    fun `trims leading and trailing slashes`() {
        val resolved = SubFolderPattern.resolve("/{yyyy}/{MM}/", "Pixel 9", whenMillis)
        assertEquals("2026/03", resolved)
    }

    @Test
    fun `pattern without tokens is returned unchanged`() {
        val resolved = SubFolderPattern.resolve("Camera Uploads", "Pixel 9", whenMillis)
        assertEquals("Camera Uploads", resolved)
    }
}
