package sg.act.domain.inference

/** A downloadable on-device model with ordered, verified mirror URLs. */
data class ModelSpec(
    val id: String,
    val displayName: String,
    /** Canonical local filename used for storage, independent of each mirror's naming. */
    val fileName: String,
    val sizeBytes: Long,
    /** Approximate RAM needed to run comfortably, in MB. */
    val minRamMb: Int,
    /**
     * Mirror URLs in priority order. The downloader walks this list, falling back
     * to the next on failure. Every entry has been verified public/ungated
     * (downloadable with no Hugging Face token). Filenames differ across mirrors
     * (dash vs dot, `google_` prefix), so each is a full URL rather than a repo.
     */
    val urls: List<String>,
)

/**
 * Curated set of small, phone-runnable models Domain AI can download. Import accepts
 * any GGUF; this list is the convenient, vetted set. All mirrors are on the
 * allow-listed host and were confirmed ungated.
 */
object ModelCatalog {

    val models: List<ModelSpec> = listOf(
        ModelSpec(
            id = "gemma-3-1b-it-q4",
            displayName = "Gemma 3 1B Instruct (Q4_K_M)",
            fileName = "gemma-3-1b-it-Q4_K_M.gguf",
            sizeBytes = 810_000_000L,
            minRamMb = 1536,
            urls = listOf(
                "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
                "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
                "https://huggingface.co/lmstudio-community/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
                "https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf",
            ),
        ),
        ModelSpec(
            id = "deepseek-r1-distill-qwen-1.5b-q4",
            displayName = "DeepSeek-R1 Distill Qwen 1.5B (Q4_K_M)",
            fileName = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            sizeBytes = 1_120_000_000L,
            minRamMb = 1792,
            urls = listOf(
                "https://huggingface.co/unsloth/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                "https://huggingface.co/lmstudio-community/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            ),
        ),
        ModelSpec(
            id = "gemma-2-2b-it-q4",
            displayName = "Gemma 2 2B Instruct (Q4_K_M)",
            fileName = "gemma-2-2b-it-Q4_K_M.gguf",
            sizeBytes = 1_710_000_000L,
            minRamMb = 3072,
            urls = listOf(
                "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
                "https://huggingface.co/lmstudio-community/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
                "https://huggingface.co/second-state/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
                "https://huggingface.co/QuantFactory/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it.Q4_K_M.gguf",
                "https://huggingface.co/MaziyarPanahi/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it.Q4_K_M.gguf",
            ),
        ),
        ModelSpec(
            id = "gemma-3-4b-it-q4",
            displayName = "Gemma 3 4B Instruct (Q4_K_M)",
            fileName = "gemma-3-4b-it-Q4_K_M.gguf",
            sizeBytes = 2_490_000_000L,
            minRamMb = 5120,
            urls = listOf(
                "https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
                "https://huggingface.co/unsloth/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
                "https://huggingface.co/lmstudio-community/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
                "https://huggingface.co/bartowski/google_gemma-3-4b-it-GGUF/resolve/main/google_gemma-3-4b-it-Q4_K_M.gguf",
            ),
        ),
    )

    /** Host(s) the downloader is permitted to contact. */
    val allowedHosts: Set<String> = setOf("huggingface.co")
}
