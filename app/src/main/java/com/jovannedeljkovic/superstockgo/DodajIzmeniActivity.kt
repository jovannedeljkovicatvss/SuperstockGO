package com.jovannedeljkovic.superstockgo

import android.app.ProgressDialog
import androidx.appcompat.app.AlertDialog
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.text.SimpleDateFormat
import java.util.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.app.Activity


class DodajIzmeniActivity : AppCompatActivity() {

    private lateinit var etNaziv: TextInputEditText
    private lateinit var autoCompleteKategorija: MaterialAutoCompleteTextView
    private lateinit var etKolicina: TextInputEditText
    private lateinit var btnSacuvaj: Button

    // KORISTI REPOSITORY UMESTO DBHelper
    private lateinit var repository: Repository
    private var proizvodId: Int = 0
    private var kategorijaIzMain: String = ""

    companion object {
        private const val SMS_PERMISSION_CODE = 100
        private const val NOTIFICATION_CHANNEL_ID = "low_stock_channel"
        private const val SMS_PHONE_NUMBER = "+381646361287"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS dozvola odobrena", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "SMS dozvola odbijena", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dodaj_izmeni)

        // INICIJALIZUJ REPOSITORY UMESTO DBHelper
        repository = Repository(this)

        etNaziv = findViewById(R.id.etNaziv)
        autoCompleteKategorija = findViewById(R.id.autoCompleteKategorija)
        etKolicina = findViewById(R.id.etKolicina)
        btnSacuvaj = findViewById(R.id.btnSacuvaj)

        val kategorijeAdapter = ArrayAdapter(this,
            android.R.layout.simple_dropdown_item_1line, kategorije)
        autoCompleteKategorija.setAdapter(kategorijeAdapter)

        proizvodId = intent.getIntExtra("PROIZVOD_ID", 0)
        kategorijaIzMain = intent.getStringExtra("KATEGORIJA") ?: ""

        if (proizvodId > 0) {
            ucitajProizvod(proizvodId)
        } else if (kategorijaIzMain.isNotEmpty()) {
            autoCompleteKategorija.setText(kategorijaIzMain)
            autoCompleteKategorija.isEnabled = false
        }

        btnSacuvaj.setOnClickListener {
            sacuvajProizvod()
        }

        proveriSMSDozvolu()
    }

