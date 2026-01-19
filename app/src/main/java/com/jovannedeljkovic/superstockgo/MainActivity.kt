package com.jovannedeljkovic.superstockgo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.MenuItem

class MainActivity : AppCompatActivity() {

    private lateinit var repository: Repository
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProizvodAdapter
    private var trenutniFilter: String = "ALL"
    private var trenutniSort: String = "name_asc"

    // Broadcast receiver-i - samo LocalBroadcastManager
    private lateinit var dataChangeReceiver: BroadcastReceiver
    private lateinit var dataSyncReceiver: BroadcastReceiver
    private lateinit var categoryChangeReceiver: BroadcastReceiver

    // Firebase helper
    private lateinit var firebaseHelper: FirebaseHelper

    companion object {
        private const val REQUEST_ADD_PRODUCT = 1001
        private const val REQUEST_EDIT_PRODUCT = 1002
        private const val SMS_PERMISSION_CODE = 100
        private const val NOTIFICATION_PERMISSION_CODE = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ========== TOOLBAR SETUP ==========
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Postavi naslove
        trenutniFilter = intent.getStringExtra("FILTER") ?: "ALL"
        val naslov = when (trenutniFilter) {
            "ALL" -> "📜 Sve stavke"
            "LOW_STOCK" -> "⚠️ Niska zaliha"
            else -> {
                val filterBezEmoji = extractNameWithoutEmoji(trenutniFilter)
                "📋 $filterBezEmoji"
            }
        }

        supportActionBar?.title = naslov
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Postavi subtitle
        repository = Repository(this)
        firebaseHelper = FirebaseHelper(this)
        val currentUser = firebaseHelper.getCurrentUser()
        if (currentUser != null) {
            supportActionBar?.subtitle = "${getEmoji("cloud")} ${currentUser.email}"
        } else {
            supportActionBar?.subtitle = "✈\uFE0F Offline mod"
        }

        // ========== INICIJALIZACIJA ==========
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        // Inicijalizuj adapter
        adapter = ProizvodAdapter(
            proizvodi = emptyList(),
            onEditClick = { proizvod ->
                val intent = Intent(this, DodajIzmeniActivity::class.java)
                intent.putExtra("PROIZVOD_ID", proizvod.id)
                startActivityForResult(intent, REQUEST_EDIT_PRODUCT)
            },
            onDeleteClick = { proizvod ->
                showDeleteConfirmationDialog(proizvod)
            },
            onPlusClick = { proizvod ->
                handlePlusClick(proizvod)
            },
            onMinusClick = { proizvod ->
                handleMinusClick(proizvod)
            }
        )

        recyclerView.adapter = adapter

        // Dugme za dodavanje novog proizvoda
        val fabAdd: FloatingActionButton = findViewById(R.id.fabAdd)
        fabAdd.setOnClickListener {
            val intent = Intent(this, DodajIzmeniActivity::class.java)
            if (trenutniFilter != "ALL" && trenutniFilter != "LOW_STOCK") {
                intent.putExtra("KATEGORIJA", trenutniFilter)
            }
            startActivityForResult(intent, REQUEST_ADD_PRODUCT)
        }

        // Inicijalizuj BroadcastReceiver-e za LOCAL broadcast
        initLocalBroadcastReceivers()

        // Registruj Local BroadcastReceiver-e
        registerLocalReceivers()

        // Učitaj podatke
        osveziPodatke()

        // Proveri dozvole
        checkSmsPermission()
        checkNotificationPermission()

        Log.d("MainActivity", "? MainActivity onCreate završen")
    }

