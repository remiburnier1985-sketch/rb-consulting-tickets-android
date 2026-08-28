package ch.rbconsulting.tickets

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import ch.rbconsulting.tickets.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let { analyse(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.addTicket.setOnClickListener { picker.launch("image/*") }
    }

    private fun analyse(uri: Uri) {
        b.result.text = "Lecture automatique en cours…"
        val image = InputImage.fromFilePath(this, uri)
        recognizer.process(image).addOnSuccessListener { result ->
            val text = result.text
            val currency = when {
                text.contains("CHF", true) || text.contains("Fr.", true) -> "CHF"
                text.contains("EUR", true) || text.contains("€") -> "EUR"
                else -> "À vérifier"
            }
            val amount = detectAmount(text)
            val category = classify(text)
            b.result.text = "Devise : $currency\nMontant détecté : ${amount ?: "À vérifier"}\nCatégorie : $category\n\nOCR :\n$text"
        }.addOnFailureListener { b.result.text = "Lecture impossible : ${it.message}" }
    }

    private fun detectAmount(text: String): String? {
        val p = Pattern.compile("(?i)(?:total|t[o0]tal|montant|à payer|a payer)[^0-9]{0,20}([0-9]+[.,][0-9]{2})")
        val m = p.matcher(text.replace("'", ""))
        if (m.find()) return m.group(1)
        return Regex("\\b\\d+[.,]\\d{2}\\b").findAll(text).lastOrNull()?.value
    }

    private fun classify(t: String): String {
        val s=t.lowercase()
        return when {
            listOf("esso","shell","bp ","totalenergies","station","carburant","diesel","essence").any{s.contains(it)} -> "Essence"
            listOf("restaurant","cafe","café","brasserie","pizza","burger","menu").any{s.contains(it)} -> "Restaurant"
            listOf("parking","indigo","paybyphone").any{s.contains(it)} -> "Parking"
            listOf("hotel","hôtel","booking").any{s.contains(it)} -> "Hôtel"
            listOf("autoroute","péage","peage","vinci").any{s.contains(it)} -> "Péage"
            else -> "Autre / à vérifier"
        }
    }
}