    private fun proveriSMSDozvolu() {
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

    // U DodajIzmeniActivity.kt, POPRAVI metodu ucitajProizvod:

    // U DodajIzmeniActivity.kt, zamenite ucitajProizvod metodu:
    private fun ucitajProizvod(id: Int) {
        repository.pronadjiProizvod(id) { proizvod ->
            runOnUiThread {
                if (proizvod != null) {
                    etNaziv.setText(proizvod.naziv)
                    autoCompleteKategorija.setText(proizvod.kategorija)
                    etKolicina.setText(proizvod.kolicina.toString())
                } else {
                    Toast.makeText(this, "Proizvod nije prona en", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun sacuvajProizvod() {
        val naziv = etNaziv.text.toString().trim()
        val kategorija = if (!autoCompleteKategorija.isEnabled) {
            kategorijaIzMain
        } else {
            autoCompleteKategorija.text.toString().trim()
        }
        val kolicinaStr = etKolicina.text.toString().trim()

        // VALIDACIJA
        if (naziv.isEmpty()) {
            etNaziv.error = "Unesite naziv proizvoda"
            etNaziv.requestFocus()
            return
        }

        if (kategorija.isEmpty()) {
            autoCompleteKategorija.error = "Izaberite kategoriju"
            autoCompleteKategorija.requestFocus()
            return
        }

        if (kolicinaStr.isEmpty()) {
            etKolicina.error = "Unesite količinu"
            etKolicina.requestFocus()
            return
        }

        val kolicina = try {
            kolicinaStr.toInt()
        } catch (e: NumberFormatException) {
            etKolicina.error = "Količina mora biti broj"
            etKolicina.requestFocus()
            return
        }

        if (kolicina < 0) {
            etKolicina.error = "Količina ne može biti negativna"
            etKolicina.requestFocus()
            return
        }

        // KREIRAJ PROIZVOD
        val proizvod = Proizvod(
            id = if (proizvodId > 0) proizvodId else 0,
            naziv = naziv,
            kategorija = kategorija,
            kolicina = kolicina
        )

        Log.d("DodajIzmeni", "💾 Čuvanje proizvoda: $naziv, Kat: $kategorija, Kol: $kolicina, ID: ${proizvod.id}")

        // KREIRAJ I PRIKAŽI PROGRESS DIALOG
        val progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Čuvanje...")
        progressDialog.setMessage("Sačuvavam proizvod '$naziv'")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // ONEMOGUĆI DUGME DA SE NE KLIKNE VIŠE PUTA
        btnSacuvaj.isEnabled = false

        if (proizvodId > 0) {
            // AŽURIRANJE POSTOJEĆEG PROIZVODA
            Log.d("DodajIzmeni", "🔄 Ažuriranje postojećeg proizvoda ID=$proizvodId")

            repository.azurirajProizvod(proizvod) { success ->
                progressDialog.dismiss()
                runOnUiThread {
                    btnSacuvaj.isEnabled = true

                    if (success) {
                        Log.d("DodajIzmeni", "✅ Proizvod '$naziv' ažuriran")
                        Toast.makeText(this, "✅ Proizvod '$naziv' ažuriran", Toast.LENGTH_SHORT).show()

                        // SMS i notifikacija ako je količina 0
                        if (kolicina <= 0) {
                            Log.d("DodajIzmeni", "⚠️ Količina 0 - šaljem SMS")
                            posaljiSMSObavestenje(naziv, kategorija, kolicina)
                            pokusajLokalnuNotifikaciju(naziv, kategorija, kolicina)
                        }

                        // POSTAVI REZULTAT ZA MAIN ACTIVITY
                        setResult(Activity.RESULT_OK, Intent().apply {
                            putExtra("ACTION", "UPDATE")
                            putExtra("PROIZVOD_NAZIV", naziv)
                        })

                        // POŠALJI LOCAL BROADCAST ZA OSVEŽAVANJE
                        val broadcastIntent = Intent("PROIZVOD_DODAT")
                        LocalBroadcastManager.getInstance(this@DodajIzmeniActivity).sendBroadcast(broadcastIntent)

                        // Takođe pošalji za podatke osvežene
                        val syncIntent = Intent("PODACI_OSVEŽENI")
                        LocalBroadcastManager.getInstance(this@DodajIzmeniActivity).sendBroadcast(syncIntent)

                        finish()

                    } else {
                        Log.e("DodajIzmeni", "❌ Greška pri ažuriranju '$naziv'")
                        Toast.makeText(this, "❌ Greška pri ažuriranju", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            // DODAVANJE NOVOG PROIZVODA
            Log.d("DodajIzmeni", "➕ Dodavanje novog proizvoda")

            repository.dodajProizvod(proizvod) { success, message ->
                progressDialog.dismiss()
                runOnUiThread {
                    btnSacuvaj.isEnabled = true

                    if (success) {
                        Log.d("DodajIzmeni", "✅ Proizvod '$naziv' $message")
                        Toast.makeText(this, "✅ Proizvod '$naziv' $message", Toast.LENGTH_SHORT).show()

                        // SMS i notifikacija ako je količina 0
                        if (kolicina <= 0) {
                            Log.d("DodajIzmeni", "⚠️ Količina 0 - šaljem SMS")
                            posaljiSMSObavestenje(naziv, kategorija, kolicina)
                            pokusajLokalnuNotifikaciju(naziv, kategorija, kolicina)
                        }

                        // POSTAVI REZULTAT ZA MAIN ACTIVITY
                        setResult(Activity.RESULT_OK, Intent().apply {
                            putExtra("ACTION", "ADD")
                            putExtra("PROIZVOD_NAZIV", naziv)
                            putExtra("PROIZVOD_KATEGORIJA", kategorija)
                        })

                        // POŠALJI LOCAL BROADCAST ZA OSVEŽAVANJE
                        val broadcastIntent = Intent("PROIZVOD_DODAT")
                        LocalBroadcastManager.getInstance(this@DodajIzmeniActivity).sendBroadcast(broadcastIntent)

                        // Takođe pošalji za podatke osvežene
                        val syncIntent = Intent("PODACI_OSVEŽENI")
                        LocalBroadcastManager.getInstance(this@DodajIzmeniActivity).sendBroadcast(syncIntent)

                        finish()

                    } else {
                        Log.e("DodajIzmeni", "❌ Greška: $message")
                        Toast.makeText(this, "❌ Greška: $message", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    private fun posaljiSMSObavestenje(nazivProizvoda: String, kategorija: String, kolicina: Int) {
        Log.d("SMS", "Pokrećem slanje SMS-a za: $nazivProizvoda")

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("SMS", "Ima dozvolu za SMS")
            posaljiSMS(nazivProizvoda, kategorija, kolicina)
        } else {
            Log.d("SMS", "Nema dozvolu za SMS")
            Toast.makeText(this,
                "Nema dozvole za slanje SMS.",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun posaljiSMS(nazivProizvoda: String, kategorija: String, kolicina: Int) {
        try {
            Log.d("SMS", "Pokušavam slanje SMS na broj: $SMS_PHONE_NUMBER")

            if (SMS_PHONE_NUMBER.isBlank() || SMS_PHONE_NUMBER == "+381000000000") {
                Toast.makeText(this,
                    "Molimo podesite validan telefon broj u kodu aplikacije",
                    Toast.LENGTH_LONG).show()
                return
            }

            val vreme = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
            val poruka = """
                🔔 SuperstockGO UPOZORENJE!
                Proizvod: $nazivProizvoda
                Kategorija: $kategorija
                Količina: $kolicina
                Vreme: $vreme
                
                HITNO: Proizvod je ponestao!
            """.trimIndent()

            Log.d("SMS", "Poruka: $poruka")

            val smsManager = SmsManager.getDefault()

            if (!android.util.Patterns.PHONE.matcher(SMS_PHONE_NUMBER).matches()) {
                Toast.makeText(this, "Nevalidan telefon broj: $SMS_PHONE_NUMBER", Toast.LENGTH_LONG).show()
                return
            }

            val parts = smsManager.divideMessage(poruka)

            smsManager.sendMultipartTextMessage(
                SMS_PHONE_NUMBER,
                null,
                parts,
                null,
                null
            )

            Log.d("SMS", "SMS uspešno poslat!")
            Toast.makeText(this, "SMS obaveštenje poslato na $SMS_PHONE_NUMBER", Toast.LENGTH_LONG).show()

        } catch (e: SecurityException) {
            Log.e("SMS", "SecurityException: ${e.message}")
            Toast.makeText(this,
                "Greška: Nema dozvole za slanje SMS.",
                Toast.LENGTH_LONG).show()
        } catch (e: IllegalArgumentException) {
            Log.e("SMS", "IllegalArgumentException: ${e.message}")
            Toast.makeText(this, "Nevalidan telefon broj", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("SMS", "Greška pri slanju SMS: ${e.message}")
            Toast.makeText(this, "Greška pri slanju SMS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pokusajLokalnuNotifikaciju(nazivProizvoda: String, kategorija: String, kolicina: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Niska zaliha",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Obaveštenja o niskoj zaliži proizvoda"
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

            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("⚠️ Niska zaliha!")
                .setContentText("Proizvod '$nazivProizvoda' je ponestao!")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Proizvod: $nazivProizvoda\nKategorija: $kategorija\nTrenutna količina: $kolicina\n\nHITNO: Dodajte novu zalihu!"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .build()

            with(NotificationManagerCompat.from(this)) {
                if (areNotificationsEnabled()) {
                    notify(System.currentTimeMillis().toInt(), notification)
                    Log.d("DodajIzmeniActivity", "Lokalna notifikacija poslata za: $nazivProizvoda")
                }
            }

        } catch (e: Exception) {
            Log.e("DodajIzmeniActivity", "Greška pri slanju notifikacije: ${e.message}")
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        try {
            //repository.zatvoriBazu()
            Log.d("DodajIzmeniActivity", "Repository baza zatvorena")
        } catch (e: Exception) {
            Log.e("DodajIzmeniActivity", "Greška pri zatvaranju baze: ${e.message}")
        }
    }

    private val kategorije = listOf(
        "🍎 Hrana",
        "🍹 Piće",
        "📎 Kancelarijski materijal",
        "👕 Odeća i obuća",
        "🧹 Sredstva za čišćenje",
        "📱 Elektronika"
    )
}