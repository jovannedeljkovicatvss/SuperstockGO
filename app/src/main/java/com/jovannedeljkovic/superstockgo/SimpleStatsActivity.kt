package com.jovannedeljkovic.superstockgo

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SimpleStatsActivity : AppCompatActivity() {

    private lateinit var repository: Repository
    private lateinit var tvTotalProducts: TextView
    private lateinit var tvLowStock: TextView
    private lateinit var tvOutOfStock: TextView
    private lateinit var tvCategoryCount: TextView
    private lateinit var tvLowStockList: TextView
    private lateinit var btnExportCSV: Button
    private lateinit var btnEmailReport: Button  // NOVO
    private lateinit var btnBack: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_stats)

        // Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        toolbar.title = "📊 Statistike"
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Inicijalizacija
        repository = Repository(this)

        // Poveži view-ove
        tvTotalProducts = findViewById(R.id.tvTotalProducts)
        tvLowStock = findViewById(R.id.tvLowStock)
        tvOutOfStock = findViewById(R.id.tvOutOfStock)
        tvCategoryCount = findViewById(R.id.tvCategoryCount)
        tvLowStockList = findViewById(R.id.tvLowStockList)
        btnExportCSV = findViewById(R.id.btnExportCSV)
        btnEmailReport = findViewById(R.id.btnEmailReport)  // NOVO
        btnBack = findViewById(R.id.btnBack)
        progressBar = findViewById(R.id.progressBar)

        // Učitaj podatke
        loadStatistics()

        // Dugmad
        btnExportCSV.setOnClickListener { exportToCSV() }
        btnEmailReport.setOnClickListener { sendEmailReport() }  // NOVO
        btnBack.setOnClickListener { finish() }
    }

    private fun loadStatistics() {
        progressBar.visibility = ProgressBar.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val proizvodi = repository.getProizvodiSuspended()

                withContext(Dispatchers.Main) {
                    updateStatistics(proizvodi)
                    progressBar.visibility = ProgressBar.GONE
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SimpleStatsActivity,
                        "Greška pri učitavanju podataka",
                        Toast.LENGTH_SHORT).show()
                    progressBar.visibility = ProgressBar.GONE
                }
            }
        }
    }

    private fun updateStatistics(proizvodi: List<Proizvod>) {
        // Osnovne statistike
        val total = proizvodi.size
        val lowStock = proizvodi.count { it.kolicina in 1..5 }
        val outOfStock = proizvodi.count { it.kolicina == 0 }
        val categories = proizvodi.map { it.kategorija }.distinct().size

        tvTotalProducts.text = "📦 Ukupno proizvoda: $total"
        tvLowStock.text = "⚠️ Niska zaliha: $lowStock"
        tvOutOfStock.text = "❌ Bez zaliha: $outOfStock"
        tvCategoryCount.text = "🏷️ Kategorija: $categories"

        // Top 5 niska zaliha
        val lowStockProducts = proizvodi
            .filter { it.kolicina <= 5 }
            .sortedBy { it.kolicina }
            .take(5)

        val lowStockText = buildString {
            if (lowStockProducts.isEmpty()) {
                append("✅ Nema proizvoda sa niskom zaliom!")
            } else {
                append("Proizvodi sa niskom zaliom:\n\n")
                lowStockProducts.forEachIndexed { index, proizvod ->
                    append("${index + 1}. ${proizvod.naziv}\n")
                    append("   Kategorija: ${proizvod.kategorija}\n")
                    append("   Količina: ${proizvod.kolicina} kom\n\n")
                }
            }
        }
        tvLowStockList.text = lowStockText
    }

    private fun exportToCSV() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val proizvodi = repository.getProizvodiSuspended()
                val csv = StringBuilder()

                // CSV header sa UTF-8 encoding
                csv.append("\uFEFF") // UTF-8 BOM za Excel
                csv.append("ID;Naziv;Kategorija;Količina;Datum\n")

                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                proizvodi.forEach { proizvod ->
                    csv.append("${proizvod.id};${proizvod.naziv};${proizvod.kategorija};${proizvod.kolicina};${dateFormat.format(Date())}\n")
                }

                withContext(Dispatchers.Main) {
                    // Prikaži dijalog za deljenje
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, csv.toString())
                        type = "text/csv"
                        putExtra(Intent.EXTRA_SUBJECT, "SuperstockGO Izveštaj")
                    }

                    startActivity(Intent.createChooser(shareIntent, "Sačuvaj CSV"))

                    Toast.makeText(this@SimpleStatsActivity,
                        "✅ CSV izveštaj spreman",
                        Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SimpleStatsActivity,
                        "Greška pri exportu: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // NOVO: METODA ZA EMAIL IZVEŠTAJ
    private fun sendEmailReport() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val proizvodi = repository.getProizvodiSuspended()

                val total = proizvodi.size
                val lowStock = proizvodi.count { it.kolicina in 1..5 }
                val outOfStock = proizvodi.count { it.kolicina == 0 }

                val emailBody = """
                SuperstockGO - Izveštaj
                ======================
                
                Datum: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}
                
                📊 Statistika:
                - Ukupno proizvoda: $total
                - Niska zaliha: $lowStock
                - Bez zaliha: $outOfStock
                
                Top 5 niska zaliha:
                ${proizvodi.filter { it.kolicina <= 5 }
                    .sortedBy { it.kolicina }
                    .take(5)
                    .joinToString("\n") { "- ${it.naziv} (${it.kolicina} kom)" }}
                
                Hvala što koristite SuperstockGO!
                """.trimIndent()

                withContext(Dispatchers.Main) {
                    val emailIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("jocaned@gmail.com"))
                        putExtra(Intent.EXTRA_SUBJECT, "SuperstockGO Izveštaj")
                        putExtra(Intent.EXTRA_TEXT, emailBody)
                    }

                    try {
                        startActivity(Intent.createChooser(emailIntent, "Pošalji izveštaj"))
                    } catch (e: Exception) {
                        Toast.makeText(this@SimpleStatsActivity,
                            "Nema email aplikacije",
                            Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SimpleStatsActivity,
                        "Greška: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}