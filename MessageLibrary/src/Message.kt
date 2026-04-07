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
/*
fun main(args: Array<String>) {
    val info = SenderInfo("Ioana bomboana", "0777676767", "ioana@ac.com")
    val msg = Message.create("localhost:4848", "test mesaj", info)
    println(msg)
    val serialized = msg.serialize()
    val deserialized = Message.deserialize(serialized)
    println(deserialized)
}*/