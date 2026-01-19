package com.jovannedeljkovic.superstockgo

import android.Manifest
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.cardview.widget.CardView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.itextpdf.text.*
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.annotation.RequiresApi

class StatsActivity : AppCompatActivity() {

    private lateinit var repository: Repository
    private lateinit var firebaseHelper: FirebaseHelper

    // UI komponente
    private lateinit var tvTotalProducts: TextView
    private lateinit var tvLowStockCount: TextView
    private lateinit var tvOutOfStock: TextView
    private lateinit var tvTotalQuantity: TextView
    private lateinit var tvPeriod: TextView
    private lateinit var tvGeneratedTime: TextView
    private lateinit var tvLowStockList: TextView
    private lateinit var tvCategoryStats: TextView

    private lateinit var spinnerFilter: Spinner
    private lateinit var btnCustomDate: Button
    private lateinit var btnExportPDF: Button
    private lateinit var btnShare: Button
    private lateinit var btnExportCSV: Button
    private lateinit var btnEmailReport: Button
    private lateinit var progressBar: ProgressBar
    private var lastAction: String = ""

    private lateinit var tvAverageQuantity: TextView
    private lateinit var tvTotalValue: TextView
    private lateinit var tvTopCategory: TextView
    private lateinit var tvNeedReorder: TextView
    private lateinit var tvDistribution: TextView


    private lateinit var cardOverview: CardView
    private lateinit var cardLowStock: CardView
    private lateinit var cardCategories: CardView

    // Filter varijable
    private var selectedFilter = "all" // all, today, week, month, custom
    private var customStartDate: Date? = null
    private var customEndDate: Date? = null

    // PDF export
    private val REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 1001
    private val REQUEST_CODE_MANAGE_STORAGE = 1002

    companion object {
        private const val PDF_DIRECTORY = "SuperstockGO_Reports"
        private const val SHARED_PREFS_TIMESTAMPS = "product_timestamps"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        // ========== TOOLBAR ==========
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        toolbar.title = "📊 Statistike"
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // ========== INICIJALIZACIJA ==========
        repository = Repository(this)
        firebaseHelper = FirebaseHelper(this)

        // ========== POVEŽI VIEW-OVE ==========
        tvTotalProducts = findViewById(R.id.tvTotalProducts)
        tvLowStockCount = findViewById(R.id.tvLowStockCount)
        tvOutOfStock = findViewById(R.id.tvOutOfStock)
        tvTotalQuantity = findViewById(R.id.tvTotalQuantity)
        tvPeriod = findViewById(R.id.tvPeriod)
        tvGeneratedTime = findViewById(R.id.tvGeneratedTime)
        tvLowStockList = findViewById(R.id.tvLowStockList)
        tvCategoryStats = findViewById(R.id.tvCategoryStats)
        tvAverageQuantity = findViewById(R.id.tvAverageQuantity)
        tvTotalValue = findViewById(R.id.tvTotalValue)
        tvTopCategory = findViewById(R.id.tvTopCategory)
        tvNeedReorder = findViewById(R.id.tvNeedReorder)
        tvDistribution = findViewById(R.id.tvDistribution)

        spinnerFilter = findViewById(R.id.spinnerFilter)
        btnCustomDate = findViewById(R.id.btnCustomDate)
        btnExportPDF = findViewById(R.id.btnExportPDF)
        btnShare = findViewById(R.id.btnShare)
        progressBar = findViewById(R.id.progressBar)

        cardOverview = findViewById(R.id.cardOverview)
        cardLowStock = findViewById(R.id.cardLowStock)
        cardCategories = findViewById(R.id.cardCategories)

        // NOVA DUGMAD
        btnExportCSV = findViewById(R.id.btnExportCSV)
        btnEmailReport = findViewById(R.id.btnEmailReport)

        // ========== POSTAVI VREME ==========
        tvGeneratedTime.text = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())

        // ========== POSTAVI SPINNER ==========
        setupFilterSpinner()

        // ========== POSTAVI KLIK LISTENERE ==========
        btnCustomDate.setOnClickListener {
            showDateRangePicker()
        }

        btnExportPDF.setOnClickListener {
            checkPermissionsAndExportPDF()
        }

        btnExportCSV.setOnClickListener {
            checkPermissionsAndExportCSV()
        }

        btnEmailReport.setOnClickListener {
            sendEmailReport()
        }

        btnShare.setOnClickListener {
            shareReport()
        }

        // ========== UČITAJ PODATKE ==========
        loadStatistics()
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        toolbar.title = "\uD83D\uDCCA Statistike i Izveštaji"
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupViews() {
        tvTotalProducts = findViewById(R.id.tvTotalProducts)
        tvLowStockCount = findViewById(R.id.tvLowStockCount)
        tvOutOfStock = findViewById(R.id.tvOutOfStock)
        tvTotalQuantity = findViewById(R.id.tvTotalQuantity)
        tvPeriod = findViewById(R.id.tvPeriod)
        tvGeneratedTime = findViewById(R.id.tvGeneratedTime)
        tvLowStockList = findViewById(R.id.tvLowStockList)
        tvCategoryStats = findViewById(R.id.tvCategoryStats)

        spinnerFilter = findViewById(R.id.spinnerFilter)
        btnCustomDate = findViewById(R.id.btnCustomDate)
        btnExportPDF = findViewById(R.id.btnExportPDF)
        btnShare = findViewById(R.id.btnShare)
        btnExportCSV = findViewById(R.id.btnExportCSV) // Dodajte u XML
        btnEmailReport = findViewById(R.id.btnEmailReport) // Dodajte u XML
        progressBar = findViewById(R.id.progressBar)

        cardOverview = findViewById(R.id.cardOverview)
        cardLowStock = findViewById(R.id.cardLowStock)
        cardCategories = findViewById(R.id.cardCategories)

        // Postavi trenutno vreme
        tvGeneratedTime.text = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
    }

    private fun setupFilterSpinner() {
        val filters = arrayOf(
            "Sve vreme",
            "Danas",
            "Ova nedelja",
            "Ovaj mesec",
            "Prilagođeni period"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filters)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilter.adapter = adapter

        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedFilter = when(position) {
                    0 -> "all"
                    1 -> "today"
                    2 -> "week"
                    3 -> "month"
                    4 -> "custom"
                    else -> "all"
                }

                if (selectedFilter == "custom") {
                    btnCustomDate.visibility = Button.VISIBLE
                    showDateRangePicker()
                } else {
                    btnCustomDate.visibility = Button.GONE
                    loadStatistics()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupClickListeners() {
        btnCustomDate.setOnClickListener {
            showDateRangePicker()
        }

        btnExportPDF.setOnClickListener {
            checkPermissionsAndExportPDF()
        }

        btnShare.setOnClickListener {
            shareReport()
        }
        btnExportCSV.setOnClickListener {
            exportToCSV()
        }
        btnEmailReport.setOnClickListener {
            sendEmailReport()
        }
    }

    private fun exportToCSV() {
        progressBar.visibility = ProgressBar.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val proizvodi = repository.getProizvodiSuspended()
                val csv = StringBuilder()

                // CSV header sa UTF-8 BOM za Excel
                csv.append("\uFEFF") // VAŽNO: UTF-8 BOM
                csv.append("ID;Naziv;Kategorija;Količina;Status\n")

                proizvodi.forEach { proizvod ->
                    val status = when {
                        proizvod.kolicina == 0 -> "NEMA ZALIHA"
                        proizvod.kolicina <= 5 -> "NISKA ZALIHA"
                        else -> "OK"
                    }

                    // Koristite ; umesto , zbog srpskih decimala
                    csv.append("${proizvod.id};${proizvod.naziv};${proizvod.kategorija};${proizvod.kolicina};$status\n")
                }

                withContext(Dispatchers.Main) {
                    // Sačuvaj fajl
                    saveCSVFile(csv.toString())
                    progressBar.visibility = ProgressBar.GONE
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@StatsActivity, "Greška pri exportu CSV: ${e.message}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = ProgressBar.GONE
                }
            }
        }
    }

