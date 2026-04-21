import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.BufferedReader
import java.io.FileWriter
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket

class MasterLoggerMicroservice {
    fun run() {
        val serverSocket = ServerSocket(1900)
        println("MasterLogger a pornit pe portul 1900. Astept loguri...")

        // Deschidem fisierul principal de log in modul "append" (sa adauge la final)
        val masterLogFile = PrintWriter(FileWriter("master_general_journal.log", true), true)

        while (true) {
            val clientSocket = serverSocket.accept()

            // Pentru fiecare microserviciu care se conecteaza, cream un flux reactiv
            Observable.create<String> { emitter ->
                val reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
                var line = reader.readLine()
                while (line != null) {
                    emitter.onNext(line)
                    line = reader.readLine()
                }
                emitter.onComplete()
            }
                .subscribeOn(Schedulers.io()) // Procesam in fundal ca sa putem asculta mai multi clienti simultan
                .subscribe(
                    { logMessage ->
                        println(logMessage) // Afisam in consola
                        masterLogFile.println(logMessage) // Scriem in fisierul general
                    },
                    { error -> /* Ignoram deconectarile tacute */ }
                )
        }
    }
}

fun main(args: Array<String>) {
    MasterLoggerMicroservice().run()
}