import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import kotlin.system.exitProcess
import messagelib.Message
import messagelib.ExecutionJournal
import java.io.PrintWriter

class MessageProcessorMicroservice {
    private var messageProcessorSocket: ServerSocket
    private lateinit var biddingProcessorSocket: Socket
    private var auctioneerConnection:Socket
    private var receiveInQueueObservable: Observable<String>
    private val subscriptions = CompositeDisposable()
    private val messageQueue: Queue<Message> = LinkedList<Message>()
    private val journal = ExecutionJournal("messageProcessor_journal.txt")

    companion object Constants {
        const val MESSAGE_PROCESSOR_PORT = 1600
        const val BIDDING_PROCESSOR_HOST = "localhost"
        const val BIDDING_PROCESSOR_PORT = 1700
    }

    init {
        messageProcessorSocket = ServerSocket(MESSAGE_PROCESSOR_PORT)
        println("MessageProcessorMicroservice se executa pe portul: ${messageProcessorSocket.localPort}")
        println("Se asteapta mesaje pentru procesare...")

        // se asteapta mesaje primite de la AuctioneerMicroservice
        auctioneerConnection = messageProcessorSocket.accept()
        val bufferReader = BufferedReader(InputStreamReader(auctioneerConnection.inputStream))

        // se creeaza obiectul Observable cu care se captureaza mesajele de la AuctioneerMicroservice
        receiveInQueueObservable = Observable.create<String> { emitter ->
            while (true) {
                // se citeste mesajul de la AuctioneerMicroservice de pe socketul TCP
                val receivedMessage = bufferReader.readLine()

                // daca se primeste un mesaj gol (NULL), atunci inseamna ca cealalta parte a socket-ului a fost inchisa
                if (receivedMessage == null) {
                    // deci subscriber-ul respectiv a fost deconectat
                    bufferReader.close()
                    auctioneerConnection.close()

                    emitter.onError(Exception("Eroare: AuctioneerMicroservice ${auctioneerConnection.port} a fost deconectat."))
                    break
                }

                // daca mesajul este cel de incheiere a licitatiei (avand corpul "final"), atunci se emite semnalul Complete
                if (Message.deserialize(receivedMessage.toByteArray()).body == "final") {
                    emitter.onComplete()

                    break
                } else {
                    // se emite ce s-a citit ca si element in fluxul de mesaje
                    emitter.onNext(receivedMessage)
                }
            }
        }
    }

    private fun receiveAndProcessMessages() {
        // se primesc si se adauga in coada mesajele de la AuctioneerMicroservice
        ///TODO --- filtrati duplicatele folosind operatorul de filtrare
        val receiveInQueueSubscription = receiveInQueueObservable
            .distinct{ unparsedMessage ->
            Message.deserialize(unparsedMessage.toString().toByteArray()).sender
            }
            .subscribeBy(
                onNext = {
                    val message = Message.deserialize(it.toByteArray())
                    println(message)
                    messageQueue.add(message)
                },
                onComplete = {
                    // s-a incheiat primirea tuturor mesajelor
                    ///TODO --- se ordoneaza in functie de data si ora cand mesajele au fost primite
                    val sortedList = messageQueue.sortedBy{ it.timestamp}
                    messageQueue.clear()
                    messageQueue.addAll(sortedList)

                    println("Mesaje procesate și sortate. Salvez în jurnal...")

                    val processedData = messageQueue.joinToString(";") { String(it.serialize()).trim() }
                    journal.logEvent("SENDING_TO_BIDDING_PROCESSOR", processedData)

                    // s-au primit toate mesajele de la AuctioneerMicroservice, i se trimite un mesaj pentru a semnala
                    // acest lucru
                    val finishedMessagesMessage = Message.create(
                        "${auctioneerConnection.localAddress}:${auctioneerConnection.localPort}",
                        "am primit tot"
                    )
                    auctioneerConnection.getOutputStream().write(finishedMessagesMessage.serialize())
                    auctioneerConnection.close()

                    // se trimit mai departe mesajele procesate catre BiddingProcessor
                    sendProcessedMessages()
                },
                onError = { println("Eroare: $it") }
            )
        subscriptions.add(receiveInQueueSubscription)
    }

    private fun sendProcessedMessages() {
        try {
            biddingProcessorSocket = Socket(BIDDING_PROCESSOR_HOST, BIDDING_PROCESSOR_PORT)
            val outputStream = biddingProcessorSocket.getOutputStream()

            println("Trimit urmatoarele mesaje...")

            Observable.fromIterable(messageQueue).subscribeBy(
                onNext = {
                    outputStream.write(it.serialize())
                    outputStream.flush() // Forțăm trimiterea imediată
                    println("Trimis: $it")
                },
                onComplete = {
                    val noMoreMessages = Message.create(
                        "${biddingProcessorSocket.localAddress}:${biddingProcessorSocket.localPort}",
                        "final"
                    )
                    outputStream.write(noMoreMessages.serialize())
                    outputStream.flush()

                    println("Toate mesajele au fost trimise la BiddingProcessor.")

                    // Jurnalizarea succesului
                    journal.clear()
                    journal.logEvent("FINISHED", "Success")

                    // ÎNCHIDEM socket-ul abia aici!
                    biddingProcessorSocket.close()
                    subscriptions.dispose()
                },
                onError = {
                    println("Eroare la trimitere: $it")
                    biddingProcessorSocket.close()
                }
            )
        } catch (e: Exception) {
            println("Nu ma pot conecta la BiddingProcessor: ${e.message}")
        }
    }

    fun run() {
        val lastState = journal.getLastEvent()

        if (lastState != null && lastState.first == "SENDING_TO_BIDDING_PROCESSOR") {
            println("[RECOVERY] Crash detectat.")

            val savedMessages = lastState.second.split(";")
            savedMessages.forEach {
                if (it.isNotBlank()) {
                    messageQueue.add(Message.deserialize(it.toByteArray()))
                }
            }

            println("[RECOVERY] Am recuperat ${messageQueue.size} mesaje. Trimit la BiddingProcessor")
            sendProcessedMessages()
        } else {
            receiveAndProcessMessages()
        }
    }
}

fun registerToHeartbeat(name: String, jarPath: String) {
    try {
        val socket = Socket("localhost", 1800)
        val writer = PrintWriter(socket.getOutputStream(), true)

        // Aflăm PID-ul în mod compatibil Java 8/11
        val jvmName = java.lang.management.ManagementFactory.getRuntimeMXBean().name
        val currentPid = jvmName.split("@")[0]

        // Comanda trebuie să fie calea COMPLETĂ către JAR
        val command = "java -jar $jarPath"

        writer.println("$name|$command|$currentPid")
        socket.close()
    } catch (e: Exception) {
        println("Heartbeat nu e pornit.")
    }
}

fun main(args: Array<String>) {
    registerToHeartbeat("MessageProcessor", "out/artifacts/MessageProcessorMicroservice_jar/MessageProcessorMicroservice.jar")

    val messageProcessorMicroservice = MessageProcessorMicroservice()
    messageProcessorMicroservice.run()
}