package com.jovannedeljkovic.superstockgo

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class LocalBackupHelper(private val context: Context) {

    companion object {
        private const val BACKUP_DIR = "SuperstockGO_Backups"
        private const val BACKUP_PREFIX = "superstock_backup_"
        private const val BACKUP_EXTENSION = ".json"
    }

    /**
     * Kreira backup svih proizvoda u JSON formatu
     */
    fun createBackup(proizvodi: List<Proizvod>): File? {
        return try {

            // Kreiraj direktorijum za backup
            val backupDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                BACKUP_DIR
            )

            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            // Kreiraj ime fajla sa timestamp-om
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "${BACKUP_PREFIX}$timestamp$BACKUP_EXTENSION")

            // Konvertuj proizvode u JSON
            val gson = Gson()
            val json = gson.toJson(proizvodi)

            // Sačuvaj u fajl
            FileOutputStream(backupFile).use { fos ->
                fos.write(json.toByteArray())
            }

            Log.d("LocalBackup", "Backup kreiran: ${backupFile.absolutePath}")
            backupFile

        } catch (e: Exception) {
            Log.e("LocalBackup", "Greška pri kreiranju backup-a: ${e.message}")
            null
        }
    }

    /**
     * Učitava backup iz JSON fajla
     */
    fun restoreFromBackup(backupFile: File): List<Proizvod> {
        return try {
            val json = FileInputStream(backupFile).bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<Proizvod>>() {}.type
            val gson = Gson()
            val proizvodi = gson.fromJson<List<Proizvod>>(json, type)

            Log.d("LocalBackup", "Učitano ${proizvodi.size} proizvoda iz backup-a")
            proizvodi ?: emptyList()

        } catch (e: Exception) {
            Log.e("LocalBackup", "Greška pri učitavanju backup-a: ${e.message}")
            emptyList()
        }
    }

    /**
     * Vraća listu svih dostupnih backup fajlova
     */
    fun getAvailableBackups(): List<File> {
        val backupDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            BACKUP_DIR
        )

        return if (backupDir.exists() && backupDir.isDirectory) {
            backupDir.listFiles { file ->
                file.name.startsWith(BACKUP_PREFIX) && file.name.endsWith(BACKUP_EXTENSION)
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * Briše stari backup fajlove (zadrži samo poslednjih 10)
     */
    fun cleanupOldBackups() {
        try {
            val backups = getAvailableBackups()
            if (backups.size > 10) {
                backups.drop(10).forEach { it.delete() }
                Log.d("LocalBackup", "Obrisano ${backups.size - 10} starih backup-a")
            }
        } catch (e: Exception) {
            Log.e("LocalBackup", "Greška pri čišćenju backup-a: ${e.message}")
        }
    }


    /**
     * Exportuje podatke u CSV format
     */
    fun exportToCSV(proizvodi: List<Proizvod>): File? {
        return try {
            val exportDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "SuperstockGO_Exports"
            )

            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val csvFile = File(exportDir, "superstock_export_$timestamp.csv")

            val csv = StringBuilder()
            // CSV header
            csv.append("ID,Naziv,Kategorija,Količina,Datum\n")

            // CSV podaci
            proizvodi.forEach { proizvod ->
                csv.append("${proizvod.id},\"${proizvod.naziv}\",\"${proizvod.kategorija}\",${proizvod.kolicina},${Date()}\n")
            }

            FileOutputStream(csvFile).use { fos ->
                fos.write(csv.toString().toByteArray())
            }

            Log.d("LocalBackup", "CSV export kreiran: ${csvFile.absolutePath}")
            csvFile

        } catch (e: Exception) {
            Log.e("LocalBackup", "Greška pri CSV exportu: ${e.message}")
            null
        }
    }
}