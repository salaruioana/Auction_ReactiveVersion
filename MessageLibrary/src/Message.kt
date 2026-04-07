package messagelib
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.*

data class SenderInfo(
    val name: String,
    val phoneNumber: String,
    val email: String
)

class Message private constructor(val sender: String, val body: String, val timestamp: Date, val senderInfo: SenderInfo?) {
    companion object {
        fun create(sender: String, body: String, senderInfo: SenderInfo? = null): Message {
            return Message(sender, body, Date(), senderInfo)
        }

        fun deserialize(msg: ByteArray): Message {
            val msgString = String(msg).trim()

            val parts = msgString.split('|', limit = 6)

            val timestamp = parts[0].toLong()
            val senderIp = parts[1]
            val name = parts[2]
            val phone = parts[3]
            val email = parts[4]
            val body = parts[5]

            val parsedSenderInfo = if (name.isNotEmpty()) {
                SenderInfo(name, phone, email)
            } else {
                null
            }

            return Message(senderIp, body, Date(timestamp), parsedSenderInfo)
        }
    }

    fun serialize(): ByteArray {
        val name = senderInfo?.name ?: ""
        val phone = senderInfo?.phoneNumber ?: ""
        val email = senderInfo?.email ?: ""

        return "${timestamp.time}|$sender|${name}|${phone}|${email}|$body\n".toByteArray()
    }

    override fun toString(): String {
        val dateString = SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(timestamp)
        return if (senderInfo != null) {
            "[$dateString] $sender (${senderInfo.name}) >>> $body"
        } else {
            "[$dateString] $sender [SYSTEM] >>> $body"
        }
    }
}



fun main(args: Array<String>) {
    val info = SenderInfo("Ioana bomboana", "0777676767", "ioana@ac.com")
    val msg = Message.create("localhost:4848", "test mesaj", info)
    println(msg)
    val serialized = msg.serialize()
    val deserialized = Message.deserialize(serialized)
    println(deserialized)
}
class ExecutionJournal( val filename: String) {
    private val journalFile = File(filename)

    init {
        if (!journalFile.exists()) {
            journalFile.createNewFile()
        }
    }

    fun logEvent(state: String, data: String) {
        val entry = "$state | $data\n"
        Files.write(
            journalFile.toPath(),
            entry.toByteArray(),
            StandardOpenOption.APPEND
        )
        println("[JOURNAL] Am salvat starea: $state")
    }
    fun logMessage(state: String, message: Message) {
        val serializedData = String(message.serialize())
        logEvent(state, serializedData)
    }

    fun getLastEvent(): Pair<String, String>? {
        val lines = journalFile.readLines()
        if (lines.isEmpty()) return null

        val lastLine = lines.last()
        val parts = lastLine.split(" | ", limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    fun clear() {
        journalFile.writeText("")
    }
}