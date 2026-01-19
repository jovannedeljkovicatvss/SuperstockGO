package com.jovannedeljkovic.superstockgo

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class BackupRestoreActivity : AppCompatActivity() {

    private lateinit var repository: Repository
    private lateinit var backupHelper: LocalBackupHelper
    private lateinit var btnCreateBackup: Button
    private lateinit var btnRestoreBackup: Button
    private lateinit var btnExportCSV: Button
    private lateinit var listViewBackups: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_restore)

        repository = Repository(this)
        backupHelper = LocalBackupHelper(this)

        btnCreateBackup = findViewById(R.id.btnCreateBackup)
        btnRestoreBackup = findViewById(R.id.btnRestoreBackup)
        btnExportCSV = findViewById(R.id.btnExportCSV)
        listViewBackups = findViewById(R.id.listViewBackups)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)

        setupListeners()
        loadBackupList()
    }

    private fun setupListeners() {
        btnCreateBackup.setOnClickListener {
            createBackup()
        }

        btnRestoreBackup.setOnClickListener {
            showRestoreOptions()
        }

        btnExportCSV.setOnClickListener {
            exportToCSV()
        }
    }

    private fun loadBackupList() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val backups = backupHelper.getAvailableBackups()

                withContext(Dispatchers.Main) {
                    if (backups.isEmpty()) {
                        tvStatus.text = "Nema backup fajlova"
                        return@withContext
                    }

                    val backupNames = backups.map { file ->
                        val date = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(file.lastModified()))
                        "${file.nameWithoutExtension} ($date)"
                    }

                    val adapter = ArrayAdapter(
                        this@BackupRestoreActivity,
                        android.R.layout.simple_list_item_1,
                        backupNames
                    )
                    listViewBackups.adapter = adapter

                    listViewBackups.setOnItemClickListener { _, _, position, _ ->
                        if (position < backups.size) {
                            val backupFile = backups[position]
                            showBackupOptionsDialog(backupFile)
                        }
                    }

                    tvStatus.text = "Pronađeno ${backups.size} backup fajlova"
                }
            } catch (e: Exception) {
                Log.e("BackupRestore", "Greška pri učitavanju backup-a: ${e.message}")
            }
        }
    }

    private fun createBackup() {
        progressBar.visibility = ProgressBar.VISIBLE
        tvStatus.text = "Kreiranje backup-a..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.sviProizvodi { proizvodi ->
                    if (proizvodi.isEmpty()) {
                        runOnUiThread {
                            progressBar.visibility = ProgressBar.GONE
                            Toast.makeText(
                                this@BackupRestoreActivity,
                                "Nema podataka za backup",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@sviProizvodi
                    }

                    // Kreiraj backup
                    val backupFile = backupHelper.createBackup(proizvodi)

                    runOnUiThread {
                        progressBar.visibility = ProgressBar.GONE

                        if (backupFile != null) {
                            tvStatus.text = "Backup kreiran!"

                            AlertDialog.Builder(this@BackupRestoreActivity)
                                .setTitle("✅ Backup uspešan!")
                                .setMessage("Backup je sačuvan u:\n${backupFile.absolutePath}")
                                .setPositiveButton("OK") { _, _ ->
                                    loadBackupList()
                                }
                                .show()
                        } else {
                            Toast.makeText(
                                this@BackupRestoreActivity,
                                "Greška pri kreiranju backup-a",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(
                        this@BackupRestoreActivity,
                        "Greška: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showRestoreOptions() {
        val backups = backupHelper.getAvailableBackups()

        if (backups.isEmpty()) {
            Toast.makeText(this, "Nema dostupnih backup fajlova", Toast.LENGTH_SHORT).show()
            return
        }

        val backupNames = backups.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Odaberite backup za restore")
            .setItems(backupNames) { _, which ->
                val selectedBackup = backups[which]
                confirmRestore(selectedBackup)
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun confirmRestore(backupFile: File) {
        AlertDialog.Builder(this)
            .setTitle("Potvrda restore-a")
            .setMessage("Da li želite da RESTORE-UJETE podatke iz backup-a?\n\n" +
                    "Fajl: ${backupFile.name}\n" +
                    "⚠️ Ova akcija će ZAMENITI postojeće lokalne podatke!")
            .setPositiveButton("Restore") { _, _ ->
                performRestore(backupFile)  // OVO JE KLJUČNO - DODAJTE OVO
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun performRestore(backupFile: File) {
        progressBar.visibility = ProgressBar.VISIBLE
        tvStatus.text = "🚚 Učitavam backup podatke..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Učitaj proizvode iz backup fajla
                val backupProizvodi = backupHelper.restoreFromBackup(backupFile)

                if (backupProizvodi.isEmpty()) {
                    runOnUiThread {
                        progressBar.visibility = ProgressBar.GONE
                        Toast.makeText(
                            this@BackupRestoreActivity,
                            "Backup fajl je prazan ili nevažeći",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                runOnUiThread {
                    tvStatus.text = "🔄 Restore u toku (${backupProizvodi.size} proizvoda)..."
                }

                // 2. Obriši sve postojeće podatke
                repository.sviProizvodi { postojeciProizvodi ->
                    // Obriši sve postojeće proizvode
                    var obrisano = 0
                    val ukupno = postojeciProizvodi.size

                    if (ukupno > 0) {
                        postojeciProizvodi.forEach { proizvod ->
                            repository.obrisiProizvod(proizvod) { success ->
                                obrisano++
                                Log.d("Restore", "Obrisan stari proizvod: $obrisano/$ukupno")

                                // Kada su svi obrisani, dodaj nove
                                if (obrisano == ukupno) {
                                    addNewProductsFromBackup(backupProizvodi)
                                }
                            }
                        }
                    } else {
                        // Nema postojećih podataka, direktno dodaj
                        addNewProductsFromBackup(backupProizvodi)
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(
                        this@BackupRestoreActivity,
                        "Greška pri restore: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun addNewProductsFromBackup(backupProizvodi: List<Proizvod>) {
        var dodato = 0
        val ukupno = backupProizvodi.size

        backupProizvodi.forEach { proizvod ->
            // Resetuj ID na 0 da se generiše novi ID
            val proizvodZaDodavanje = proizvod.copy(id = 0)

            repository.dodajProizvod(proizvodZaDodavanje) { success, message ->
                dodato++
                Log.d("Restore", "Dodat proizvod iz backup-a: $dodato/$ukupno")

                // Kada su svi dodati
                if (dodato == ukupno) {
                    runOnUiThread {
                        progressBar.visibility = ProgressBar.GONE
                        tvStatus.text = "✅ Restore završen!"

                        AlertDialog.Builder(this@BackupRestoreActivity)
                            .setTitle("✅ Restore uspešan!")
                            .setMessage("Uspešno je restore-ovano $ukupno proizvoda iz backup-a.")
                            .setPositiveButton("OK") { _, _ ->
                                // Osveži listu backup fajlova
                                loadBackupList()

                                // Pošalji broadcast da se osveže MainActivity
                                val intent = Intent("PODACI_OSVEŽENI")
                                LocalBroadcastManager.getInstance(this@BackupRestoreActivity).sendBroadcast(intent)
                            }
                            .show()
                    }
                }
            }
        }
    }

    // Opciono: Kreiraj backup pre restore-a kao sigurnosnu kopiju
    /*private fun createSafetyBackupBeforeRestore(onComplete: () -> Unit) {
        repository.sviProizvodi { postojeciProizvodi ->
            if (postojeciProizvodi.isNotEmpty()) {
                val safetyBackup = backupHelper.createBackup(postojeciProizvodi)
                if (safetyBackup != null) {
                    Log.d("Restore", "Sigurnosni backup kreiran: ${safetyBackup.name}")
                }
            }
            onComplete()
        }
    }
*/
    private fun exportToCSV() {
        progressBar.visibility = ProgressBar.VISIBLE
        tvStatus.text = "Export u CSV..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.sviProizvodi { proizvodi ->
                    val csvFile = backupHelper.exportToCSV(proizvodi)

                    runOnUiThread {
                        progressBar.visibility = ProgressBar.GONE

                        if (csvFile != null) {
                            AlertDialog.Builder(this@BackupRestoreActivity)
                                .setTitle("✅ CSV Export uspešan")
                                .setMessage("Fajl je sačuvan u:\n${csvFile.absolutePath}")
                                .setPositiveButton("OK", null)
                                .show()
                        } else {
                            Toast.makeText(
                                this@BackupRestoreActivity,
                                "Greška pri exportu u CSV",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(
                        this@BackupRestoreActivity,
                        "Greška: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showBackupOptionsDialog(backupFile: File) {
        val options = arrayOf("Pregledaj", "Obriši", "Otkaži")

        AlertDialog.Builder(this)
            .setTitle("Opcije za: ${backupFile.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> previewBackup(backupFile) // Pregledaj
                    1 -> deleteBackup(backupFile) // Obriši
                    // 2 -> Otkaži
                }
            }
            .show()
    }

    private fun deleteBackup(backupFile: File) {
        AlertDialog.Builder(this)
            .setTitle("Brisanje backup-a")
            .setMessage("Da li ste sigurni da želite da obrišete ovaj backup?")
            .setPositiveButton("Obriši") { _, _ ->
                if (backupFile.delete()) {
                    Toast.makeText(this, "Backup obrisan", Toast.LENGTH_SHORT).show()
                    loadBackupList()
                }
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun previewBackup(backupFile: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val proizvodi = backupHelper.restoreFromBackup(backupFile)

                withContext(Dispatchers.Main) {
                    val previewText = StringBuilder()
                    previewText.append("Backup: ${backupFile.name}\n")
                    previewText.append("Broj proizvoda: ${proizvodi.size}\n\n")

                    proizvodi.take(10).forEachIndexed { index, proizvod ->
                        previewText.append("${index + 1}. ${proizvod.naziv} (${proizvod.kolicina} kom)\n")
                    }

                    if (proizvodi.size > 10) {
                        previewText.append("\ni još ${proizvodi.size - 10} proizvoda...")
                    }

                    AlertDialog.Builder(this@BackupRestoreActivity)
                        .setTitle("Pregled backup-a")
                        .setMessage(previewText.toString())
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@BackupRestoreActivity,
                        "Greška pri pregledu: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    fun onBackClicked(view: android.view.View) {
        finish()
    }
}