/*Strategia de implementare (Fără Docker)
HeartbeatMicroservice: Va fi un server TCP care ascultă pe portul 1800.
Înregistrare: Fiecare microserviciu (Auctioneer, MessageProcessor, BiddingProcessor), imediat ce pornește, se conectează la Heartbeat și îi spune: "Sunt [NUME] și mă poți reporni cu [COMANDA]".
Monitorizare: Heartbeat-ul va folosi ProcessHandle (disponibil în Java 9+) pentru a verifica dacă PID-ul procesului respectiv mai este activ.
Restart: Dacă PID-ul dispare, Heartbeat execută comanda primită.*/

import java.io.*
import java.net.*
import java.util.concurrent.*

data class MonitoredService(val name: String, val command: String, var pid: Long)

//primeste la intrare pid-ul
//intoarce true daca procesul este inca activ si false daca nu

private fun isProcessAlive(pid: Long): Boolean {
    if (pid == -1L) return false // evitam verificarea daca procesul e in curs de restart
    return try {
        val os = System.getProperty("os.name").lowercase()
        val command = if (os.contains("win")) {
            "tasklist /FI \"PID eq $pid\""
        } else {
            "ps -p $pid"
        }

        val process = Runtime.getRuntime().exec(command)
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = reader.readLines().joinToString("")

        if (os.contains("win")) {
            // Daca tasklist returneaza PID-ul, inseamna ca e viu
            output.contains(pid.toString())
        } else {
            // Pe Linux/Mac, daca exit value e 0, procesul exista
            process.waitFor() == 0
        }
    } catch (e: Exception) {
        false
    }
}

private fun restartService(service: MonitoredService) {
    try {
        println("!!! [HEARTBEAT] Microserviciul ${service.name} a picat. Repornire..")
        // Executăm comanda de pornire (ex: java -jar ...)
        Runtime.getRuntime().exec(service.command)

        // Îi punem un PID temporar de -1.
        // Când microserviciul pornește, se va RE-ÎNREGISTRA singur și va actualiza PID-ul real.
        service.pid = -1
    } catch (e: Exception) {
        println("Eroare la repornire: ${e.message}")
    }
}

class HeartbeatMicroservice {
    private val services = ConcurrentHashMap<String, MonitoredService>()
    private val serverSocket = ServerSocket(1800)
    private val executor = Executors.newSingleThreadScheduledExecutor()

    fun run(){
        println("HeartbeatMicroservice a pornit pe portul 1800")

        //thread care primeste conexiuni de socketuri
        Thread{
            while(true) {
                try {
                    val socket = serverSocket.accept()
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val data = reader.readLine()  // format name | command | pid
                    if (data != null) {
                        val parts = data.split("|")
                        if (parts.size == 3) {
                            val service = MonitoredService(parts[0], parts[1], parts[2].toLong())
                            services[service.name] = service
                            println("[HEARTBEAT] Monitorizez ${service.name} (PID: ${service.pid})")
                        }
                        socket.close()
                    }
                } catch (e: Exception) {}
            }
        }.start()

        executor.scheduleAtFixedRate({
            services.forEach{ (name, service) ->
                if (!isProcessAlive(service.pid)) {
                    restartService(service)
                }
            }
        },5,5,TimeUnit.SECONDS)
    }
}

fun main() {
    HeartbeatMicroservice().run()
}