    private fun getEmoji(type: String): String {
        return when(type) {
            "cloud" -> "☁️"  // Kopirajte emoji direktno ovde
            "offline" -> "📴" // Kopirajte emoji direktno ovde
            else -> "📱"
        }
    }
    private fun initLocalBroadcastReceivers() {
        dataChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("MainActivity", "\uD83D\uDCE1 LOCAL Broadcast primljen - PROIZVOD promenjen")
                osveziPodatke()
            }
        }

        dataSyncReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("MainActivity", "\uD83D\uDCE1 LOCAL Broadcast primljen - PODACI osveženi")
                osveziPodatke()
                Toast.makeText(this@MainActivity,
                    "Podaci su sinhronizovani sa Cloud-om",
                    Toast.LENGTH_SHORT).show()
            }
        }

        categoryChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("MainActivity", "📡 LOCAL Broadcast primljen - KATEGORIJA promenjena/vraćena")
                Log.d("MainActivity", "Action: ${intent?.action}")
                Log.d("MainActivity", "Extra ORIGINAL_KATEGORIJA: ${intent?.getStringExtra("ORIGINAL_KATEGORIJA")}")
                Log.d("MainActivity", "Extra STARA_KATEGORIJA: ${intent?.getStringExtra("STARA_KATEGORIJA")}")
                Log.d("MainActivity", "Extra NOVA_KATEGORIJA: ${intent?.getStringExtra("NOVA_KATEGORIJA")}")

                osveziPodatke()
            }
        }
    }

    private fun registerLocalReceivers() {
        val localBroadcastManager = LocalBroadcastManager.getInstance(this)

        try {
            // Registruj sve receiver-e preko LocalBroadcastManager
            localBroadcastManager.registerReceiver(
                dataChangeReceiver,
                IntentFilter("PROIZVOD_DODAT")
            )

            localBroadcastManager.registerReceiver(
                dataSyncReceiver,
                IntentFilter("PODACI_OSVEŽENI")
            )

            localBroadcastManager.registerReceiver(
                categoryChangeReceiver,
                IntentFilter("KATEGORIJA_PROMENJENA")
            )

            localBroadcastManager.registerReceiver(
                categoryChangeReceiver,  // VAŽNO: Ovo je ista metoda
                IntentFilter("KATEGORIJA_VRAĆENA")
            )

            Log.d("MainActivity", "✅ LOCAL Broadcast receiveri registrovani")

        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Greška pri registraciji LOCAL receivera: ${e.message}")
        }
    }



    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        if (firebaseHelper.getCurrentUser() != null) {
            // KOPIRAJTE EMOJI: 📡
            menu.add(0, 998, 1, "📡 Cloud Sync")
        }

        // KOPIRAJTE EMOJI: 🧹
        menu.add(0, 999, 2, "🧹 Očisti duplikate")

        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_sort -> {
                showSortPopupMenu()
                true
            }
            998 -> {
                startActivity(Intent(this, CloudSyncActivity::class.java))
                true
            }
            999 -> {
                showCleanDuplicatesDialog()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == REQUEST_ADD_PRODUCT || requestCode == REQUEST_EDIT_PRODUCT)
            && resultCode == RESULT_OK) {
            Handler(Looper.getMainLooper()).postDelayed({
                osveziPodatke()
            }, 300)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Deregistruj sve LOCAL receiver-e
            val localBroadcastManager = LocalBroadcastManager.getInstance(this)
            localBroadcastManager.unregisterReceiver(dataChangeReceiver)
            localBroadcastManager.unregisterReceiver(dataSyncReceiver)
            localBroadcastManager.unregisterReceiver(categoryChangeReceiver)

            Log.d("MainActivity", "✅ LOCAL Broadcast receiveri deregistrovani")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Greška pri deregistraciji LOCAL receivera: ${e.message}")
        }
    }

    private fun showSortPopupMenu() {
        val items = arrayOf(
            "Naziv (A -> Z)",
            "Naziv (Z -> A)",
            "Količina (manje -> više)",
            "Količina (više -> manje)",
            "Po kategoriji"
        )

        AlertDialog.Builder(this)
            .setTitle("Sortiraj po")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> { trenutniSort = "name_asc" }
                    1 -> { trenutniSort = "name_desc" }
                    2 -> { trenutniSort = "quantity_asc" }
                    3 -> { trenutniSort = "quantity_desc" }
                    4 -> { trenutniSort = "category" }
                }
                osveziPodatke()
                Toast.makeText(this, "Sortirano: ${items[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun showCleanDuplicatesDialog() {
        AlertDialog.Builder(this)
            .setTitle("\uD83E\uDDF9 Čišćenje duplikata")
            .setMessage("Da li želite da skenirate bazu i obrišete sve duplirane proizvode?")
            .setPositiveButton("Skeniraj i očisti") { dialog, _ ->
                dialog.dismiss()
                cleanDuplicates()
            }
            .setNegativeButton("Otkaži") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "🔄 onResume - osvežavam podatke")
        osveziPodatke()
    }

    /**
     * Forsirano osvežavanje svih aktivnosti
     */
    private fun forceRefreshAllActivities() {
        // Pošalji sve relevantne broadcast-ove
        val intent1 = Intent("PODACI_OSVEŽENI")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent1)

        val intent2 = Intent("PROIZVOD_DODAT")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent2)

        val intent3 = Intent("KATEGORIJA_PROMENJENA")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent3)

        Log.d("KategorijeActivity", "✅ Svi broadcast-ovi poslati za osvežavanje")
    }
    private fun osveziPodatke() {
        repository.sviProizvodi { proizvodi ->
            val filtrirani = filtriraniProizvodi(proizvodi)
            val sortirani = sortirajProizvode(filtrirani)

            runOnUiThread {
                adapter.updateData(sortirani)
                if (sortirani.isEmpty()) {
                    showEmptyStateMessage()
                }
            }
        }
    }

    private fun showEmptyStateMessage() {
        val message = when (trenutniFilter) {
            "ALL" -> "Nema proizvoda. Dodajte prvi proizvod!"
            "LOW_STOCK" -> "Nema proizvoda sa niskom zalihom. Odlično! ?"
            else -> "Nema proizvoda u ovoj kategoriji."
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun filtriraniProizvodi(proizvodi: List<Proizvod>): List<Proizvod> {
        return when {
            trenutniFilter == "ALL" -> proizvodi
            trenutniFilter == "LOW_STOCK" -> proizvodi.filter { it.kolicina <= 5 }
            else -> {
                val filterBezEmoji = extractNameWithoutEmoji(trenutniFilter)
                proizvodi.filter { proizvod ->
                    val proizvodBezEmoji = extractNameWithoutEmoji(proizvod.kategorija)
                    proizvodBezEmoji == filterBezEmoji ||
                            proizvod.kategorija.contains(trenutniFilter) ||
                            proizvodBezEmoji.contains(filterBezEmoji)
                }
            }
        }
    }

    private fun sortirajProizvode(proizvodi: List<Proizvod>): List<Proizvod> {
        return when (trenutniSort) {
            "name_asc" -> proizvodi.sortedBy { it.naziv }
            "name_desc" -> proizvodi.sortedByDescending { it.naziv }
            "quantity_asc" -> proizvodi.sortedBy { it.kolicina }
            "quantity_desc" -> proizvodi.sortedByDescending { it.kolicina }
            "category" -> proizvodi.sortedBy { it.kategorija }
            else -> proizvodi.sortedBy { it.naziv }
        }
    }

    private fun extractNameWithoutEmoji(text: String): String {
        return text.replace(Regex("^[\\p{So}\\s]+"), "").trim()
    }

    private fun showDeleteConfirmationDialog(proizvod: Proizvod) {
        AlertDialog.Builder(this)
            .setTitle("Brisanje proizvoda")
            .setMessage("Da li ste sigurni da želite da obrišete '${proizvod.naziv}'?\n\n" +
                    "\uD83D\uDDD1\uFE0F PROIZVOD ĆE BITI OBRISAN SAMO LOKALNO.\n" +
                    "Za brisanje iz Cloud-a koristite Cloud Sync opciju.")
            .setPositiveButton("Obriši lokalno") { _, _ ->
                repository.obrisiProizvod(proizvod) { success ->
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this,
                                "\uD83D\uDDD1\uFE0F Proizvod '${proizvod.naziv}' je obrisan LOKALNO",
                                Toast.LENGTH_SHORT).show()
                            osveziPodatke()

                            // Pošalji LOCAL broadcast
                            val intent = Intent("PROIZVOD_DODAT")
                            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                        } else {
                            Toast.makeText(this, "⚠\uFE0F Greška pri brisanju", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun handlePlusClick(proizvod: Proizvod) {
        Log.d("MainActivity", "[PLUS] Klik za: ${proizvod.naziv}")

        val novaKolicina = proizvod.kolicina + 1
        val azuriranProizvod = proizvod.copy(kolicina = novaKolicina)

        repository.azurirajProizvod(azuriranProizvod) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this@MainActivity,
                        "\uD83D\uDD3C Količina za '${proizvod.naziv}' povećana na $novaKolicina",
                        Toast.LENGTH_SHORT).show()
                    osveziPodatke()

                    // Pošalji LOCAL broadcast
                    val intent = Intent("PROIZVOD_DODAT")
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                } else {
                    Toast.makeText(this@MainActivity,
                        "\uD83C\uDFE0 Sačuvano samo lokalno",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleMinusClick(proizvod: Proizvod) {
        Log.d("MainActivity", "[MINUS] Klik za: ${proizvod.naziv}")

        if (proizvod.kolicina > 0) {
            val novaKolicina = proizvod.kolicina - 1
            val azuriranProizvod = proizvod.copy(kolicina = novaKolicina)

            repository.azurirajProizvod(azuriranProizvod) { success ->
                runOnUiThread {
                    if (success) {
                        if (novaKolicina == 0) {
                            Toast.makeText(this@MainActivity,
                                "${EmojiHelper.forToast("warning")} Količina za '${proizvod.naziv}' je sada 0!",
                                Toast.LENGTH_LONG).show()
                            posaljiSMSObavestenje(proizvod.naziv, novaKolicina, proizvod.kategorija)
                            pokusajLokalnuNotifikaciju(proizvod)
                        } else {
                            Toast.makeText(this@MainActivity,
                                "\uD83D\uDD3D Količina za '${proizvod.naziv}' smanjena na $novaKolicina",
                                Toast.LENGTH_SHORT).show()
                        }
                        osveziPodatke()

                        // Pošalji LOCAL broadcast
                        val intent = Intent("PROIZVOD_DODAT")
                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                    } else {
                        Toast.makeText(this@MainActivity,
                            "\uD83C\uDFE0 Sačuvano samo lokalno",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(this, "Količina je već 0", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanDuplicates() {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("⏳ Skeniranje duplikata")
            setMessage("Proveravam bazu podataka...")
            setCancelable(false)
            show()
        }

        repository.proveriIIspraviDuplikate { brojDuplikata ->
            runOnUiThread {
                progressDialog.dismiss()
                if (brojDuplikata > 0) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("\uD83E\uDDF9✨ Čišćenje završeno")
                        .setMessage("Obrisano je $brojDuplikata dupliranih proizvoda.")
                        .setPositiveButton("OK") { dialog, _ ->
                            dialog.dismiss()
                            osveziPodatke()

                            // Pošalji LOCAL broadcast
                            val intent = Intent("PODACI_OSVEŽENI")
                            LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(intent)
                        }
                        .show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "\uD83C\uDFC6 Nema dupliranih proizvoda u bazi podataka",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                SMS_PERMISSION_CODE
            )
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
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
            SMS_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "✉\uFE0F✅ SMS dozvola odobrena", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "❌ SMS dozvola odbijena", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun posaljiSMSObavestenje(nazivProizvoda: String, kolicina: Int, kategorija: String) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val SMS_PHONE_NUMBER = "+381646361287"
                val vreme = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())

                // Ekstraktuj emoji iz kategorije
                val emojiRegex = Regex("([\\p{So}])")
                val emojiMatch = emojiRegex.find(kategorija)
                val emoji = emojiMatch?.value ?: "⏳"

                // Čisti naziv bez emoji
                val cleanCategory = extractNameWithoutEmoji(kategorija)

                val poruka = """
            🚨 SuperstockGO UPOZORENJE!
            Proizvod: $nazivProizvoda
            Kategorija: $emoji $cleanCategory
            Količina: $kolicina
            Vreme: $vreme
            
            HITNO: Proizvod je ponestao!
            """.trimIndent()

                val smsManager = SmsManager.getDefault()
                val parts = smsManager.divideMessage(poruka)

                smsManager.sendMultipartTextMessage(
                    SMS_PHONE_NUMBER,
                    null,
                    parts,
                    null,
                    null
                )

                Log.d("SMS", "SMS uspešno poslat!")
                Toast.makeText(this, "\uD83D\uDCE8 SMS obaveštenje poslato", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Log.e("SMS", "Greška pri slanju SMS: ${e.message}")
            }
        } else {
            Toast.makeText(this, "Potrebna dozvola za SMS", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pokusajLokalnuNotifikaciju(proizvod: Proizvod) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "low_stock_channel",
                    "Niska zaliha",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Obaveštenja o niskoj zalihi proizvoda"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                }

                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("FILTER", "LOW_STOCK")
            }

            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            val notification = NotificationCompat.Builder(this, "low_stock_channel")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("\uD83D\uDEA8 SuperstockGO Upozorenje!")
                .setContentText("Proizvod '${proizvod.naziv}' je ponestao!")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Proizvod: ${proizvod.naziv}\nKategorija: ${extractNameWithoutEmoji(proizvod.kategorija)}\nKoličina: ${proizvod.kolicina}\n\nHITNO: Dodajte novu zalihu!"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .build()

            with(NotificationManagerCompat.from(this)) {
                if (areNotificationsEnabled()) {
                    notify(System.currentTimeMillis().toInt(), notification)
                    Log.d("MainActivity", "\uD83D\uDCE8 Notifikacija poslata za: ${proizvod.naziv}")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Greška pri slanju notifikacije: ${e.message}")
        }
    }
}