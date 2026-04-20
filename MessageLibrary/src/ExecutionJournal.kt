package messagelib
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ExecutionJournal(microserviceName: String) : IExecutionMonitor {
    private val journalFile = File("${microserviceName}_journal.log")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    init {
        if (!journalFile.exists()) {
            journalFile.createNewFile()
        }
    }

    private fun appendToFile(line: String) {
        val timestamp = LocalDateTime.now().format(timeFormatter)
        val entry = "[$timestamp] $line\n"

        Files.write(
            journalFile.toPath(),
            entry.toByteArray(),
            StandardOpenOption.APPEND
        )
    }

    override fun logInfo(info: String) {
        appendToFile("INFO | $info")
    }

    override fun logStep(state: String, content: String) {
        // Scriem pe disc sub forma: STEP | NumeleStarii | DateleSerializate
        appendToFile("STEP | $state | $content")
    }

    override fun markAsFinished() {
        appendToFile("FINISHED")
    }

    override fun getLastIncompleteStep(): Pair<String, String>? {
        if (!journalFile.exists() || journalFile.length() == 0L) return null

        var incompleteStep: Pair<String, String>? = null

        // Citim fișierul linie cu linie din trecut spre prezent
        journalFile.useLines { lines ->
            lines.forEach { line ->
                // Tăiem timestamp-ul din față pentru a parsa comanda (ex: "[12:30:15.123] STEP | BID | ...")
                val commandPart = line.substringAfter("] ")

                if (commandPart.startsWith("STEP")) {
                    // Am găsit un pas! Îl memorăm ca fiind (potențial) incomplet.
                    val parts = commandPart.split(" | ", limit = 3)
                    if (parts.size == 3) {
                        incompleteStep = Pair(parts[1], parts[2])
                    }
                } else if (commandPart.startsWith("FINISHED")) {
                    // Dacă vedem un finished, înseamnă că ultimul pas a fost complet. Anulăm starea.
                    incompleteStep = null
                }
                // Liniile cu "INFO" sunt pur și simplu ignorate de mecanismul de recovery
            }
        }

        return incompleteStep
    }
}