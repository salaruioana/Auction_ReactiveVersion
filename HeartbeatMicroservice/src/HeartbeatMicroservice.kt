import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/* Strategia de implementare (Fără Docker - Compatibil Java 8)
HeartbeatMicroservice: Server TCP care ascultă pe portul 1800.
Înregistrare: Fiecare microserviciu se conectează și trimite: "NUME|COMANDA|PID".
Monitorizare: Folosim comenzi OS (tasklist / ps) pentru a verifica dacă PID-ul este activ.
Restart: Dacă pică, executăm comanda, îi punem PID temporar -1 și așteptăm să se reînregistreze. */

data class MonitoredService(val name: String, val command: String, var pid: Long)

class HeartbeatMicroservice {
    private val services = ConcurrentHashMap<String, MonitoredService>()
    private val serverSocket = ServerSocket(1800)
    private val executor = Executors.newSingleThreadScheduledExecutor()

    // Intoarce true daca procesul este inca activ sau daca abia i s-a dat comanda de start
    private fun isProcessAlive(pid: Long): Boolean {
        // --- FIX CRITIC ---
        // Daca PID-ul e -1, inseamna ca abia am dat comanda de restart.
        // Returnam TRUE ca sa ii dam timp sa porneasca si sa trimita noul PID pe socket.
        if (pid == -1L) return true

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
                // Pe Windows, tasklist afiseaza PID-ul daca procesul exista
                output.contains(pid.toString())
            } else {
                // Pe Linux/Mac, comanda ps returneaza codul de succes 0 daca a gasit procesul
                process.waitFor() == 0
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun restartService(service: MonitoredService) {
        try {
            println("!!! [HEARTBEAT] Microserviciul ${service.name} a picat (PID vechi: ${service.pid}). Repornire...")

            // Setam starea "in curs de repornire" inainte de a executa comanda
            service.pid = -1L

            // Executam comanda de pornire
            Runtime.getRuntime().exec(service.command)

            println("[HEARTBEAT] Comanda executata pentru ${service.name}. Astept reinregistrarea...")
        } catch (e: Exception) {
            println("Eroare la repornirea serviciului ${service.name}: ${e.message}")
        }
    }

    fun run() {
        println("HeartbeatMicroservice a pornit pe portul 1800. Compatibilitate OS activata.")

        // Thread dedicat care primeste inregistrari de la microservicii
        Thread {
            while (true) {
                try {
                    val socket = serverSocket.accept()
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val data = reader.readLine()

                    if (data != null) {
                        val parts = data.split("|")
                        if (parts.size == 3) {
                            val name = parts[0]
                            val command = parts[1]
                            val pid = parts[2].toLong()

                            val service = MonitoredService(name, command, pid)
                            services[name] = service
                            println("[HEARTBEAT] Inregistrare primita: Monitorizez $name (PID: $pid)")
                        }
                    }
                    socket.close()
                } catch (e: Exception) {
                    // Ignoram erorile minore de conectare pentru a tine thread-ul viu
                }
            }
        }.start()

        // Task recurent care scaneaza toate serviciile inregistrate o data la 5 secunde
        executor.scheduleAtFixedRate({
            services.forEach { (_, service) ->
                if (!isProcessAlive(service.pid)) {
                    restartService(service)
                }
            }
        }, 5, 5, TimeUnit.SECONDS)
    }
}

fun main(args: Array<String>) {
    HeartbeatMicroservice().run()
}