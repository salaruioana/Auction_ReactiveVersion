import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import kotlin.Exception
import kotlin.random.Random
import kotlin.system.exitProcess
import messagelib.Message
import messagelib.ExecutionJournal
import messagelib.SenderInfo

class BidderMicroservice {
    private var auctioneerSocket: Socket
    private var auctionResultObservable: Observable<String>
    private var myIdentity: String = "[BIDDER_NECONECTAT]"
    private var journal = ExecutionJournal("bidder_journal_${System.nanoTime()}_${Random.nextInt(1000)}.txt")

    //se genereaza identitatea bidder-ului
    private val mySenderInfo: SenderInfo = generateRandomSenderInfo()

    companion object Constants {
        const val AUCTIONEER_HOST = "localhost"
        const val AUCTIONEER_PORT = 1500
        const val MAX_BID = 10_000
        const val MIN_BID = 1_000
    }

    init {
        try {
            auctioneerSocket = Socket(AUCTIONEER_HOST, AUCTIONEER_PORT)
            println("M-am conectat la Auctioneer!")

            myIdentity = "[${auctioneerSocket.localPort}]"

            // se creeaza un obiect Observable ce va emite mesaje primite printr-un TCP
            // fiecare mesaj primit reprezinta un element al fluxului de date reactiv
            auctionResultObservable = Observable.create<String> { emitter ->
                // se citeste raspunsul de pe socketul TCP
                val bufferReader = BufferedReader(InputStreamReader(auctioneerSocket.inputStream))
                val receivedMessage = bufferReader.readLine()

                // daca se primeste un mesaj gol (NULL), atunci inseamna ca cealalta parte a socket-ului a fost inchisa
                if (receivedMessage == null) {
                    bufferReader.close()
                    auctioneerSocket.close()

                    emitter.onError(Exception("AuctioneerMicroservice s-a deconectat."))
                    return@create
                }

                // mesajul primit este emis in flux
                emitter.onNext(receivedMessage)

                // deoarece se asteapta un singur mesaj, in continuare se emite semnalul de incheiere al fluxului
                emitter.onComplete()

                bufferReader.close()
                auctioneerSocket.close()
            }
        } catch (e: Exception) {
            println("$myIdentity Nu ma pot conecta la Auctioneer!")
            exitProcess(1)
        }
    }

    private fun bid() {
        // se genereaza o oferta aleatorie din partea bidderului curent
        val pret = Random.nextInt(MIN_BID, MAX_BID)


        // se creeaza mesajul care incapsuleaza oferta
        val biddingMessage = Message.create("${auctioneerSocket.localAddress}:${auctioneerSocket.localPort}",
            "licitez $pret",mySenderInfo)

        // bidder-ul trimite pretul pentru care doreste sa liciteze
        val serializedMessage = biddingMessage.serialize()

        journal.logEvent("BID_SENT", String(serializedMessage).trim())

        auctioneerSocket.getOutputStream().write(serializedMessage)

        // exista o sansa din 2 ca bidder-ul sa-si trimita oferta de 2 ori, eronat
        if (Random.nextBoolean()) {
            auctioneerSocket.getOutputStream().write(serializedMessage)
        }
    }

    private fun waitForResult() {
        println("$myIdentity Astept rezultatul licitatiei...")
        // bidder-ul se inscrie pentru primirea unui raspuns la oferta trimisa de acesta
        val auctionResultSubscription = auctionResultObservable.subscribeBy(
            // cand se primeste un mesaj in flux, inseamna ca a sosit rezultatul licitatiei
            onNext = {
                val resultMessage: Message = Message.deserialize(it.toByteArray())
                journal
                println("$myIdentity Rezultat licitatie: ${resultMessage.body}")
                journal.clear()
                journal.logEvent("FINISHED", "Received result")
            },
            onError = {
                println("$myIdentity Eroare: $it")
            }
        )

        // se elibereaza memoria obiectului Subscription
        auctionResultSubscription.dispose()
    }

    fun run() {
        val lastState = journal.getLastEvent()
        if (lastState != null && lastState.first == "BID_SENT") {
            println("$myIdentity [RECOVERY] oferta trimisa anterior (${lastState.second}).")
            waitForResult()
        } else {
            bid()
            waitForResult()
        }
    }
}
private fun generateRandomSenderInfo(): SenderInfo {
    val firstNames = listOf(
        "Gregory", "James", "Lisa", "Robert", "Eric", "Allison", "Remy", // House M.D.
        "Michael", "Jim", "Pam", "Dwight", "Stanley", "Kevin", "Angela", "Creed" // The Office
    )

    val lastNames = listOf(
        "House", "Wilson", "Cuddy", "Chase", "Foreman", "Cameron", "Hadley", // House M.D.
        "Scott", "Halpert", "Beesly", "Schrute", "Hudson", "Malone", "Martin", "Bratton" // The Office
    )

    val domains = listOf(
        "princeton-plainsboro.edu",
        "dundermifflin.com",
        "scrute-farms.com",
        "gmail.com",
        "yahoo.com"
    )
    // Alegem aleatoriu
    val firstName = firstNames.random()
    val lastName = lastNames.random()

    // Construim datele
    val name = "$firstName $lastName"

    // Adăugăm un număr mic la email pentru a evita complet duplicatele (ex: ion.popescu_42@gmail.com)
    val email = "${firstName.lowercase()}.${lastName.lowercase()}_${Random.nextInt(10, 99)}@${domains.random()}"

    // Generăm un număr de telefon de 10 cifre care începe cu "07"
    val phone = "07" + (1..8).map { Random.nextInt(0, 10) }.joinToString("")

    return SenderInfo(name, phone, email)
}

fun main(args: Array<String>) {
    val bidderMicroservice = BidderMicroservice()
    bidderMicroservice.run()
}