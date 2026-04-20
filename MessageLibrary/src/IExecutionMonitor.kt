package messagelib

// percepem fiecare microserviciu ca pe un state-machine in care nodurile sunt de tip (stare, date asociate)
interface IExecutionMonitor {
    // Loghează informații generale ("M-am conectat", "Astept mesaje")
    fun logInfo(info: String)

    // Loghează un pas critic și datele lui (ex: state="BID_SENT", content="<json_oferta>")
    fun logStep(state: String, content: String)

    // Marchează ultimul pas critic ca fiind încheiat cu succes
    fun markAsFinished()

    // Metoda de recovery: returnează perechea (State, Content) dacă a picat curentul înainte de markAsFinished()
    fun getLastIncompleteStep(): Pair<String, String>?
}