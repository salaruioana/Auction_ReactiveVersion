package messagelib
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.PrintWriter
import java.net.Socket

class ReactiveNetworkJournal(
    private val serviceName: String,
    private val localJournal: IExecutionMonitor // Jurnalul tau vechi
) : IExecutionMonitor {

    // "Teava" prin care trec logurile
    private val logSubject = PublishSubject.create<String>()

    init {
        // Aici ne abonam sa trimitem logurile catre MasterLogger pe un thread de fundal (I/O)
        // Astfel nu blocam microserviciul niciodata!
        logSubject
            .observeOn(Schedulers.io()) // Mutam trimiterea pe retea in background
            .subscribe(
                { logMessage -> sendToMasterLogger(logMessage) },
                { error -> println("Eroare la trimiterea logului catre Master: ${error.message}") }
            )
    }

    private fun sendToMasterLogger(message: String) {
        try {
            // Trimite mesajul la portul 1900 (unde va asculta MasterLogger)
            val socket = Socket("localhost", 1900)
            val writer = PrintWriter(socket.getOutputStream(), true)
            // Formatam mesajul ca sa stie Master-ul de la cine vine
            writer.println("[$serviceName] $message")
            socket.close()
        } catch (e: Exception) {
            // Daca masterul e picat, pur si simplu ignoram eroarea aici,
            // logul ramane salvat in localJournal oricum.
        }
    }

    // --- Implementarea Interfetei ---

    override fun logInfo(message: String) {
        localJournal.logInfo(message) // 1. Scrie local pe disc (siguranta)
        logSubject.onNext("INFO | $message") // 2. Arunca pe teava RxJava
    }

    override fun logStep(stepName: String, data: String) {
        localJournal.logStep(stepName, data) // 1. Scrie local
        logSubject.onNext("STEP | $stepName | $data") // 2. Arunca pe teava RxJava
    }

    override fun markAsFinished() {
        localJournal.markAsFinished()
        logSubject.onNext("FINISHED | Ciclul s-a incheiat cu succes.")
    }

    override fun getLastIncompleteStep(): Pair<String, String>? {
        return localJournal.getLastIncompleteStep()
    }
}