    private fun saveCSVFile(csvContent: String) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "SuperstockGO_$timestamp.csv"

            // Za Android 10+ koristimo MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SuperstockGO")
                }

                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    resolver.openOutputStream(it)?.use { stream ->
                        stream.write(csvContent.toByteArray(Charsets.UTF_8))
                    }

                    Toast.makeText(this, "CSV sačuvan u Download/SuperstockGO", Toast.LENGTH_LONG).show()
                }
            } else {
                // Za starije Androide
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val superstockDir = File(downloadsDir, "SuperstockGO")
                if (!superstockDir.exists()) {
                    superstockDir.mkdirs()
                }

                val file = File(superstockDir, fileName)
                FileOutputStream(file).use { fos ->
                    fos.write(csvContent.toByteArray(Charsets.UTF_8))
                }

                Toast.makeText(this, "CSV sačuvan: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Greška pri čuvanju CSV: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateAdvancedStatistics(products: List<Proizvod>) {
        // 1. PROSEČNA KOLIČINA PO PROIZVODU
        val averageQuantity = if (products.isNotEmpty()) {
            String.format("%.1f", products.sumOf { it.kolicina } / products.size.toDouble())
        } else "0.0"

        // 2. UKUPNA VREDNOST (pretpostavka: svaki proizvod vredi 100 din)
        val totalValue = products.sumOf { it.kolicina * 100 }

        // 3. NAJZASTUPLJENIJA KATEGORIJA
        val topCategory = products.groupBy { it.kategorija }
            .maxByOrNull { it.value.size }
            ?.key ?: "Nema"

        // 4. PROIZVODI KOJI TREBA NARUČITI (količina = 0)
        val needReorder = products.filter { it.kolicina == 0 }

        // 5. DISTRIBUCIJA PO NIVOIMA ZALIHE
        val distribution = mapOf(
            "Nema" to products.count { it.kolicina == 0 },
            "Kritično" to products.count { it.kolicina in 1..2 },
            "Nisko" to products.count { it.kolicina in 3..5 },
            "Normalno" to products.count { it.kolicina in 6..20 },
            "Visoko" to products.count { it.kolicina > 20 }
        )

        // Prikažite ove statistike u TextView-ovima
        updateUIWithAdvancedStats(averageQuantity, totalValue, topCategory, needReorder.size, distribution)
    }

    /*private fun calculateAdvancedStatistics(products: List<Proizvod>) {
        // 1. PROSEČNA KOLIČINA PO PROIZVODU
        val averageQuantity = if (products.isNotEmpty()) {
            String.format("%.1f", products.sumOf { it.kolicina } / products.size.toDouble())
        } else "0.0"

        // 2. UKUPNA VREDNOST (pretpostavka: svaki proizvod vredi 100 din)
        val totalValue = products.sumOf { it.kolicina * 100 }

        // 3. NAJZASTUPLJENIJA KATEGORIJA
        val topCategory = products.groupBy { it.kategorija }
            .maxByOrNull { it.value.size }
            ?.key ?: "Nema"

        // 4. PROIZVODI KOJI TREBA NARUČITI (količina = 0)
        val needReorder = products.filter { it.kolicina == 0 }

        // 5. DISTRIBUCIJA PO NIVOIMA ZALIHE
        val distribution = mapOf(
            "Nema" to products.count { it.kolicina == 0 },
            "Kritično" to products.count { it.kolicina in 1..2 },
            "Nisko" to products.count { it.kolicina in 3..5 },
            "Normalno" to products.count { it.kolicina in 6..20 },
            "Visoko" to products.count { it.kolicina > 20 }
        )

        // Prikažite ove statistike u TextView-ovima
        updateUIWithAdvancedStats(averageQuantity, totalValue, topCategory, needReorder.size, distribution)
    }
*/
    private fun updateUIWithAdvancedStats(
        averageQuantity: String,
        totalValue: Int,
        topCategory: String,
        needReorderCount: Int,
        distribution: Map<String, Int>
    ) {
        runOnUiThread {
            // Bezbedno pristupanje TextView-ovima
            val tvAverage = findViewById<TextView?>(R.id.tvAverageQuantity)
            val tvValue = findViewById<TextView?>(R.id.tvTotalValue)
            val tvTopCat = findViewById<TextView?>(R.id.tvTopCategory)
            val tvReorder = findViewById<TextView?>(R.id.tvNeedReorder)
            val tvDist = findViewById<TextView?>(R.id.tvDistribution)

            tvAverage?.text = "Prosečno: $averageQuantity kom"
            tvValue?.text = "Vrednost: $totalValue RSD"
            tvTopCat?.text = "Top kategorija: $topCategory"
            tvReorder?.text = "Za naručivanje: $needReorderCount"

            // Formatiraj distribuciju
            val distributionText = buildString {
                append("Distribucija zaliha:\n")
                distribution.forEach { (key, value) ->
                    append("• $key: $value\n")
                }
            }
            tvDist?.text = distributionText
        }
    }
    private fun createPieChart(distribution: Map<String, Int>) {
        val pieChart = findViewById<PieChart>(R.id.pieChart)

        val entries = distribution.map {
            PieEntry(it.value.toFloat(), it.key)
        }

        val dataSet = PieDataSet(entries, "Distribucija zaliha")
        dataSet.colors = listOf(
            Color.RED, Color.YELLOW, Color.rgb(255, 165, 0),
            Color.GREEN, Color.rgb(0, 100, 0)
        )

        val data = PieData(dataSet)
        pieChart.data = data
        pieChart.description.text = "Stanje zaliha"
        pieChart.animateY(1000)
        pieChart.invalidate()
    }
    private fun sendEmailReport() {
        progressBar.visibility = ProgressBar.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val proizvodi = repository.getProizvodiSuspended()

                if (proizvodi.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = ProgressBar.GONE
                        Toast.makeText(this@StatsActivity,
                            "Nema podataka za izveštaj",
                            Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // GRUPIŠI PO KATEGORIJAMA
                val productsByCategory = proizvodi.groupBy { it.kategorija }

                // KREIRAJ DETALJAN EMAIL SADRŽAJ
                val emailBody = buildString {
                    append("=".repeat(50))
                    append("\n")
                    append("SUPERSTOCKGO - KOMPLETAN IZVEŠTAJ O ZALIHAMA\n")
                    append("=".repeat(50))
                    append("\n\n")

                    append("📅 Datum izveštaja: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}")
                    append("\n\n")

                    append("📊 OSNOVNE STATISTIKE")
                    append("\n")
                    append("-".repeat(30))
                    append("\n")

                    val total = proizvodi.size
                    val lowStock = proizvodi.count { it.kolicina in 1..5 }
                    val outOfStock = proizvodi.count { it.kolicina == 0 }
                    val totalQuantity = proizvodi.sumOf { it.kolicina }
                    val averageQuantity = if (total > 0) String.format("%.1f", totalQuantity.toDouble() / total) else "0.0"

                    append("• Ukupno proizvoda: $total\n")
                    append("• Ukupna količina: $totalQuantity kom\n")
                    append("• Prosečna količina: $averageQuantity kom\n")
                    append("• Niska zaliha (1-5 kom): $lowStock\n")
                    append("• Bez zaliha: $outOfStock\n")
                    append("\n")

                    append("📦 KOMPLETAN SPISAK PROIZVODA PO KATEGORIJAMA")
                    append("\n")
                    append("=".repeat(50))
                    append("\n\n")

                    // ZA SVAKU KATEGORIJU
                    productsByCategory.forEach { (category, categoryProducts) ->
                        append("🏷️ KATEGORIJA: $category")
                        append(" (${categoryProducts.size} proizvoda, ")
                        append("Ukupno: ${categoryProducts.sumOf { it.kolicina }} kom)")
                        append("\n")
                        append("-".repeat(40))
                        append("\n")

                        // SORTIRAJ PO NAZIVU
                        val sortedProducts = categoryProducts.sortedBy { it.naziv }

                        sortedProducts.forEachIndexed { index, proizvod ->
                            val status = when {
                                proizvod.kolicina == 0 -> "🔴 NEMA"
                                proizvod.kolicina <= 5 -> "🟡 NISKO"
                                else -> "🟢 OK"
                            }

                            append("${index + 1}. ${proizvod.naziv}")
                            append(" | Količina: ${proizvod.kolicina} kom")
                            append(" | $status")
                            append("\n")
                        }

                        append("\n")
                    }

                    append("📈 SAŽETAK PO KATEGORIJAMA")
                    append("\n")
                    append("-".repeat(30))
                    append("\n")

                    productsByCategory.forEach { (category, categoryProducts) ->
                        val catTotal = categoryProducts.size
                        val catQuantity = categoryProducts.sumOf { it.kolicina }
                        val catLowStock = categoryProducts.count { it.kolicina <= 5 && it.kolicina > 0 }
                        val catOutOfStock = categoryProducts.count { it.kolicina == 0 }

                        append("• $category: $catTotal proizvoda")
                        append(" ($catQuantity kom)")
                        append(" - Nisko: $catLowStock")
                        append(" | Nema: $catOutOfStock")
                        append("\n")
                    }

                    append("\n")
                    append("=".repeat(50))
                    append("\n")
                    append("📋 PREPORUKE:\n")

                    // GENERIŠI PREPORUKE
                    val noStockProducts = proizvodi.filter { it.kolicina == 0 }
                    val lowStockProducts = proizvodi.filter { it.kolicina in 1..5 }

                    if (noStockProducts.isNotEmpty()) {
                        append("\n🔴 HITNO NARUČITI (nema na stanju):\n")
                        noStockProducts.take(10).forEach { proizvod ->
                            append("   - ${proizvod.naziv} (${proizvod.kategorija})\n")
                        }
                        if (noStockProducts.size > 10) {
                            append("   ... i još ${noStockProducts.size - 10} proizvoda\n")
                        }
                    }

                    if (lowStockProducts.isNotEmpty()) {
                        append("\n🟡 NARUČITI USKORO (niska zaliha):\n")
                        lowStockProducts.take(10).forEach { proizvod ->
                            append("   - ${proizvod.naziv} (${proizvod.kategorija}) - ${proizvod.kolicina} kom\n")
                        }
                        if (lowStockProducts.size > 10) {
                            append("   ... i još ${lowStockProducts.size - 10} proizvoda\n")
                        }
                    }

                    if (noStockProducts.isEmpty() && lowStockProducts.isEmpty()) {
                        append("\n✅ Svi proizvodi su na dovoljnoj zalihi!\n")
                    }

                    append("\n")
                    append("=".repeat(50))
                    append("\n")
                    append("Izveštaj generisan aplikacijom SuperstockGO\n")
                    append("Hvala što koristite naš sistem!\n")
                    append("=".repeat(50))
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = ProgressBar.GONE

                    // KREIRAJ EMAIL
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    val currentDate = dateFormat.format(Date())
                    val emailSubject = "SuperstockGO Kompletan Izveštaj - $currentDate"

                    val emailIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("jocaned@gmail.com")) // VAŠ EMAIL
                        putExtra(Intent.EXTRA_SUBJECT, emailSubject)
                        putExtra(Intent.EXTRA_TEXT, emailBody.toString())
                    }

                    try {
                        startActivity(Intent.createChooser(emailIntent, "Pošalji izveštaj na email"))
                    } catch (e: Exception) {
                        Toast.makeText(this@StatsActivity,
                            "Nema email aplikacije instalirane",
                            Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this@StatsActivity,
                        "Greška pri slanju izveštaja: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun showDateRangePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        // Dialog za početni datum
        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val startDate = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            // Dialog za krajnji datum
            DatePickerDialog(this, { _, endYear, endMonth, endDay ->
                val endDate = Calendar.getInstance().apply {
                    set(endYear, endMonth, endDay, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }.time

                // Proveri da li je početni datum pre krajnjeg
                if (startDate.before(endDate) || startDate == endDate) {
                    customStartDate = startDate
                    customEndDate = endDate

                    // Prikaz perioda
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    tvPeriod.text = "${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"

                    loadStatistics()
                } else {
                    Toast.makeText(this, "Početni datum mora biti pre krajnjeg!", Toast.LENGTH_SHORT).show()
                    spinnerFilter.setSelection(0) // Vrati na "Sve vreme"
                }

            }, year, month, day).show()

        }, year, month, day).show()
    }

    private fun loadStatistics() {
        progressBar.visibility = ProgressBar.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allProducts = withContext(Dispatchers.IO) {
                    repository.getProizvodiSuspended()
                }

                // Filtriranje po vremenu
                val filteredProducts = filterProductsByTime(allProducts)

                withContext(Dispatchers.Main) {
                    // 1. OSNOVNE STATISTIKE
                    updateStatistics(filteredProducts)

                    // 2. NAPREDNE STATISTIKE
                    calculateAdvancedStatistics(filteredProducts)

                    // 3. GRAFIKON (ako postoji u layout-u)
                    try {
                        createPieChart(filteredProducts)
                    } catch (e: Exception) {
                        Log.e("StatsActivity", "Greška pri kreiranju grafikona: ${e.message}")
                    }

                    progressBar.visibility = ProgressBar.GONE
                }

            } catch (e: Exception) {
                Log.e("StatsActivity", "Greška pri učitavanju statistika: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@StatsActivity,
                        "Greška pri učitavanju podataka",
                        Toast.LENGTH_SHORT).show()
                    progressBar.visibility = ProgressBar.GONE
                }
            }
        }
    }

    private fun checkPermissionsAndExportCSV() {
        lastAction = "CSV"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                exportToCSVWithMediaStore()
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
                exportToCSVWithMediaStore()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_CODE_WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }

    private fun exportToCSVWithMediaStore() {
        progressBar.visibility = ProgressBar.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val proizvodi = repository.getProizvodiSuspended()

                if (proizvodi.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = ProgressBar.GONE
                        Toast.makeText(this@StatsActivity,
                            "Nema podataka za export",
                            Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // KREIRAJ CSV
                val csv = StringBuilder()
                csv.append("\uFEFF") // UTF-8 BOM za Excel

                // HEADER
                csv.append("KATEGORIJA;NAZIV PROIZVODA;KOLICINA;STATUS\n")

                // POPRAVKA SRPSKIH KARAKTERA - ZAMENITE SA ENGLESKIM EKVIVALENTIMA
                val srpskiKarakteri = mapOf(
                    "č" to "c", "ć" to "c", "š" to "s", "ž" to "z", "đ" to "dj",
                    "Č" to "C", "Ć" to "C", "Š" to "S", "Ž" to "Z", "Đ" to "Dj"
                )

                // SORTIRAJ
                val sortedProducts = proizvodi.sortedWith(
                    compareBy({ it.kategorija }, { it.naziv })
                )

                // DODAJ PROIZVODE
                sortedProducts.forEach { proizvod ->
                    // POPRAVI SRPSKE KARAKTERE
                    var kategorija = proizvod.kategorija
                    var naziv = proizvod.naziv

                    srpskiKarakteri.forEach { (srpski, engleski) ->
                        kategorija = kategorija.replace(srpski, engleski)
                        naziv = naziv.replace(srpski, engleski)
                    }

                    // SPECIFIČNE ZAMENE ZA "PIĆE"
                    kategorija = kategorija.replace("Pie", "Pice")
                        .replace("PIĆE", "PICE")
                        .replace("Pi e", "Pice")

                    val status = when {
                        proizvod.kolicina == 0 -> "NEMA ZALIHE"
                        proizvod.kolicina <= 5 -> "NISKA ZALIHA"
                        else -> "DOBRA ZALIHA"
                    }

                    csv.append("$kategorija;$naziv;${proizvod.kolicina};$status\n")
                }

                // SAČUVAJ
                val success = saveCSVFileCompatible(csv.toString())

                withContext(Dispatchers.Main) {
                    progressBar.visibility = ProgressBar.GONE

                    if (success) {
                        Toast.makeText(this@StatsActivity,
                            "✅ CSV sačuvan u Downloads/SuperstockGO",
                            Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@StatsActivity,
                            "❌ Greška pri čuvanju CSV",
                            Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this@StatsActivity,
                        "Greška pri exportu CSV: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveCSVToDownloads(content: String, fileName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+) - koristite MediaStore
                saveCSVWithMediaStore(content, fileName)
            } else {
                // Android 9 i niže - koristite legacy pristup
                saveCSVLegacy(content, fileName)
            }
        } catch (e: Exception) {
            Log.e("StatsActivity", "Greška pri čuvanju CSV: ${e.message}")
            false
        }
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveCSVWithMediaStore(csvContent: String, fileName: String): Boolean {
        return try {
            val resolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SuperstockGO")
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(csvContent.toByteArray(Charsets.UTF_8))
                }
                true
            } ?: false

        } catch (e: Exception) {
            Log.e("StatsActivity", "Greška pri MediaStore čuvanju: ${e.message}")
            false
        }
    }

    // Za Android 9 i niže
    private fun saveCSVLegacy(csvContent: String, fileName: String): Boolean {
        return try {
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                return false
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val superstockDir = File(downloadsDir, "SuperstockGO")

            if (!superstockDir.exists()) {
                superstockDir.mkdirs()
            }

            val file = File(superstockDir, fileName)
            FileOutputStream(file).use { fos ->
                fos.write(csvContent.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            Log.e("StatsActivity", "Greška pri legacy čuvanju: ${e.message}")
            false
        }
    }

    private fun createPieChart(products: List<Proizvod>) {
        try {
            val pieChart = findViewById<com.github.mikephil.charting.charts.PieChart>(R.id.pieChart)

            // Izračunaj distribuciju
            val distribution = mapOf(
                "Nema" to products.count { it.kolicina == 0 },
                "Kritično" to products.count { it.kolicina in 1..2 },
                "Nisko" to products.count { it.kolicina in 3..5 },
                "Normalno" to products.count { it.kolicina in 6..20 },
                "Visoko" to products.count { it.kolicina > 20 }
            )

            // Filter out zero values for better visualization
            val filteredDistribution = distribution.filter { it.value > 0 }

            if (filteredDistribution.isEmpty()) {
                pieChart.clear()
                pieChart.setNoDataText("Nema podataka za grafik")
                pieChart.setNoDataTextColor(Color.GRAY)
                return
            }

            // Kreiraj unose za grafik
            val entries = filteredDistribution.map {
                com.github.mikephil.charting.data.PieEntry(it.value.toFloat(), it.key)
            }

            val dataSet = com.github.mikephil.charting.data.PieDataSet(entries, "Distribucija zaliha")

            // Boje za svaku kategoriju
            val colors = listOf(
                Color.parseColor("#FF5252"), // Crvena za "Nema"
                Color.parseColor("#FF9800"), // Narandžasta za "Kritično"
                Color.parseColor("#FFEB3B"), // Žuta za "Nisko"
                Color.parseColor("#4CAF50"), // Zelena za "Normalno"
                Color.parseColor("#2E7D32")  // Tamno zelena za "Visoko"
            )

            dataSet.colors = colors.take(filteredDistribution.size)
            dataSet.valueTextSize = 12f
            dataSet.valueTextColor = Color.WHITE

            val data = com.github.mikephil.charting.data.PieData(dataSet)
            pieChart.data = data

            // Konfiguriši izgled
            pieChart.description.text = "Stanje zaliha"
            pieChart.description.textSize = 14f
            pieChart.setEntryLabelTextSize(12f)
            pieChart.setEntryLabelColor(Color.BLACK)

            pieChart.legend.textSize = 12f
            pieChart.legend.verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
            pieChart.legend.horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER

            pieChart.setDrawEntryLabels(true)
            pieChart.setUsePercentValues(true)
            pieChart.setDrawHoleEnabled(true)
            pieChart.holeRadius = 30f
            pieChart.transparentCircleRadius = 35f

            // Animiramo grafikon
            pieChart.animateY(1000, com.github.mikephil.charting.animation.Easing.EaseInOutQuad)

            // Osveži prikaz
            pieChart.invalidate()

        } catch (e: Exception) {
            Log.e("StatsActivity", "Greška pri kreiranju grafikona: ${e.message}")
            // Ako ne postoji PieChart u layout-u, ovo će fail-ovati - to je OK
        }
    }

    private fun filterProductsByTime(products: List<Proizvod>): List<Proizvod> {
        return when(selectedFilter) {
            "all" -> products

            "today" -> {
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time

                products.filter { proizvod ->
                    // Ovde treba da koristite stvarni datum proizvoda
                    // Za sada ćemo pretpostaviti da su svi danas
                    true
                }
            }

            "week" -> {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                val weekAgo = calendar.time

                products.filter { proizvod ->
                    // Ovde treba da koristite stvarni datum proizvoda
                    true
                }
            }

            "month" -> {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MONTH, -1)
                val monthAgo = calendar.time

                products.filter { proizvod ->
                    // Ovde treba da koristite stvarni datum proizvoda
                    true
                }
            }

            "custom" -> {
                if (customStartDate != null && customEndDate != null) {
                    products.filter { proizvod ->
                        // Ovde treba da koristite stvarni datum proizvoda
                        true
                    }
                } else {
                    products
                }
            }

            else -> products
        }
    }

    private fun updateStatistics(products: List<Proizvod>) {
        // Osnovne statistike
        val totalProducts = products.size
        val lowStockCount = products.count { it.kolicina in 1..5 }
        val outOfStockCount = products.count { it.kolicina == 0 }
        val totalQuantity = products.sumOf { it.kolicina }

        tvTotalProducts.text = totalProducts.toString()
        tvLowStockCount.text = lowStockCount.toString()
        tvOutOfStock.text = outOfStockCount.toString()
        tvTotalQuantity.text = totalQuantity.toString()

        // Top 5 niska zaliha
        val lowStockProducts = products
            .filter { it.kolicina <= 5 }
            .sortedBy { it.kolicina }
            .take(5)

        val lowStockText = buildString {
            if (lowStockProducts.isEmpty()) {
                append("Nema proizvoda sa niskom zaliom!")
            } else {
                lowStockProducts.forEachIndexed { index, proizvod ->
                    append("${index + 1}. ${proizvod.naziv} - ${proizvod.kolicina} kom\n")
                    append("   Kategorija: ${proizvod.kategorija}\n\n")
                }
            }
        }
        tvLowStockList.text = lowStockText

        // Statistike po kategorijama
        val categoryStats = products
            .groupBy { it.kategorija }
            .mapValues { (_, proizvodi) ->
                mapOf(
                    "count" to proizvodi.size,
                    "totalQuantity" to proizvodi.sumOf { it.kolicina },
                    "lowStock" to proizvodi.count { it.kolicina <= 5 }
                )
            }
            .toList()
            .sortedByDescending { it.second["count"] as Int }

        val categoryText = buildString {
            if (categoryStats.isEmpty()) {
                append("Nema podataka po kategorijama")
            } else {
                categoryStats.take(5).forEach { (category, stats) ->
                    val categoryName = if (category.length > 20) "${category.substring(0, 17)}..." else category
                    append(" $categoryName:\n")
                    append("     Proizvoda: ${stats["count"]}\n")
                    append("     Ukupno: ${stats["totalQuantity"]} kom\n")
                    append("     Niska zaliha: ${stats["lowStock"]}\n\n")
                }
            }
        }
        tvCategoryStats.text = categoryText

        // Ažuriraj prikaz perioda
        updatePeriodText()
    }

    private fun updatePeriodText() {
        val periodText = when(selectedFilter) {
            "all" -> "Sve vreme"
            "today" -> "Danas (${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())})"
            "week" -> "Ova nedelja"
            "month" -> "Ovaj mesec (${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date())})"
            "custom" -> {
                if (customStartDate != null && customEndDate != null) {
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    "${dateFormat.format(customStartDate!!)} - ${dateFormat.format(customEndDate!!)}"
                } else {
                    "Prilagođeni period"
                }
            }
            else -> "Sve vreme"
        }

        tvPeriod.text = periodText
        tvGeneratedTime.text = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
    }

    // ===== PDF EXPORT =====

    private fun checkPermissionsAndExportPDF() {
        lastAction = "PDF"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                exportToPDF()
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
                exportToPDF()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_CODE_WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CODE_WRITE_EXTERNAL_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Proveri šta je pozvano
                    if (lastAction == "PDF") {
                        exportToPDF()
                    } else if (lastAction == "CSV") {
                        exportToCSVWithMediaStore()
                    }
                } else {
                    Toast.makeText(this, "Dozvola odbijena", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    exportToPDF()
                } else {
                    Toast.makeText(this, "Doza odbijena. Ne možete generisati PDF.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportToPDF() {
        progressBar.visibility = ProgressBar.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allProducts = withContext(Dispatchers.IO) {
                    repository.getProizvodiSuspended()
                }
                val filteredProducts = filterProductsByTime(allProducts)
                val pdfFile = createPDFReport(filteredProducts)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = ProgressBar.GONE

                    if (pdfFile != null) {
                        showExportSuccessDialog(pdfFile)
                    } else {
                        Toast.makeText(this@StatsActivity,
                            "Greška pri generisanju PDF-a",
                            Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("StatsActivity", "Greška pri PDF exportu: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this@StatsActivity,
                        "Greška: ${e.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createPDFReport(products: List<Proizvod>): File? {
        return try {
            // KREIRAJ DIREKTORIJUM
            val downloadsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            } else {
                File(Environment.getExternalStorageDirectory(), "Download")
            }

            val reportsDir = File(downloadsDir, "SuperstockGO_Reports")
            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }

            // KREIRAJ NAZIV FAJLA
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "SuperstockGO_$timestamp.pdf"
            val pdfFile = File(reportsDir, fileName)

            // KREIRAJ PDF
            val document = Document(PageSize.A4)
            PdfWriter.getInstance(document, FileOutputStream(pdfFile))
            document.open()

            // DODAJ SADRŽAJ
            addPDFHeader(document)
            addPDFStats(document, products)
            addPDFProductsByCategory(document, products) // OVO JE NOVA METODA
            addPDFFooter(document)

            document.close()

            pdfFile

        } catch (e: Exception) {
            Log.e("StatsActivity", "PDF greška: ${e.message}")
            null
        }
    }

    private fun addPDFHeaderSimple(document: Document) {
        // Naslov BEZ EMOJI-JA (zbog fontova)
        val titleFont = Font(Font.FontFamily.HELVETICA, 24f, Font.BOLD, BaseColor(0, 102, 204))
        val title = Paragraph("SuperstockGO - Izvestaj", titleFont)
        title.alignment = Element.ALIGN_CENTER
        title.spacingAfter = 20f
        document.add(title)

        // Podnaslov
        val subtitleFont = Font(Font.FontFamily.HELVETICA, 14f, Font.NORMAL, BaseColor.DARK_GRAY)
        val subtitle = Paragraph("Izvestaj o stanju zaliha", subtitleFont)
        subtitle.alignment = Element.ALIGN_CENTER
        subtitle.spacingAfter = 10f
        document.add(subtitle)

        // Period
        val periodFont = Font(Font.FontFamily.HELVETICA, 12f, Font.ITALIC, BaseColor.GRAY)
        val period = Paragraph("Period: ${tvPeriod.text}", periodFont)
        period.alignment = Element.ALIGN_CENTER
        period.spacingAfter = 20f
        document.add(period)

        // Datum generisanja
        val date = Paragraph("Generisano: ${tvGeneratedTime.text}", periodFont)
        date.alignment = Element.ALIGN_CENTER
        date.spacingAfter = 30f
        document.add(date)
    }

    private fun addPDFOverviewSimple(document: Document, products: List<Proizvod>) {
        val totalProducts = products.size
        val lowStockCount = products.count { it.kolicina in 1..5 }
        val outOfStockCount = products.count { it.kolicina == 0 }
        val totalQuantity = products.sumOf { it.kolicina }

        // Napredne statistike
        val averageQuantity = if (products.isNotEmpty()) {
            String.format("%.1f", products.sumOf { it.kolicina } / products.size.toDouble())
        } else "0.0"

        val topCategory = products.groupBy { it.kategorija }
            .maxByOrNull { it.value.size }
            ?.key ?: "Nema"

        val overviewFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor.BLACK)
        val overview = Paragraph("Pregled zaliha", overviewFont)
        overview.spacingAfter = 10f
        document.add(overview)

        val contentFont = Font(Font.FontFamily.HELVETICA, 12f, Font.NORMAL, BaseColor.BLACK)

        val content = """
        Ukupan broj proizvoda: $totalProducts
        Ukupna kolicina: $totalQuantity kom
        Prosecna kolicina: $averageQuantity kom
        Proizvoda sa niskom zaliom (1-5 kom): $lowStockCount
        Proizvoda bez zaliha: $outOfStockCount
        Najzastupljenija kategorija: $topCategory
    """.trimIndent()

        val contentPara = Paragraph(content, contentFont)
        contentPara.spacingAfter = 20f
        document.add(contentPara)
    }

    private fun addPDFLowStockSimple(document: Document, products: List<Proizvod>) {
        val lowStockProducts = products
            .filter { it.kolicina <= 5 }
            .sortedBy { it.kolicina }
            .take(10)

        if (lowStockProducts.isNotEmpty()) {
            val titleFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor.RED)
            val title = Paragraph("Proizvodi sa niskom zaliom (Top 10)", titleFont)
            title.spacingAfter = 10f
            document.add(title)

            val contentFont = Font(Font.FontFamily.HELVETICA, 12f, Font.NORMAL, BaseColor.BLACK)

            lowStockProducts.forEachIndexed { index, proizvod ->
                val item = Paragraph("${index + 1}. ${proizvod.naziv} - ${proizvod.kolicina} kom (${proizvod.kategorija})", contentFont)
                document.add(item)
            }

            document.add(Paragraph(" ", contentFont))
            document.add(Paragraph(" ", contentFont))
        }
    }

    private fun addPDFCategoriesSimple(document: Document, products: List<Proizvod>) {
        val categoryStats = products
            .groupBy { it.kategorija }
            .mapValues { (_, proizvodi) ->
                mapOf(
                    "count" to proizvodi.size,
                    "totalQuantity" to proizvodi.sumOf { it.kolicina }
                )
            }

        if (categoryStats.isNotEmpty()) {
            val titleFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor.BLUE)
            val title = Paragraph("Raspodela po kategorijama", titleFont)
            title.spacingAfter = 10f
            document.add(title)

            // Tabela za kategorije
            val table = PdfPTable(3)
            table.widthPercentage = 100f

            // Zaglavlje tabele
            val headerFont = Font(Font.FontFamily.HELVETICA, 12f, Font.BOLD, BaseColor.WHITE)
            val headerBgColor = BaseColor(0, 102, 204)

            arrayOf("Kategorija", "Broj proizvoda", "Ukupna kolicina").forEach { header ->
                val cell = PdfPCell(Phrase(header, headerFont))
                cell.backgroundColor = headerBgColor
                cell.horizontalAlignment = Element.ALIGN_CENTER
                cell.paddingTop = 8f
                cell.paddingBottom = 8f
                cell.paddingLeft = 8f
                cell.paddingRight = 8f
                table.addCell(cell)
            }

            // Podaci
            val cellFont = Font(Font.FontFamily.HELVETICA, 10f, Font.NORMAL, BaseColor.BLACK)

            categoryStats.forEach { (category, stats) ->
                // Kategorija
                val categoryCell = PdfPCell(Phrase(category, cellFont))
                categoryCell.paddingTop = 6f
                categoryCell.paddingBottom = 6f
                categoryCell.paddingLeft = 6f
                categoryCell.paddingRight = 6f
                table.addCell(categoryCell)

                // Broj proizvoda
                val countCell = PdfPCell(Phrase(stats["count"].toString(), cellFont))
                countCell.horizontalAlignment = Element.ALIGN_CENTER
                countCell.paddingTop = 6f
                countCell.paddingBottom = 6f
                countCell.paddingLeft = 6f
                countCell.paddingRight = 6f
                table.addCell(countCell)

                // Kolicina
                val quantityCell = PdfPCell(Phrase(stats["totalQuantity"].toString(), cellFont))
                quantityCell.horizontalAlignment = Element.ALIGN_CENTER
                quantityCell.paddingTop = 6f
                quantityCell.paddingBottom = 6f
                quantityCell.paddingLeft = 6f
                quantityCell.paddingRight = 6f
                table.addCell(quantityCell)
            }

            document.add(table)
            document.add(Paragraph(" ", cellFont))
        }
    }

    private fun addPDFTableSimple(document: Document, products: List<Proizvod>) {
        if (products.isNotEmpty()) {
            val titleFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor.DARK_GRAY)
            val title = Paragraph("Kompletan spisak proizvoda", titleFont)
            title.spacingAfter = 10f
            document.add(title)

            // Tabela
            val table = PdfPTable(4)
            table.widthPercentage = 100f

            // Zaglavlje
            val headerFont = Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD, BaseColor.WHITE)
            val headerBgColor = BaseColor(79, 129, 189)

            arrayOf("Naziv", "Kategorija", "Kolicina", "Status").forEach { header ->
                val cell = PdfPCell(Phrase(header, headerFont))
                cell.backgroundColor = headerBgColor
                cell.horizontalAlignment = Element.ALIGN_CENTER
                cell.paddingTop = 6f
                cell.paddingBottom = 6f
                cell.paddingLeft = 6f
                cell.paddingRight = 6f
                table.addCell(cell)
            }

            // Podaci
            val cellFont = Font(Font.FontFamily.HELVETICA, 9f, Font.NORMAL, BaseColor.BLACK)

            products.sortedBy { it.naziv }.forEach { proizvod ->
                // Naziv
                val nameCell = PdfPCell(Phrase(proizvod.naziv, cellFont))
                nameCell.paddingTop = 4f
                nameCell.paddingBottom = 4f
                nameCell.paddingLeft = 4f
                nameCell.paddingRight = 4f
                table.addCell(nameCell)

                // Kategorija
                val categoryCell = PdfPCell(Phrase(proizvod.kategorija, cellFont))
                categoryCell.paddingTop = 4f
                categoryCell.paddingBottom = 4f
                categoryCell.paddingLeft = 4f
                categoryCell.paddingRight = 4f
                table.addCell(categoryCell)

                // Kolicina
                val quantityCell = PdfPCell(Phrase("${proizvod.kolicina} kom", cellFont))
                quantityCell.horizontalAlignment = Element.ALIGN_CENTER
                quantityCell.paddingTop = 4f
                quantityCell.paddingBottom = 4f
                quantityCell.paddingLeft = 4f
                quantityCell.paddingRight = 4f
                table.addCell(quantityCell)

                // Status
                val status = when {
                    proizvod.kolicina == 0 -> "NEMA"
                    proizvod.kolicina <= 5 -> "NISKO"
                    else -> "OK"
                }

                val statusColor = when {
                    proizvod.kolicina == 0 -> BaseColor.RED
                    proizvod.kolicina <= 5 -> BaseColor.ORANGE
                    else -> BaseColor.GREEN
                }

                val statusFont = Font(Font.FontFamily.HELVETICA, 9f, Font.BOLD, statusColor)
                val statusCell = PdfPCell(Phrase(status, statusFont))
                statusCell.horizontalAlignment = Element.ALIGN_CENTER
                statusCell.paddingTop = 4f
                statusCell.paddingBottom = 4f
                statusCell.paddingLeft = 4f
                statusCell.paddingRight = 4f
                table.addCell(statusCell)
            }

            document.add(table)
        }
    }

    private fun addPDFFooterSimple(document: Document) {
        document.add(Paragraph(" "))
        document.add(Paragraph(" "))

        val footerFont = Font(Font.FontFamily.HELVETICA, 10f, Font.ITALIC, BaseColor.GRAY)
        val footer = Paragraph("Izvestaj generisan aplikacijom SuperstockGO", footerFont)
        footer.alignment = Element.ALIGN_CENTER
        document.add(footer)
    }
    private fun addBasicStatistics(document: Document, products: List<Proizvod>) {
        val total = products.size
        val lowStock = products.count { it.kolicina in 1..5 }
        val outOfStock = products.count { it.kolicina == 0 }
        val totalQuantity = products.sumOf { it.kolicina }

        val statsFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor.BLACK)
        val statsTitle = Paragraph("Osnovne statistike", statsFont)
        statsTitle.spacingAfter = 10f
        document.add(statsTitle)

        val contentFont = Font(Font.FontFamily.HELVETICA, 12f, Font.NORMAL, BaseColor.BLACK)

        val statsText = """
        Ukupan broj proizvoda: $total
        Ukupna kolicina: $totalQuantity kom
        Proizvoda sa niskom zaliom (1-5 kom): $lowStock
        Proizvoda bez zaliha: $outOfStock
    """.trimIndent()

        val contentPara = Paragraph(statsText, contentFont)
        contentPara.spacingAfter = 20f
        document.add(contentPara)
    }

    private fun addProductsTable(document: Document, products: List<Proizvod>) {
        if (products.isEmpty()) return

        val titleFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor.DARK_GRAY)
        val title = Paragraph("Spisak proizvoda", titleFont)
        title.spacingAfter = 10f
        document.add(title)

        // Kreiraj tabelu sa 4 kolone
        val table = PdfPTable(4)
        table.widthPercentage = 100f

        // Zaglavlje tabele
        val headerFont = Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD, BaseColor.WHITE)
        val headerBgColor = BaseColor(79, 129, 189)

        // ENGLESKI NASLOVI da bi PDF radio
        val headers = arrayOf("Name", "Category", "Quantity", "Status")

        headers.forEach { header ->
            val cell = PdfPCell(Phrase(header, headerFont))
            cell.backgroundColor = headerBgColor
            cell.horizontalAlignment = Element.ALIGN_CENTER
            cell.paddingTop = 6f
            cell.paddingBottom = 6f
            table.addCell(cell)
        }

        // Podaci - srpski karakteri će raditi u sadržaju
        val cellFont = Font(Font.FontFamily.HELVETICA, 9f, Font.NORMAL, BaseColor.BLACK)

        products.sortedBy { it.naziv }.forEach { proizvod ->
            // Naziv (može imati srpske karaktere)
            table.addCell(PdfPCell(Phrase(proizvod.naziv, cellFont)))

            // Kategorija
            table.addCell(PdfPCell(Phrase(proizvod.kategorija, cellFont)))

            // Količina
            val quantityCell = PdfPCell(Phrase("${proizvod.kolicina} kom", cellFont))
            quantityCell.horizontalAlignment = Element.ALIGN_CENTER
            table.addCell(quantityCell)

            // Status
            val status = when {
                proizvod.kolicina == 0 -> "NEMA"
                proizvod.kolicina <= 5 -> "NISKO"
                else -> "OK"
            }

            val statusColor = when {
                proizvod.kolicina == 0 -> BaseColor.RED
                proizvod.kolicina <= 5 -> BaseColor.ORANGE
                else -> BaseColor.GREEN
            }

            val statusFont = Font(Font.FontFamily.HELVETICA, 9f, Font.BOLD, statusColor)
            val statusCell = PdfPCell(Phrase(status, statusFont))
            statusCell.horizontalAlignment = Element.ALIGN_CENTER
            table.addCell(statusCell)
        }

        document.add(table)
    }

    // U PDF kreiranju, promenite:
    private fun addPDFHeader(document: Document) {
        val title = Paragraph("SUPERSTOCKGO - KOMPLETAN IZVEŠTAJ\n\n",
            Font(Font.FontFamily.HELVETICA, 20f, Font.BOLD, BaseColor(0, 102, 204)))
        title.alignment = Element.ALIGN_CENTER
        document.add(title)

        val date = Paragraph("Datum: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}\n\n",
            Font(Font.FontFamily.HELVETICA, 12f, Font.NORMAL, BaseColor.BLACK))
        date.alignment = Element.ALIGN_CENTER
        document.add(date)
    }



    private fun addPDFOverview(document: Document, products: List<Proizvod>) {
        val totalProducts = products.size
        val lowStockCount = products.count { it.kolicina in 1..5 }
        val outOfStockCount = products.count { it.kolicina == 0 }
        val totalQuantity = products.sumOf { it.kolicina }

        val overviewFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor.BLACK)
        val overview = Paragraph("Pregled zaliha", overviewFont)
        overview.spacingAfter = 10f
        document.add(overview)

        val contentFont = Font(Font.FontFamily.HELVETICA, 12f, Font.NORMAL, BaseColor.BLACK)

        val content = """
            Ukupan broj proizvoda: $totalProducts
            Ukupna količina: $totalQuantity kom
            Proizvoda sa niskom zaliom (1-5 kom): $lowStockCount
            Proizvoda bez zaliha: $outOfStockCount
        """.trimIndent()

        val contentPara = Paragraph(content, contentFont)
        contentPara.spacingAfter = 20f
        document.add(contentPara)
    }

    private fun addPDFLowStock(document: Document, products: List<Proizvod>) {
        val lowStockProducts = products
            .filter { it.kolicina <= 5 }
            .sortedBy { it.kolicina }
            .take(10)

        if (lowStockProducts.isNotEmpty()) {
            val titleFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor.RED)
            val title = Paragraph("Proizvodi sa niskom zaliom (Top 10)", titleFont)
            title.spacingAfter = 10f
            document.add(title)

            val contentFont = Font(Font.FontFamily.HELVETICA, 12f, Font.NORMAL, BaseColor.BLACK)

            lowStockProducts.forEachIndexed { index, proizvod ->
                val item = Paragraph("${index + 1}. ${proizvod.naziv} - ${proizvod.kolicina} kom (${proizvod.kategorija})", contentFont)
                document.add(item)
            }

            document.add(Paragraph(" ", contentFont))
            document.add(Paragraph(" ", contentFont))
        }
    }

    private fun addPDFCategories(document: Document, products: List<Proizvod>) {
        val categoryStats = products
            .groupBy { it.kategorija }
            .mapValues { (_, proizvodi) ->
                mapOf(
                    "count" to proizvodi.size,
                    "totalQuantity" to proizvodi.sumOf { it.kolicina }
                )
            }

        if (categoryStats.isNotEmpty()) {
            val titleFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor.BLUE)
            val title = Paragraph("Raspodela po kategorijama", titleFont)
            title.spacingAfter = 10f
            document.add(title)

            // Tabela za kategorije
            val table = PdfPTable(3)
            table.widthPercentage = 100f

            // Zaglavlje tabele
            val headerFont = Font(Font.FontFamily.HELVETICA, 12f, Font.BOLD, BaseColor.WHITE)
            val headerBgColor = BaseColor(0, 102, 204)

            arrayOf("Kategorija", "Broj proizvoda", "Ukupna količina").forEach { header ->
                val cell = PdfPCell(Phrase(header, headerFont))
                cell.backgroundColor = headerBgColor
                cell.horizontalAlignment = Element.ALIGN_CENTER
                cell.paddingTop = 8f
                cell.paddingBottom = 8f
                cell.paddingLeft = 8f
                cell.paddingRight = 8f
                table.addCell(cell)
            }

            // Podaci
            val cellFont = Font(Font.FontFamily.HELVETICA, 10f, Font.NORMAL, BaseColor.BLACK)

            categoryStats.forEach { (category, stats) ->
                // Kategorija
                val categoryCell = PdfPCell(Phrase(category, cellFont))
                categoryCell.paddingTop = 6f
                categoryCell.paddingBottom = 6f
                categoryCell.paddingLeft = 6f
                categoryCell.paddingRight = 6f
                table.addCell(categoryCell)

                // Broj proizvoda
                val countCell = PdfPCell(Phrase(stats["count"].toString(), cellFont))
                countCell.horizontalAlignment = Element.ALIGN_CENTER
                countCell.paddingTop = 6f
                countCell.paddingBottom = 6f
                countCell.paddingLeft = 6f
                countCell.paddingRight = 6f
                table.addCell(countCell)

                // Količina
                val quantityCell = PdfPCell(Phrase(stats["totalQuantity"].toString(), cellFont))
                quantityCell.horizontalAlignment = Element.ALIGN_CENTER
                quantityCell.paddingTop = 6f
                quantityCell.paddingBottom = 6f
                quantityCell.paddingLeft = 6f
                quantityCell.paddingRight = 6f
                table.addCell(quantityCell)
            }

            document.add(table)
            document.add(Paragraph(" ", cellFont))
        }
    }

    private fun addPDFTable(document: Document, products: List<Proizvod>) {
        if (products.isNotEmpty()) {
            val titleFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor.DARK_GRAY)
            val title = Paragraph("Kompletan spisak proizvoda", titleFont)
            title.spacingAfter = 10f
            document.add(title)

            // Tabela
            val table = PdfPTable(4)
            table.widthPercentage = 100f

            // Zaglavlje
            val headerFont = Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD, BaseColor.WHITE)
            val headerBgColor = BaseColor(79, 129, 189)

            arrayOf("Naziv", "Kategorija", "Količina", "Status").forEach { header ->
                val cell = PdfPCell(Phrase(header, headerFont))
                cell.backgroundColor = headerBgColor
                cell.horizontalAlignment = Element.ALIGN_CENTER
                cell.paddingTop = 6f
                cell.paddingBottom = 6f
                cell.paddingLeft = 6f
                cell.paddingRight = 6f
                table.addCell(cell)
            }

            // Podaci
            val cellFont = Font(Font.FontFamily.HELVETICA, 9f, Font.NORMAL, BaseColor.BLACK)

            products.sortedBy { it.naziv }.forEach { proizvod ->
                // Naziv
                val nameCell = PdfPCell(Phrase(proizvod.naziv, cellFont))
                nameCell.paddingTop = 4f
                nameCell.paddingBottom = 4f
                nameCell.paddingLeft = 4f
                nameCell.paddingRight = 4f
                table.addCell(nameCell)

                // Kategorija
                val categoryCell = PdfPCell(Phrase(proizvod.kategorija, cellFont))
                categoryCell.paddingTop = 4f
                categoryCell.paddingBottom = 4f
                categoryCell.paddingLeft = 4f
                categoryCell.paddingRight = 4f
                table.addCell(categoryCell)

                // Količina
                val quantityCell = PdfPCell(Phrase("${proizvod.kolicina} kom", cellFont))
                quantityCell.horizontalAlignment = Element.ALIGN_CENTER
                quantityCell.paddingTop = 4f
                quantityCell.paddingBottom = 4f
                quantityCell.paddingLeft = 4f
                quantityCell.paddingRight = 4f
                table.addCell(quantityCell)

                // Status
                val status = when {
                    proizvod.kolicina == 0 -> "NEMA"
                    proizvod.kolicina <= 5 -> "NISKO"
                    else -> "OK"
                }

                val statusColor = when {
                    proizvod.kolicina == 0 -> BaseColor.RED
                    proizvod.kolicina <= 5 -> BaseColor.ORANGE
                    else -> BaseColor.GREEN
                }

                val statusFont = Font(Font.FontFamily.HELVETICA, 9f, Font.BOLD, statusColor)
                val statusCell = PdfPCell(Phrase(status, statusFont))
                statusCell.horizontalAlignment = Element.ALIGN_CENTER
                statusCell.paddingTop = 4f
                statusCell.paddingBottom = 4f
                statusCell.paddingLeft = 4f
                statusCell.paddingRight = 4f
                table.addCell(statusCell)
            }

            document.add(table)
        }
    }

    // DODAJTE OVU METODU ZA STATISTIKE:
    private fun addPDFStats(document: Document, products: List<Proizvod>) {
        val statsTitle = Paragraph("STATISTIKA\n",
            Font(Font.FontFamily.HELVETICA, 16f, Font.BOLD, BaseColor.BLACK))
        statsTitle.spacingAfter = 10f
        document.add(statsTitle)

        val total = products.size
        val lowStock = products.count { it.kolicina in 1..5 }
        val outOfStock = products.count { it.kolicina == 0 }
        val totalQuantity = products.sumOf { it.kolicina }
        val averageQuantity = if (total > 0)
            String.format("%.1f", totalQuantity.toDouble() / total)
        else "0.0"

        val statsText = """
        Ukupno proizvoda: $total
        Ukupna kolicina: $totalQuantity kom
        Prosecna kolicina: $averageQuantity kom
        Niska zaliha (1-5 kom): $lowStock
        Bez zaliha: $outOfStock
        
    """.trimIndent()

        val stats = Paragraph(statsText,
            Font(Font.FontFamily.HELVETICA, 12f, Font.NORMAL, BaseColor.BLACK))
        stats.spacingAfter = 20f
        document.add(stats)
    }

    // DODAJTE OVU METODU ZA PROIZVODE PO KATEGORIJAMA (KLJUČNA!):
    private fun addPDFProductsByCategory(document: Document, products: List<Proizvod>) {
        if (products.isEmpty()) return

        val tableTitle = Paragraph("KOMPLETAN SPISAK PROIZVODA\n",
            Font(Font.FontFamily.HELVETICA, 16f, Font.BOLD, BaseColor.BLACK))
        tableTitle.spacingAfter = 15f
        document.add(tableTitle)

        // GRUPIŠI PROIZVODE PO KATEGORIJAMA
        val productsByCategory = products.groupBy { it.kategorija }

        productsByCategory.forEach { (category, categoryProducts) ->
            // NASLOV KATEGORIJE (BEZ EMOJI-JA ZBOG PDF FONTOVA)
            val categoryTitle = Paragraph("KATEGORIJA: $category (${categoryProducts.size} proizvoda)\n",
                Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor(0, 102, 204)))
            categoryTitle.spacingAfter = 10f
            document.add(categoryTitle)

            // KREIRAJ TABELU
            val table = PdfPTable(3)
            table.widthPercentage = 100f
            table.setWidths(floatArrayOf(4f, 1.5f, 1.5f))

            // ZAGLAVLJE
            val headers = arrayOf("NAZIV PROIZVODA", "KOLICINA", "STATUS")
            headers.forEach { header ->
                val cell = PdfPCell(Phrase(header,
                    Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD, BaseColor.WHITE)))
                cell.backgroundColor = BaseColor(79, 129, 189)
                cell.horizontalAlignment = Element.ALIGN_CENTER
                cell.setPadding(5f)
                table.addCell(cell)
            }

            // PROIZVODI
            categoryProducts.sortedBy { it.naziv }.forEach { proizvod ->
                // NAZIV
                table.addCell(PdfPCell(Phrase(proizvod.naziv,
                    Font(Font.FontFamily.HELVETICA, 9f, Font.NORMAL, BaseColor.BLACK))))

                // KOLIČINA
                val qtyCell = PdfPCell(Phrase("${proizvod.kolicina} kom",
                    Font(Font.FontFamily.HELVETICA, 9f, Font.NORMAL, BaseColor.BLACK)))
                qtyCell.horizontalAlignment = Element.ALIGN_CENTER
                table.addCell(qtyCell)

                // STATUS
                val status = when {
                    proizvod.kolicina == 0 -> "NEMA"
                    proizvod.kolicina <= 5 -> "NISKO"
                    else -> "OK"
                }

                val statusColor = when {
                    proizvod.kolicina == 0 -> BaseColor.RED
                    proizvod.kolicina <= 5 -> BaseColor.ORANGE
                    else -> BaseColor.GREEN
                }

                val statusCell = PdfPCell(Phrase(status,
                    Font(Font.FontFamily.HELVETICA, 9f, Font.BOLD, statusColor)))
                statusCell.horizontalAlignment = Element.ALIGN_CENTER
                table.addCell(statusCell)
            }

            document.add(table)
            document.add(Paragraph("\n"))
        }
    }

    private fun saveCSVFileCompatible(csvContent: String): Boolean {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "SuperstockGO_${timestamp}.csv"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveCSVWithMediaStore(csvContent, fileName)
            } else {
                saveCSVLegacy(csvContent, fileName)
            }
        } catch (e: Exception) {
            Log.e("StatsActivity", "Greška pri čuvanju CSV: ${e.message}")
            false
        }
    }
    private fun addPDFFooter(document: Document) {
        document.add(Paragraph(" "))
        document.add(Paragraph(" "))

        val footerFont = Font(Font.FontFamily.HELVETICA, 10f, Font.ITALIC, BaseColor.GRAY)
        val footer = Paragraph("Izveštaj generisan aplikacijom SuperstockGO\nwww.superstockgo.com", footerFont)
        footer.alignment = Element.ALIGN_CENTER
        document.add(footer)
    }

    private fun showExportSuccessDialog(pdfFile: File) {
        AlertDialog.Builder(this)
            .setTitle(" PDF uspešno generisan!")
            .setMessage("Izveštaj je sačuvan u: ${pdfFile.absolutePath}")
            .setPositiveButton("Otvori PDF") { _, _ ->
                openPDFFile(pdfFile)
            }
            .setNegativeButton("Podeli") { _, _ ->
                sharePDFFile(pdfFile)
            }
            .setNeutralButton("OK") { _, _ -> }
            .show()
    }

    private fun openPDFFile(pdfFile: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(this, "$packageName.provider", pdfFile)
        } else {
            Uri.fromFile(pdfFile)
        }

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/pdf")
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Nema aplikacije za prikaz PDF-a", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePDFFile(pdfFile: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(this, "$packageName.provider", pdfFile)
        } else {
            Uri.fromFile(pdfFile)
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "application/pdf"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Podeli PDF izveštaj"))
    }

    private fun shareReport() {
        progressBar.visibility = ProgressBar.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val proizvodi = repository.getProizvodiSuspended()

                val shareText = buildString {
                    append("📊 SUPERSTOCKGO - PREGLED ZALIHA\n")
                    append("Datum: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}\n")
                    append("\n")

                    // OSNOVNE STATISTIKE
                    append("📈 STATISTIKA:\n")
                    append("• Ukupno proizvoda: ${proizvodi.size}\n")
                    append("• Niska zaliha: ${proizvodi.count { it.kolicina in 1..5 }}\n")
                    append("• Bez zaliha: ${proizvodi.count { it.kolicina == 0 }}\n")
                    append("\n")

                    // TOP 5 PROIZVODA PO KATEGORIJI
                    append("🏆 NAJVAŽNIJE:\n")

                    // PROIZVODI BEZ ZALIHE
                    val noStock = proizvodi.filter { it.kolicina == 0 }.take(5)
                    if (noStock.isNotEmpty()) {
                        append("🔴 HITNO NARUČITI:\n")
                        noStock.forEach {
                            append("   - ${it.naziv} (${it.kategorija})\n")
                        }
                        append("\n")
                    }

                    // PROIZVODI SA NISKOM ZALIHOM
                    val lowStock = proizvodi.filter { it.kolicina in 1..5 }.take(5)
                    if (lowStock.isNotEmpty()) {
                        append("🟡 NARUČITI USKORO:\n")
                        lowStock.forEach {
                            append("   - ${it.naziv} (${it.kategorija}) - ${it.kolicina} kom\n")
                        }
                        append("\n")
                    }

                    // UKUPAN BROJ PO KATEGORIJAMA
                    append("📦 PO KATEGORIJAMA:\n")
                    proizvodi.groupBy { it.kategorija }.forEach { (category, items) ->
                        append("• $category: ${items.size} proizvoda\n")
                    }

                    append("\n")
                    append("📱 Generisano SuperstockGO aplikacijom")
                    append("\n")
                    append("#inventory #stock #superstockgo")
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = ProgressBar.GONE

                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText.toString())
                        type = "text/plain"
                    }

                    startActivity(Intent.createChooser(shareIntent, "Podeli izveštaj"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this@StatsActivity,
                        "Greška pri deljenju izveštaja",
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