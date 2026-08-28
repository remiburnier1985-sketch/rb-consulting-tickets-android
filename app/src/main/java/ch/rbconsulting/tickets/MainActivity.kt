package ch.rbconsulting.tickets

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import ch.rbconsulting.tickets.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val queue = mutableListOf<Uri>()
    private val tickets = mutableListOf<Ticket>()

    private val multiPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            if (!queue.contains(uri)) queue.add(uri)
        }
        renderQueue()
    }

    private val camera = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val uri = Uri.parse(MediaStore.Images.Media.insertImage(contentResolver, bitmap, "RB_${System.currentTimeMillis()}", "Justificatif RB Consulting"))
            if (uri.toString() != "null") queue.add(uri)
            renderQueue()
            Toast.makeText(this, "Photo ajoutée. Vous pouvez en prendre une autre.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        loadTickets()
        b.importPhotos.setOnClickListener { multiPicker.launch(arrayOf("image/*")) }
        b.takePhoto.setOnClickListener { camera.launch(null) }
        b.clearQueue.setOnClickListener { queue.clear(); renderQueue() }
        b.validateAll.setOnClickListener { validateQueue() }
        b.exportPdf.setOnClickListener { closePeriod() }
        b.archive.setOnClickListener { showArchive() }
        renderQueue(); renderTickets()
    }

    private fun renderQueue() {
        b.queueCount.text = "${queue.size} justificatif${if (queue.size > 1) "s" else ""} en attente"
        b.queuePreview.text = if (queue.isEmpty()) "Importez ou photographiez vos justificatifs." else queue.mapIndexed { i, _ -> "✓ Photo ${i + 1}" }.joinToString("\n")
        b.validateAll.isEnabled = queue.isNotEmpty()
        b.clearQueue.isEnabled = queue.isNotEmpty()
    }

    private fun validateQueue() {
        if (queue.isEmpty()) return
        b.validateAll.isEnabled = false
        b.queuePreview.text = "Analyse OCR de ${queue.size} justificatif(s)…"
        val pending = queue.toList()
        var completed = 0
        pending.forEach { uri ->
            try {
                val image = InputImage.fromFilePath(this, uri)
                recognizer.process(image)
                    .addOnSuccessListener { result -> addRecognizedTicket(uri, result.text) }
                    .addOnFailureListener { addRecognizedTicket(uri, "") }
                    .addOnCompleteListener {
                        completed++
                        if (completed == pending.size) {
                            queue.clear(); saveTickets(); renderQueue(); renderTickets()
                            Toast.makeText(this, "${pending.size} justificatif(s) validé(s)", Toast.LENGTH_LONG).show()
                        }
                    }
            } catch (_: Exception) {
                addRecognizedTicket(uri, ""); completed++
                if (completed == pending.size) { queue.clear(); saveTickets(); renderQueue(); renderTickets() }
            }
        }
    }

    private fun addRecognizedTicket(uri: Uri, text: String) {
        val amount = detectAmount(text)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
        val currency = when {
            text.contains("CHF", true) || text.contains("Fr.", true) -> "CHF"
            text.contains("EUR", true) || text.contains("€") -> "EUR"
            else -> "CHF"
        }
        val fingerprint = "${amount}_${currency}_${text.take(80).lowercase()}"
        if (tickets.none { it.fingerprint == fingerprint }) {
            tickets.add(Ticket(UUID.randomUUID().toString(), uri.toString(), detectDate(text), detectVendor(text), amount, currency, classify(text), text.take(1200), fingerprint, false))
        }
    }

    private fun detectAmount(text: String): String? {
        val p = Pattern.compile("(?i)(?:total|t[o0]tal|montant|à payer|a payer|net)[^0-9]{0,25}([0-9]+[.,][0-9]{2})")
        val m = p.matcher(text.replace("'", "")); if (m.find()) return m.group(1)
        return Regex("\\b\\d+[.,]\\d{2}\\b").findAll(text).lastOrNull()?.value
    }

    private fun detectDate(text: String): String {
        return Regex("\\b([0-3]?\\d)[./-]([01]?\\d)[./-](20\\d{2}|\\d{2})\\b").find(text)?.value
            ?: SimpleDateFormat("dd.MM.yyyy", Locale.FRANCE).format(Date())
    }

    private fun detectVendor(text: String): String = text.lineSequence().map { it.trim() }.firstOrNull { it.length in 3..60 && it.any(Char::isLetter) } ?: "À vérifier"

    private fun classify(t: String): String {
        val s = t.lowercase()
        return when {
            listOf("esso","shell","bp ","totalenergies","carburant","diesel","essence").any{s.contains(it)} -> "Carburant"
            listOf("restaurant","cafe","café","brasserie","pizza","burger","menu").any{s.contains(it)} -> "Restaurant"
            listOf("parking","indigo","paybyphone").any{s.contains(it)} -> "Parking"
            listOf("hotel","hôtel","booking").any{s.contains(it)} -> "Hôtel"
            listOf("autoroute","péage","peage","vinci").any{s.contains(it)} -> "Péage"
            listOf("train","sncf","taxi","uber","bus","tram").any{s.contains(it)} -> "Transport"
            listOf("fourniture","papeterie","office","bureau").any{s.contains(it)} -> "Fournitures"
            listOf("outil","matériel","materiel","brico").any{s.contains(it)} -> "Équipement"
            else -> "Autre / à vérifier"
        }
    }

    private fun renderTickets() {
        val active = tickets.filter { !it.archived }
        b.chfTotal.text = "Dépenses CHF : %.2f CHF".format(Locale.US, active.filter { it.currency == "CHF" }.sumOf { it.amount })
        b.eurTotal.text = "Dépenses EUR : %.2f EUR".format(Locale.US, active.filter { it.currency == "EUR" }.sumOf { it.amount })
        b.result.text = if (active.isEmpty()) "Aucun ticket validé." else active.reversed().joinToString("\n\n") { "${it.date} • ${it.vendor}\n${"%.2f".format(Locale.US,it.amount)} ${it.currency} • ${it.category}" }
    }

    private fun closePeriod() {
        val active = tickets.filter { !it.archived }
        if (active.isEmpty()) { Toast.makeText(this, "Aucun ticket à clôturer", Toast.LENGTH_SHORT).show(); return }
        active.forEach { it.archived = true }; saveTickets(); renderTickets()
        val name = b.periodName.text.toString().ifBlank { SimpleDateFormat("MMMM yyyy", Locale.FRANCE).format(Date()) }
        getSharedPreferences("rbc", MODE_PRIVATE).edit().putString("last_period", "$name : ${active.size} justificatif(s)").apply()
        Toast.makeText(this, "Période $name clôturée et archivée", Toast.LENGTH_LONG).show()
    }

    private fun showArchive() {
        val archived = tickets.filter { it.archived }
        b.result.text = if (archived.isEmpty()) "Aucune archive." else "ARCHIVES\n\n" + archived.reversed().joinToString("\n") { "${it.date} • ${it.vendor} • %.2f ${it.currency}".format(Locale.US,it.amount) }
    }

    private fun saveTickets() {
        val a = JSONArray(); tickets.forEach { t -> a.put(JSONObject().apply { put("id",t.id); put("uri",t.uri); put("date",t.date); put("vendor",t.vendor); put("amount",t.amount); put("currency",t.currency); put("category",t.category); put("ocr",t.ocr); put("fingerprint",t.fingerprint); put("archived",t.archived) }) }
        getSharedPreferences("rbc", MODE_PRIVATE).edit().putString("tickets", a.toString()).apply()
    }

    private fun loadTickets() {
        val raw = getSharedPreferences("rbc", MODE_PRIVATE).getString("tickets", null) ?: return
        try { val a=JSONArray(raw); for(i in 0 until a.length()){ val o=a.getJSONObject(i); tickets.add(Ticket(o.getString("id"),o.getString("uri"),o.getString("date"),o.getString("vendor"),o.getDouble("amount"),o.getString("currency"),o.getString("category"),o.optString("ocr"),o.optString("fingerprint"),o.optBoolean("archived"))) } } catch (_:Exception) {}
    }

    data class Ticket(val id:String,val uri:String,val date:String,val vendor:String,val amount:Double,val currency:String,val category:String,val ocr:String,val fingerprint:String,var archived:Boolean)
}
