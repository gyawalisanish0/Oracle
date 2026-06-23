package sg.act.domain.inference

import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun `every model has at least two mirrors`() {
        for (spec in ModelCatalog.models) {
            assertTrue("${spec.id} should have fallback mirrors", spec.urls.size >= 2)
        }
    }

    @Test
    fun `every mirror url is https on an allow-listed host`() {
        val hostRegex = Regex("^https://([^/]+)/")
        for (spec in ModelCatalog.models) {
            for (url in spec.urls) {
                val host = hostRegex.find(url)?.groupValues?.get(1)
                assertTrue("Not https: $url", url.startsWith("https://"))
                assertTrue("Host not allow-listed: $url", host in ModelCatalog.allowedHosts)
                assertTrue("Not a .gguf URL: $url", url.endsWith(".gguf"))
            }
        }
    }

    @Test
    fun `local filenames are gguf and ids unique`() {
        val ids = ModelCatalog.models.map { it.id }
        assertTrue("Duplicate ids", ids.size == ids.toSet().size)
        for (spec in ModelCatalog.models) {
            assertTrue("${spec.fileName} must be .gguf", spec.fileName.endsWith(".gguf"))
            assertTrue("${spec.id} needs positive size", spec.sizeBytes > 0)
            assertTrue("${spec.id} needs minRamMb", spec.minRamMb > 0)
        }
    }
}
