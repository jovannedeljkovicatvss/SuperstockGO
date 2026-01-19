package com.jovannedeljkovic.superstockgo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.appcompat.app.AlertDialog
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class CloudSyncActivity : AppCompatActivity() {

    private lateinit var btnBackup: Button
    private lateinit var btnRestore: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var btnSyncBothWays: Button

    private lateinit var repository: Repository
    private lateinit var firebaseHelper: FirebaseHelper

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_sync)

        repository = Repository(this)
        firebaseHelper = FirebaseHelper(this)

        btnBackup = findViewById(R.id.btnBackup)
        btnRestore = findViewById(R.id.btnRestore)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        btnSyncBothWays = findViewById(R.id.btnSyncBothWays)

        btnBackup.setOnClickListener {
            Log.d("CloudSync", "Klik na Backup dugme")
            backupToCloud()
        }

        btnRestore.setOnClickListener {
            Log.d("CloudSync", "Klik na Restore dugme")
            restoreFromCloud()
        }

        btnSyncBothWays.setOnClickListener {
            Log.d("CloudSync", "Klik na Dvosmerna komunikacija dugme")
            simpleTwoWaySync()
        }

        // Proveri da li je korisnik ulogovan
        val currentUser = firebaseHelper.getCurrentUser()
        if (currentUser == null) {
            tvStatus.text = "❌ Niste prijavljeni"
            btnBackup.isEnabled = false
            btnRestore.isEnabled = false
            btnSyncBothWays.isEnabled = false
            Toast.makeText(this, "Morate biti prijavljeni za Cloud sinhronizaciju", Toast.LENGTH_LONG).show()
        } else {
            tvStatus.text = "✅ Prijavljen: ${currentUser.email}"
            checkSyncStatus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    private fun checkSyncStatus() {
        coroutineScope.launch {
            try {
                val lokalniProizvodi = withContext(Dispatchers.IO) {
                    getLocalProductsSuspended()
                }
                val localCount = lokalniProizvodi.size

                delay(1000)

                val cloudProizvodi = withContext(Dispatchers.IO) {
                    getCloudProductsSuspended()
                }
                val cloudCount = cloudProizvodi.size

                tvStatus.text = "📊 Stanje: Lokalno: $localCount | Cloud: $cloudCount"

                if (localCount == 0 && cloudCount > 0) {
                    Toast.makeText(this@CloudSyncActivity,
                        "Imate podatke u Cloud-u, možete ih restore-ovati",
                        Toast.LENGTH_SHORT).show()
                } else if (localCount > 0 && cloudCount == 0) {
                    Toast.makeText(this@CloudSyncActivity,
                        "Imate lokalne podatke, možete ih backup-ovati",
                        Toast.LENGTH_SHORT).show()
                } else if (localCount != cloudCount) {
                    Toast.makeText(this@CloudSyncActivity,
                        "Podaci nisu sinhronizovani. Preporučujemo dvosmernu sinhronizaciju.",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("CloudSync", "Greška pri proveri stanja: ${e.message}")
                tvStatus.text = "❌ Greška pri proveri stanja"
            }
        }
    }

    private suspend fun getCloudProductsSuspended(): List<Proizvod> {
        return try {
            firebaseHelper.restoreFromCloud()
        } catch (e: Exception) {
            Log.e("CloudSync", "Greška pri učitavanju cloud podataka: ${e.message}")
            emptyList()
        }
    }

    private fun backupToCloud() {
        Log.d("CloudSync", "=== POČINJE BACKUP ===")

        progressBar.visibility = ProgressBar.VISIBLE
        tvStatus.text = "📥 Učitavam lokalne podatke..."
        btnBackup.isEnabled = false

        repository.sviProizvodi { lokalniProizvodi ->
            Log.d("CloudSync", "Dobio ${lokalniProizvodi.size} lokalnih proizvoda")

            if (lokalniProizvodi.isEmpty()) {
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    btnBackup.isEnabled = true
                    tvStatus.text = "❌ Nema podataka za backup"
                    Toast.makeText(this,
                        "Nema podataka za čuvanje. Dodajte prvo neke proizvode.",
                        Toast.LENGTH_SHORT).show()
                }
                return@sviProizvodi
            }

            tvStatus.text = "⬆️ Šaljem u Cloud (${lokalniProizvodi.size} proizvoda)..."

            firebaseHelper.backupToCloudSimple(lokalniProizvodi) { success, message ->
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    btnBackup.isEnabled = true

                    if (success) {
                        tvStatus.text = "✅ Backup uspešan!"
                        Toast.makeText(this@CloudSyncActivity,
                            "✅ $message",
                            Toast.LENGTH_LONG).show()
                    } else {
                        tvStatus.text = "❌ Backup nije uspeo"
                        Toast.makeText(this@CloudSyncActivity,
                            "❌ $message",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun restoreFromCloud() {
        Log.d("CloudSync", "=== POČINJE SMART RESTORE ===")

        progressBar.visibility = ProgressBar.VISIBLE
        tvStatus.text = "📥 Učitavam iz Cloud-a..."
        btnRestore.isEnabled = false

        firebaseHelper.restoreFromCloudSimple { cloudProizvodi ->
            // Takođe učitaj kategorije iz Cloud-a
            restoreCategoriesFromCloud { cloudCategories ->
                runOnUiThread {
                    if (cloudProizvodi.isEmpty() && cloudCategories.isEmpty()) {
                        progressBar.visibility = ProgressBar.GONE
                        btnRestore.isEnabled = true
                        tvStatus.text = "ℹ️ Nema podataka u Cloud-u"
                        Toast.makeText(this@CloudSyncActivity,
                            "Nema podataka u Cloud-u za restore.",
                            Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    tvStatus.text = "🔄 Upoređujem sa lokalnim podacima..."

                    repository.sviProizvodi { lokalniProizvodi ->
                        // Učitaj lokalne kategorije
                        val lokalneKategorije = getLocalCategories()

                        coroutineScope.launch {
                            // 1. Prvo sinhronizuj kategorije
                            processCategoryRestore(lokalneKategorije, cloudCategories)

                            // 2. Onda sinhronizuj proizvode
                            processRestore(lokalniProizvodi, cloudProizvodi)
                        }
                    }
                }
            }
        }
    }

    private fun restoreCategoriesFromCloud(onComplete: (List<Kategorija>) -> Unit) {
        coroutineScope.launch {
            try {
                val currentUser = firebaseHelper.getCurrentUser()
                val userId = currentUser?.uid ?: return@launch onComplete(emptyList())

                val categories = withContext(Dispatchers.IO) {
                    val categoriesRef = firebaseHelper.getDatabaseReference()
                        .child("categories")
                        .child(userId)

                    try {
                        val snapshot = categoriesRef.get().await()
                        val kategorije = mutableListOf<Kategorija>()

                        snapshot.children.forEach { child ->
                            val emoji = child.child("emoji").getValue(String::class.java) ?: ""
                            val name = child.child("name").getValue(String::class.java) ?: ""
                            val color = child.child("color").getValue(Int::class.java) ?: android.R.color.holo_blue_light

                            if (name.isNotEmpty() && emoji.isNotEmpty()) {
                                kategorije.add(Kategorija(emoji, name, color))
                            }
                        }

                        kategorije
                    } catch (e: Exception) {
                        Log.e("CloudSync", "Greška pri učitavanju kategorija: ${e.message}")
                        emptyList()
                    }
                }

                withContext(Dispatchers.Main) {
                    onComplete(categories)
                    Log.d("CloudSync", "✅ Učitano ${categories.size} kategorija iz Cloud-a")
                }
            } catch (e: Exception) {
                Log.e("CloudSync", "Greška pri učitavanju kategorija: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete(emptyList())
                }
            }
        }
    }

    private suspend fun processCategoryRestore(
        lokalneKategorije: List<Kategorija>,
        cloudCategories: List<Kategorija>
    ) {
        try {
            // Dodaj kategorije koje postoje u Cloud-u, a ne postoje lokalno
            cloudCategories.forEach { cloudCategory ->
                val postoji = lokalneKategorije.any {
                    it.naziv == cloudCategory.naziv ||
                            it.ikona == cloudCategory.ikona
                }

                if (!postoji) {
                    // Dodaj u lokalnu bazu
                    addCategoryToLocalStorage(cloudCategory)
                }
            }

            Log.d("CloudSync", "✅ Kategorije sinhronizovane")
        } catch (e: Exception) {
            Log.e("CloudSync", "❌ Greška pri sinhronizaciji kategorija: ${e.message}")
        }
    }

    private fun addCategoryToLocalStorage(kategorija: Kategorija) {
        try {
            val currentUser = FirebaseHelper(this).getCurrentUser()
            val userId = currentUser?.uid ?: "offline_user"

            val sharedPref = getSharedPreferences("categories_$userId", Context.MODE_PRIVATE)
            val categoriesJson = sharedPref.getString("custom_categories", "[]")

            val type = object : TypeToken<MutableList<Kategorija>>() {}.type
            val categories = Gson().fromJson<MutableList<Kategorija>>(categoriesJson, type) ?: mutableListOf()

            categories.add(kategorija)

            val updatedJson = Gson().toJson(categories)
            sharedPref.edit().putString("custom_categories", updatedJson).apply()

            Log.d("CloudSync", "✅ Dodata kategorija: ${kategorija.naziv}")
        } catch (e: Exception) {
            Log.e("CloudSync", "❌ Greška pri dodavanju kategorije: ${e.message}")
        }
    }

    private suspend fun processRestore(lokalniProizvodi: List<Proizvod>, cloudProizvodi: List<Proizvod>) {
        try {
            Log.d("CloudSync", "=== PROCES RESTORE - POČINJE ===")

            // Ukloni duplikate iz cloud podataka
            val jedinstveniCloudMap = mutableMapOf<String, Proizvod>()
            cloudProizvodi.forEach { proizvod ->
                val key = proizvod.getUniqueKey()
                if (!jedinstveniCloudMap.containsKey(key)) {
                    jedinstveniCloudMap[key] = proizvod
                }
            }

            val jedinstveniCloudProizvodi = jedinstveniCloudMap.values.toList()

            // Proveri šta treba dodati/ažurirati
            val toAdd = mutableListOf<Proizvod>()
            val toUpdate = mutableListOf<Proizvod>()

            for (cloudProizvod in jedinstveniCloudProizvodi) {
                val cloudKey = cloudProizvod.getUniqueKey()
                val lokalni = lokalniProizvodi.find { it.getUniqueKey() == cloudKey }

                if (lokalni == null) {
                    toAdd.add(cloudProizvod)
                } else {
                    val trebaAzurirati = lokalni.naziv != cloudProizvod.naziv ||
                            lokalni.kategorija != cloudProizvod.kategorija ||
                            lokalni.kolicina != cloudProizvod.kolicina

                    if (trebaAzurirati) {
                        val azuriranProizvod = cloudProizvod.copy(id = lokalni.id)
                        toUpdate.add(azuriranProizvod)
                    }
                }
            }

            if (toAdd.isEmpty() && toUpdate.isEmpty()) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = ProgressBar.GONE
                    btnRestore.isEnabled = true
                    tvStatus.text = "✅ Podaci su već sinhronizovani"
                    Toast.makeText(this@CloudSyncActivity,
                        "✅ Podaci su već sinhronizovani. Nema promena.",
                        Toast.LENGTH_SHORT).show()
                }
                return
            }

            // Izvrši dodavanje i ažuriranje
            var uspesnoDodato = 0
            var uspesnoAzurirano = 0
            var greske = 0

            for (proizvod in toAdd) {
                try {
                    val deferred = CompletableDeferred<Boolean>()
                    repository.dodajProizvod(proizvod) { success, _ ->
                        deferred.complete(success)
                    }
                    val success = deferred.await()
                    if (success) {
                        uspesnoDodato++
                    } else {
                        greske++
                    }
                    delay(100)
                } catch (e: Exception) {
                    greske++
                }
            }

            for (proizvod in toUpdate) {
                try {
                    val deferred = CompletableDeferred<Boolean>()
                    repository.azurirajProizvod(proizvod) { success ->
                        deferred.complete(success)
                    }
                    val success = deferred.await()
                    if (success) {
                        uspesnoAzurirano++
                    } else {
                        greske++
                    }
                    delay(100)
                } catch (e: Exception) {
                    greske++
                }
            }

            delay(1000)

            withContext(Dispatchers.Main) {
                progressBar.visibility = ProgressBar.GONE
                btnRestore.isEnabled = true

                val poruka = StringBuilder()
                poruka.append("✅ Restore završen!\n")
                if (uspesnoDodato > 0) poruka.append("Dodato: $uspesnoDodato\n")
                if (uspesnoAzurirano > 0) poruka.append("Ažurirano: $uspesnoAzurirano\n")
                if (greske > 0) poruka.append("Grešaka: $greske\n")

                tvStatus.text = "✅ Restore završen"
                Toast.makeText(this@CloudSyncActivity,
                    poruka.toString(),
                    Toast.LENGTH_LONG).show()

                val intent = Intent("PODACI_OSVEŽENI")
                LocalBroadcastManager.getInstance(this@CloudSyncActivity).sendBroadcast(intent)

                Handler(Looper.getMainLooper()).postDelayed({
                    checkSyncStatus()
                }, 2000)
            }

        } catch (e: Exception) {
            Log.e("CloudSync", "❌ GREŠKA U PROCESU RESTORE: ${e.message}")
            withContext(Dispatchers.Main) {
                progressBar.visibility = ProgressBar.GONE
                btnRestore.isEnabled = true
                tvStatus.text = "❌ Greška pri restore-u"
                Toast.makeText(this@CloudSyncActivity,
                    "Greška: ${e.message}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    // ========== KLJUČNA METODA: DVOSMERNA KOMUNIKACIJA ==========
    // OVO JE KOJE KORISNIK KLIKNE KADA ŽELI DA SINHRONIZUJE
    private fun simpleTwoWaySync() {
        Log.d("CloudSync", "=== POČINJE DVOSMERNA SINHRONIZACIJA ===")

        progressBar.visibility = ProgressBar.VISIBLE
        tvStatus.text = "🔄 Dvosmerna sinhronizacija..."
        btnSyncBothWays.isEnabled = false

        coroutineScope.launch {
            try {
                // 1. Uzmi lokalne podatke
                tvStatus.text = "📥 Učitavam lokalne podatke..."
                val lokalniProizvodi = withContext(Dispatchers.IO) {
                    getLocalProductsSuspended()
                }

                // 2. Uzmi lokalne kategorije
                val lokalneKategorije = getLocalCategories()

                if (lokalniProizvodi.isEmpty() && lokalneKategorije.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = ProgressBar.GONE
                        btnSyncBothWays.isEnabled = true
                        tvStatus.text = "ℹ️ Nema lokalnih podataka"
                        Toast.makeText(this@CloudSyncActivity,
                            "Nema lokalnih podataka za sinhronizaciju.",
                            Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 3. Proveri da li je korisnik prijavljen
                val currentUser = firebaseHelper.getCurrentUser()
                if (currentUser == null) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = ProgressBar.GONE
                        btnSyncBothWays.isEnabled = true
                        tvStatus.text = "⚠️ Niste prijavljeni"
                        Toast.makeText(this@CloudSyncActivity,
                            "Niste prijavljeni. Ne možete sinhronizovati sa Cloud-om.",
                            Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 4. Obriši sve iz Cloud-a
                tvStatus.text = "🧹 Brišem stare podatke iz Cloud-a..."
                val userId = currentUser.uid

                try {
                    val userInventoryRef = firebaseHelper.getDatabaseReference()
                        .child("inventory")
                        .child(userId)
                    userInventoryRef.removeValue().await()

                    val userCategoriesRef = firebaseHelper.getDatabaseReference()
                        .child("categories")
                        .child(userId)
                    userCategoriesRef.removeValue().await()

                    Log.d("CloudSync", "✅ Stari cloud podaci obrisani")
                    delay(1000)
                } catch (e: Exception) {
                    Log.e("CloudSync", "⚠️ Nije uspelo brisanje starih podataka: ${e.message}")
                }

                // 5. Pošalji LOKALNE PROIZVODE u CLOUD
                tvStatus.text = "☁️ Šaljem ${lokalniProizvodi.size} proizvoda u Cloud..."
                val backupSuccess = try {
                    firebaseHelper.backupToCloud(lokalniProizvodi)
                } catch (e: Exception) {
                    Log.e("CloudSync", "❌ Greška pri backup-u: ${e.message}")
                    false
                }

                // 6. Pošalji LOKALNE KATEGORIJE u CLOUD (koristeći suspend funkciju)
                if (lokalneKategorije.isNotEmpty()) {
                    tvStatus.text = "☁️ Šaljem ${lokalneKategorije.size} kategorija u Cloud..."
                    val categoriesSuccess = withContext(Dispatchers.IO) {
                        backupCategoriesToCloud(lokalneKategorije)
                    }
                    Log.d("CloudSync", "Kategorije backup: ${if (categoriesSuccess) "✅" else "❌"}")
                }

                delay(3000)

                // 7. Proveri rezultat
                withContext(Dispatchers.Main) {
                    progressBar.visibility = ProgressBar.GONE
                    btnSyncBothWays.isEnabled = true

                    if (backupSuccess) {
                        tvStatus.text = "✅ Sinhronizacija uspešna!"
                        Toast.makeText(this@CloudSyncActivity,
                            "✅ Sinhronizovano ${lokalniProizvodi.size} proizvoda u Cloud" +
                                    if (lokalneKategorije.isNotEmpty()) " i ${lokalneKategorije.size} kategorija" else "",
                            Toast.LENGTH_LONG).show()

                        val intent = Intent("PODACI_OSVEŽENI")
                        LocalBroadcastManager.getInstance(this@CloudSyncActivity).sendBroadcast(intent)

                        Handler(Looper.getMainLooper()).postDelayed({
                            checkSyncStatus()
                        }, 2000)
                    } else {
                        tvStatus.text = "❌ Greška pri sinhronizaciji"
                        Toast.makeText(this@CloudSyncActivity,
                            "Greška pri slanju podataka u Cloud",
                            Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("CloudSync", "❌ Greška u simpleTwoWaySync: ${e.message}")
                withContext(Dispatchers.Main) {
                    progressBar.visibility = ProgressBar.GONE
                    btnSyncBothWays.isEnabled = true
                    tvStatus.text = "❌ Greška pri sinhronizaciji"
                    Toast.makeText(this@CloudSyncActivity,
                        "Greška: ${e.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private suspend fun backupCategoriesToCloud(categories: List<Kategorija>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val currentUser = firebaseHelper.getCurrentUser()
                val userId = currentUser?.uid ?: return@withContext false

                val categoriesRef = firebaseHelper.getDatabaseReference()
                    .child("categories")
                    .child(userId)

                // Konvertuj kategorije u mapu
                val categoriesMap = mutableMapOf<String, Any>()
                categories.forEachIndexed { index, kategorija ->
                    categoriesMap["category_$index"] = mapOf(
                        "emoji" to kategorija.ikona,
                        "name" to kategorija.naziv,
                        "color" to kategorija.boja
                    )
                }

                categoriesRef.setValue(categoriesMap).await()
                Log.d("CloudSync", "✅ Kategorije sačuvane u Cloud: ${categories.size}")
                true
            } catch (e: Exception) {
                Log.e("CloudSync", "❌ Greška pri čuvanju kategorija: ${e.message}")
                false
            }
        }
    }

    // Dodajte ovu metodu da dobijete lokalne kategorije
    private fun getLocalCategories(): List<Kategorija> {
        return try {
            val currentUser = FirebaseHelper(this).getCurrentUser()
            val userId = currentUser?.uid ?: "offline_user"

            val sharedPref = getSharedPreferences("categories_$userId", Context.MODE_PRIVATE)
            val categoriesJson = sharedPref.getString("custom_categories", "[]")

            val type = object : TypeToken<MutableList<Kategorija>>() {}.type
            Gson().fromJson<MutableList<Kategorija>>(categoriesJson, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("CloudSync", "Greška pri učitavanju lokalnih kategorija: ${e.message}")
            emptyList()
        }
    }

    private suspend fun getLocalProductsSuspended(): List<Proizvod> {
        return try {
            repository.getProizvodiSuspended()
        } catch (e: Exception) {
            Log.e("CloudSync", "Greška pri učitavanju lokalnih proizvoda: ${e.message}")
            emptyList()
        }
    }
}