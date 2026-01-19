package com.jovannedeljkovic.superstockgo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.app.ProgressDialog
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import kotlinx.coroutines.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager


class KategorijeActivity : AppCompatActivity() {

    private val customCategories = mutableListOf<Kategorija>()
    private lateinit var adapter: KategorijaAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var repository: Repository
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        // Pomoćna metoda za ekstrakciju čistog naziva iz kategorije (bez emoji)
        fun extractCategoryName(fullCategory: String): String {
            // Ukloni emoji i dodatni whitespace
            return fullCategory.replace(Regex("^[\\p{So}\\s]+"), "").trim()
        }

        // Pomoćna metoda za kreiranje pune kategorije (sa emoji)
        fun createFullCategory(emoji: String, name: String): String {
            return "$emoji $name"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kategorije)

        // ========== TOOLBAR SETUP ==========
        val toolbar: Toolbar = findViewById(R.id.toolbar)

        // 1. Postavi toolbar
        setSupportActionBar(toolbar)

        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_500))
        toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white))
        toolbar.setSubtitleTextColor(ContextCompat.getColor(this, android.R.color.white))
        // 2. Postavi naslove
        supportActionBar?.title = "SuperstockGO"

        // 3. Postavi subtitle sa email-om korisnika
        repository = Repository(this)
        val firebaseHelper = FirebaseHelper(this)
        val currentUser = firebaseHelper.getCurrentUser()
        if (currentUser != null) {
            supportActionBar?.subtitle = "☁️ ${currentUser.email}"
        } else {
            supportActionBar?.subtitle = "📱 Offline mod"
        }

        // 4. FORSIRAJ beli tekst za naslove
        toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white))
        toolbar.setSubtitleTextColor(ContextCompat.getColor(this, android.R.color.white))

        // 5. Postavi overflow ikonu na belo
        try {
            val overflowIcon = toolbar.overflowIcon
            if (overflowIcon != null) {
                val whiteOverflowIcon = overflowIcon.mutate()
                whiteOverflowIcon.setTint(ContextCompat.getColor(this, android.R.color.white))
                toolbar.overflowIcon = whiteOverflowIcon
            }
        } catch (e: Exception) {
            Log.e("KategorijeActivity", "Greška pri postavljanju overflow ikone: ${e.message}")
        }

        val addButton = Button(this).apply {
            text = "+ Dodaj kategoriju"
            setTextColor(ContextCompat.getColor(this@KategorijeActivity, android.R.color.white))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                showAddCategoryDialog()
            }
        }
        toolbar.addView(addButton)
        // Ovo je KLJUČNO za crni tekst u popup meniju
        toolbar.popupTheme = R.style.Theme_SuperstockGO_LightPopup

        // ========== INICIJALIZACIJA ==========
        // Inicijalizacija RecyclerView
        recyclerView = findViewById(R.id.rvKategorije)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        // Inicijalizacija Repository (ako već niste)
        if (!::repository.isInitialized) {
            repository = Repository(this)
        }

        // Migracija starih podataka
        repository.migrirajPodatkeZaTrenutnogKorisnika()

        // Učitaj custom kategorije iz svih izvora - PROMENJENO OVDE!
        loadCategoriesFromAllSources() // ZAMENJENO: loadCustomCategories()

        // Postavi adapter sa potrebnim callback-ovima
        setupAdapter()

        // Proveri duplikate pri pokretanju
        checkForDuplicates()

        // Proveri potencijalne probleme
        checkForIssues()

        // ČISTAČKA RADNJA: proveri da li postoje proizvodi sa obrisanim kategorijama
        cleanupDeletedCategories()

        Log.d("KategorijeActivity", "✅ KategorijeActivity onCreate završen")
    }

    private fun cleanupDeletedCategories() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Proveri globalno obrisane kategorije
                val globalDeletedPref = getSharedPreferences("permanently_deleted_categories_global", Context.MODE_PRIVATE)
                val globalDeleted = globalDeletedPref.getStringSet("global_deleted", mutableSetOf()) ?: mutableSetOf()

                if (globalDeleted.isNotEmpty()) {
                    Log.d("Cleanup", "Proveravam obrisane kategorije: $globalDeleted")

                    // Proveri proizvode sa ovim kategorijama
                    repository.sviProizvodi { proizvodi ->
                        proizvodi.forEach { proizvod ->
                            val kategorijaProizvoda = extractCategoryName(proizvod.kategorija)

                            if (globalDeleted.contains(kategorijaProizvoda)) {
                                Log.d("Cleanup", "Pronađen proizvod sa obrisanom kategorijom: ${proizvod.naziv}")
                                // Možete ovde da obrišete proizvod ili ga premestite
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Cleanup", "Greška pri cleanup: ${e.message}")
            }
        }
    }

    private fun checkForDuplicates() {
        repository.sviProizvodi { proizvodi ->
            val naziviSet = mutableSetOf<String>()
            val duplicates = mutableListOf<Proizvod>()

            proizvodi.forEach { proizvod ->
                val kljuc = "${proizvod.naziv}|${proizvod.kategorija}"
                if (naziviSet.contains(kljuc)) {
                    duplicates.add(proizvod)
                } else {
                    naziviSet.add(kljuc)
                }
            }

            if (duplicates.isNotEmpty()) {
                Log.w("KategorijeActivity", "Pronađeno ${duplicates.size} duplikata")
                // Možete prikazati upozorenje ili automatski ukloniti duplikate
                Toast.makeText(this,
                    "Pronađeno ${duplicates.size} dupliranih proizvoda",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Postavi subtitle sa informacijama o korisniku
     */
    /**
     * Postavi subtitle sa informacijama o korisniku
     */
    private fun updateToolbarSubtitle() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        val userEmail = repository.getCurrentUserEmail()

        if (repository.isUserLoggedIn()) {
            toolbar.subtitle = userEmail ?: "Prijavljen"
        } else {
            toolbar.subtitle = "Niste prijavljeni (offline mod)"

            // Ako nije prijavljen, možda želite da prikažete poruku
            Toast.makeText(this,
                "Radite u offline modu. Podaci će biti sačuvani samo lokalno.",
                Toast.LENGTH_LONG).show()
        }
    }


    /**
     * Postavi adapter sa svim potrebnim callback-ovima
     */
    private fun setupAdapter() {
        // 1. Učitaj zamenjene kategorije
        val sharedPref = getSharedPreferences("replaced_categories", Context.MODE_PRIVATE)
        val replacedCategories = sharedPref.all

        // 2. Učitaj SVE obrisane kategorije
        val currentUser = FirebaseHelper(this).getCurrentUser()
        val userId = currentUser?.uid ?: "offline_user"

        val globalDeletedPref = getSharedPreferences("permanently_deleted_categories_global", Context.MODE_PRIVATE)
        val globalDeleted = globalDeletedPref.getStringSet("global_deleted", mutableSetOf()) ?: mutableSetOf()

        val userDeletedPref = getSharedPreferences("permanently_deleted_$userId", Context.MODE_PRIVATE)
        val userDeleted = userDeletedPref.getStringSet("user_deleted", mutableSetOf()) ?: mutableSetOf()

        val allDeletedCategories = globalDeleted + userDeleted

        // 3. Kreiraj osnovne kategorije (preskoči zamenjene i obrisane)
        val osnovneKategorije = Constants.Kategorije.SVE
            .filter { naziv ->
                !replacedCategories.containsKey(naziv) &&
                        !allDeletedCategories.contains(naziv)
            }
            .map { naziv ->
                Kategorija(
                    ikona = Constants.Kategorije.EMOJI_MAP[naziv] ?: "\uD83D\uDCDC",
                    naziv = naziv,
                    boja = Constants.Kategorije.BOJA_MAP[naziv] ?: android.R.color.holo_blue_light
                )
            }

        // 4. Filtriraj custom kategorije
        val filtriraneCustomKategorije = customCategories.filter {
            !allDeletedCategories.contains(it.naziv)
        }

        // 5. Ako ima razlike, ažuriraj
        if (filtriraneCustomKategorije.size != customCategories.size) {
            customCategories.clear()
            customCategories.addAll(filtriraneCustomKategorije)
            saveCustomCategories()
        }

        // 6. Kombinuj
        val sveKategorije = osnovneKategorije + customCategories

        // 7. Debug
        Log.d("KategorijeActivity", "=== ADAPTER ===")
        Log.d("KategorijeActivity", "Osnovne: ${osnovneKategorije.size}")
        Log.d("KategorijeActivity", "Custom: ${customCategories.size}")
        Log.d("KategorijeActivity", "Obrisane: $allDeletedCategories")

        // 8. Kreiraj adapter
        adapter = KategorijaAdapter(
            kategorije = sveKategorije,
            onKategorijaClick = { kategorija -> handleKategorijaClick(kategorija) },
            onEditCategoryClick = { kategorija ->
                if (Constants.Kategorije.OSNOVNE.contains(kategorija.naziv)) {
                    showEditCategoryDialog(kategorija)
                }
            },
            onDeleteCategoryClick = { kategorija -> showDeleteCategoryConfirmation(kategorija) },
            onUpdateCategoryClick = { stara, nova -> updateCustomCategory(stara, nova) },
            onRestoreCategoryClick = { original -> showRestoreToOriginalDialog(original) }
        )

        recyclerView.adapter = adapter
    }

    private fun showNoCategoriesMessage() {
        // Možete prikazati TextView ili Toast kada nema kategorija
        runOnUiThread {
            val noDataView = TextView(this).apply {
                text = "? Nema kategorija. Dodajte prvu kategoriju!"
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 100, 0, 0)
            }

            // Dodajte ovaj view u layout (ako želite trajnu poruku)
            // Ili jednostavno Toast:
            Toast.makeText(this,
                "Nema kategorija. Koristite '+' dugme da dodate novu.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updateCustomCategory(staraKategorija: Kategorija, novaKategorija: Kategorija) {
        // Pronađi i zameni u listi
        val index = customCategories.indexOfFirst { it.naziv == staraKategorija.naziv }
        if (index != -1) {
            customCategories[index] = novaKategorija
            saveCustomCategories()
            setupAdapter()

            // Ažuriraj sve proizvode sa starom kategorijom
            repository.sviProizvodi { proizvodi ->
                proizvodi.forEach { proizvod ->
                    if (proizvod.kategorija.contains(staraKategorija.naziv)) {
                        val novaKategorijaPunNaziv = "${novaKategorija.ikona} ${novaKategorija.naziv}"
                        val azuriranProizvod = proizvod.copy(kategorija = novaKategorijaPunNaziv)
                        repository.azurirajProizvod(azuriranProizvod) { success ->
                            if (success) {
                                Log.d("UpdateCategory", "Ažuriran proizvod: ${proizvod.naziv}")
                            }
                        }
                    }
                }

                runOnUiThread {
                    Toast.makeText(this,
                        "Kategorija ažurirana za sve proizvode",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    /**
     * Proverava potencijalne probleme
     */
    /**
     * Proverava potencijalne probleme
     */
    private fun checkForIssues() {
        // Proveri da li Firebase radi
        val firebaseHelper = FirebaseHelper(this)
        if (!firebaseHelper.isFirebaseConfigured()) {
            Toast.makeText(this,
                "Firebase nije pravilno konfigurisan. Cloud funkcionalnosti neće raditi.",
                Toast.LENGTH_LONG).show()
        }

        // Proveri broj proizvoda
        repository.brojProizvodaZaKorisnika { broj ->
            Log.d("KategorijeActivity", "Korisnik ima $broj proizvoda")
            if (broj == 0) {
                // Možete prikazati poruku za novog korisnika
                // showWelcomeMessage()
            }
        }
    }

    /**
     * Potvrda brisanja kategorije
     */
    private fun showDeleteCategoryConfirmation(kategorija: Kategorija) {
        AlertDialog.Builder(this)
            .setTitle("Brisanje kategorije '${kategorija.naziv}'")
            .setMessage(" KATEGORIJA IMA PROIZVODE!\n\n" +
                    "Izaberite  šta  želite:\n\n" +
                    "1. Obriši SAMO kategoriju\n" +
                    "   (proizvodi ostaju bez kategorije)\n\n" +
                    "2. Obriši kategoriju I PROIZVODE\n" +
                    "   (trajno briše sve u ovoj kategoriji)\n\n" +
                    "3. Premesti proizvode u drugu kategoriju\n" +
                    "   (pa onda obriši praznu kategoriju)")
            .setPositiveButton("SAMO kategoriju") { _, _ ->
                deleteCategoryOnly(kategorija)
            }
            .setNegativeButton("Kategoriju i proizvode") { _, _ ->
                deleteCategoryWithProducts(kategorija)
            }
            .setNeutralButton("Premesti proizvode") { _, _ ->
                showMoveProductsDialog(kategorija)
            }
            .show()
    }

    private fun markCategoryAsPermanentlyDeleted(categoryName: String) {
        try {
            // 1. Ozna i u globalnim obrisanim kategorijama
            val globalDeletedPref = getSharedPreferences("permanently_deleted_categories_global", Context.MODE_PRIVATE)
            val globalDeleted = globalDeletedPref.getStringSet("global_deleted", mutableSetOf()) ?: mutableSetOf()
            globalDeleted.add(categoryName)
            globalDeletedPref.edit().putStringSet("global_deleted", globalDeleted).apply()

            // 2. Ozna i za trenutnog korisnika
            val currentUser = FirebaseHelper(this).getCurrentUser()
            val userId = currentUser?.uid ?: "offline_user"
            val userDeletedPref = getSharedPreferences("permanently_deleted_$userId", Context.MODE_PRIVATE)
            val userDeleted = userDeletedPref.getStringSet("user_deleted", mutableSetOf()) ?: mutableSetOf()
            userDeleted.add(categoryName)
            userDeletedPref.edit().putStringSet("user_deleted", userDeleted).apply()

            // 3. OBELE I U BAZI PROIZVODA (KLJUČNO!)
            markProductsCategoryAsDeleted(categoryName)

            Log.d("DeleteCategory", "Kategorija '$categoryName' trajno obeležena kao obrisana")

        } catch (e: Exception) {
            Log.e("DeleteCategory", "Greška pri obeležavanju kategorije: ${e.message}")
        }
    }

    private fun markProductsCategoryAsDeleted(categoryName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.sviProizvodi { sviProizvodi ->
                    val proizvodiUKategoriji = sviProizvodi.filter {
                        val proizvodKategorija = extractCategoryName(it.kategorija)
                        proizvodKategorija == categoryName
                    }

                    if (proizvodiUKategoriji.isNotEmpty()) {
                        // Ažuriraj proizvode da imaju specijalnu oznaku
                        proizvodiUKategoriji.forEach { proizvod ->
                            val azuriranProizvod = proizvod.copy(
                                kategorija = "[OBRISANO] ${proizvod.kategorija}"
                            )
                            repository.azurirajProizvod(azuriranProizvod) { success ->
                                if (success) {
                                    Log.d("DeleteCategory", "Proizvod obeležen: ${proizvod.naziv}")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DeleteCategory", "Greška pri obeležavanju proizvoda: ${e.message}")
            }
        }
    }
    private fun deleteCategoryWithProducts(kategorija: Kategorija) {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("TRAJNO BRISANJE")
            setMessage("Brišem kategoriju '${kategorija.naziv}' i SVE proizvode u njoj...")
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.sviProizvodi { sviProizvodi ->
                    val proizvodiUKategoriji = sviProizvodi.filter {
                        val proizvodKategorija = extractCategoryName(it.kategorija)
                        proizvodKategorija == kategorija.naziv
                    }

                    Log.d("DeleteCategory", "Pronađeno ${proizvodiUKategoriji.size} proizvoda")

                    if (proizvodiUKategoriji.isEmpty()) {
                        // Nema proizvoda, samo obriši kategoriju
                        runOnUiThread {
                            markCategoryAsPermanentlyDeleted(kategorija.naziv)
                            customCategories.removeAll { it.naziv == kategorija.naziv }
                            saveCustomCategories()
                            setupAdapter()
                            progressDialog.dismiss()
                            Toast.makeText(this@KategorijeActivity,
                                "? Kategorija '${kategorija.naziv}' obrisana",
                                Toast.LENGTH_SHORT).show()
                        }
                        return@sviProizvodi
                    }

                    // Obriši sve proizvode
                    var obrisanoProizvoda = 0
                    val ukupnoProizvoda = proizvodiUKategoriji.size

                    proizvodiUKategoriji.forEach { proizvod ->
                        repository.obrisiProizvod(proizvod) { success ->
                            obrisanoProizvoda++
                            Log.d("DeleteCategory", "Obrisan proizvod: ${proizvod.naziv}")

                            // Kada su svi proizvodi obrisani
                            if (obrisanoProizvoda == ukupnoProizvoda) {
                                runOnUiThread {
                                    // Označi kategoriju kao trajno obrisanu
                                    markCategoryAsPermanentlyDeleted(kategorija.naziv)

                                    // Ukloni iz liste
                                    customCategories.removeAll { it.naziv == kategorija.naziv }
                                    saveCustomCategories()

                                    // Osveži
                                    setupAdapter()
                                    progressDialog.dismiss()

                                    Toast.makeText(this@KategorijeActivity,
                                        " TRAJNO OBRISANO:\n" +
                                                "- Kategorija: '${kategorija.naziv}'\n" +
                                                "- Proizvodi: $ukupnoProizvoda",
                                        Toast.LENGTH_LONG).show()

                                    // Broadcast
                                    val intent = Intent("PODACI_OSVEŽENI")
                                    LocalBroadcastManager.getInstance(this@KategorijeActivity).sendBroadcast(intent)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this@KategorijeActivity,
                        " Greška: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun deleteCategoryOnly(kategorija: Kategorija) {
        // 1. Označi kategoriju kao TRAJNO OBRISANU
        markCategoryAsPermanentlyDeleted(kategorija.naziv)

        // 2. Ukloni iz trenutne liste (samo za ovu sesiju)
        customCategories.removeAll { it.naziv == kategorija.naziv }

        // 3. Obriši iz SharedPreferences (custom kategorije)
        saveCustomCategories()

        // 4. Obeleži kao zamenjenu (ako je osnovna kategorija)
        if (Constants.Kategorije.OSNOVNE.contains(kategorija.naziv)) {
            val sharedPref = getSharedPreferences("replaced_categories", Context.MODE_PRIVATE)
            sharedPref.edit().remove(kategorija.naziv).apply()
        }

        // 5. Osveži prikaz
        setupAdapter()

        Toast.makeText(this,
            "? Kategorija '${kategorija.naziv}' obrisana\n" +
                    "Proizvodi su ostali bez kategorije",
            Toast.LENGTH_LONG
        ).show()

        // 6. Pošalji broadcast za osvežavanje
        val intent = Intent("KATEGORIJA_OBRISANA")
        intent.putExtra("OBRISANA_KATEGORIJA", kategorija.naziv)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    private fun showMoveProductsDialog(kategorija: Kategorija) {
        // Prikaži sve dostupne kategorije (osim one koja se briše)
        val availableCategories = mutableListOf<Kategorija>()

        // Dodaj osnovne kategorije
        Constants.Kategorije.SVE.forEach { naziv ->
            if (naziv != kategorija.naziv) {
                availableCategories.add(Kategorija(
                    ikona = Constants.Kategorije.EMOJI_MAP[naziv] ?: "\uD83D\uDCDC",
                    naziv = naziv,
                    boja = Constants.Kategorije.BOJA_MAP[naziv] ?: android.R.color.holo_blue_light
                ))
            }
        }

        // Dodaj custom kategorije (osim one koja se briše)
        customCategories.forEach { customCat ->
            if (customCat.naziv != kategorija.naziv) {
                availableCategories.add(customCat)
            }
        }

        if (availableCategories.isEmpty()) {
            Toast.makeText(this, "Nema drugih kategorija za premestanje", Toast.LENGTH_SHORT).show()
            return
        }

        val categoryNames = availableCategories.map { it.naziv }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Premesti proizvode iz '${kategorija.naziv}' u:")
            .setItems(categoryNames) { _, which ->
                val selectedCategory = availableCategories[which]
                moveProductsToAnotherCategory(kategorija, selectedCategory)
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun moveProductsToAnotherCategory(fromCategory: Kategorija, toCategory: Kategorija) {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Premestanje...")
            setMessage("Premi em proizvode iz '${fromCategory.naziv}' u '${toCategory.naziv}'")
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.sviProizvodi { sviProizvodi ->
                    val proizvodiZaPremestanje = sviProizvodi.filter {
                        val proizvodKategorija = KategorijeActivity.extractCategoryName(it.kategorija)
                        val fromCategoryName = KategorijeActivity.extractCategoryName(fromCategory.naziv)
                        proizvodKategorija == fromCategoryName
                    }

                    if (proizvodiZaPremestanje.isEmpty()) {
                        runOnUiThread {
                            progressDialog.dismiss()
                            deleteCategoryOnly(fromCategory)
                            Toast.makeText(
                                this@KategorijeActivity,
                                "Nema proizvoda za premestanje. Kategorija obrisana.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@sviProizvodi
                    }

                    var premesteno = 0
                    val ukupno = proizvodiZaPremestanje.size

                    proizvodiZaPremestanje.forEach { proizvod ->
                        val novaKategorija = "${toCategory.ikona} ${toCategory.naziv}"
                        val azuriranProizvod = proizvod.copy(kategorija = novaKategorija)

                        repository.azurirajProizvod(azuriranProizvod) { success ->
                            premesteno++

                            if (success) {
                                Log.d("MoveProducts", "Premesten proizvod: ${proizvod.naziv} ($premesteno/$ukupno)")
                            }

                            // Kada su svi premesteni
                            if (premesteno == ukupno) {
                                runOnUiThread {
                                    progressDialog.dismiss()
                                    deleteCategoryOnly(fromCategory)

                                    Toast.makeText(
                                        this@KategorijeActivity,
                                        "? $ukupno proizvoda premesteno u '${toCategory.naziv}'",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    // Po alji broadcast
                                    val intent = Intent("PODACI_OSVE ENI")
                                    LocalBroadcastManager.getInstance(this@KategorijeActivity).sendBroadcast(intent)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@KategorijeActivity,
                        "Gre ka pri premestanju: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    private fun markCategoryAsDeleted(categoryName: String) {
        val sharedPref = getSharedPreferences("deleted_categories", Context.MODE_PRIVATE)
        val deletedCategories = sharedPref.getStringSet("deleted", mutableSetOf()) ?: mutableSetOf()
        deletedCategories.add(categoryName)
        sharedPref.edit().putStringSet("deleted", deletedCategories).apply()

        Log.d("DeleteCategory", "Kategorija '$categoryName' ozna ena kao obrisana")
    }


    /**
     * Vraćanje na originalnu kategoriju
     */
    private fun showRestoreToOriginalDialog(originalKategorija: Kategorija) {
        AlertDialog.Builder(this)
            .setTitle("Vraćanje na original")
            .setMessage("Da li želite da vratite kategoriju '${originalKategorija.naziv}'?\n\n" +
                    "Svi proizvodi će biti prebačeni u originalnu kategoriju.")
            .setPositiveButton("Vrati") { _, _ ->
                // Ukloni custom verziju
                val modifiedName = findModifiedCategoryName(originalKategorija.naziv)
                if (modifiedName != null) {
                    customCategories.removeAll { it.naziv == modifiedName }
                }

                // Ukloni iz shared preferences
                val sharedPref = getSharedPreferences("replaced_categories", Context.MODE_PRIVATE)
                sharedPref.edit().remove(originalKategorija.naziv).apply()

                // Sačuvaj promene
                saveCustomCategories()

                // Ažuriraj proizvode da koriste originalnu kategoriju
                updateProductsToOriginalCategory(originalKategorija, modifiedName)

                Toast.makeText(this, "Kategorija vraćena na original", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    /**
     * Pronalazi modifikovano ime kategorije (ako postoji)
     */
    private fun findModifiedCategoryName(originalName: String): String? {
        return customCategories.find {
            it.naziv.startsWith(originalName) && it.naziv != originalName
        }?.naziv
    }

    /**
     * Ažurira proizvode da koriste originalnu kategoriju
     */
    private fun updateProductsToOriginalCategory(original: Kategorija, modifiedName: String?) {
        if (modifiedName != null) {
            val modifiedKategorija = customCategories.find { it.naziv == modifiedName }
            if (modifiedKategorija != null) {
                val staraKategorijaPunNaziv = "${modifiedKategorija.ikona} ${modifiedKategorija.naziv}"
                val novaKategorijaPunNaziv = "${original.ikona} ${original.naziv}"

                repository.sviProizvodi { sviProizvodi ->
                    val proizvodiZaAzuriranje = sviProizvodi.filter {
                        it.kategorija == staraKategorijaPunNaziv
                    }

                    if (proizvodiZaAzuriranje.isEmpty()) {
                        // Nema proizvoda za ažuriranje
                        runOnUiThread {
                            setupAdapter()
                            Toast.makeText(this,
                                "Nema proizvoda u ovoj kategoriji za vraćanje",
                                Toast.LENGTH_SHORT).show()
                        }

                        // Pošalji LOCAL broadcast
                        sendCategoryRestoredBroadcast(novaKategorijaPunNaziv)
                        return@sviProizvodi
                    }

                    var uspesnoAzurirano = 0
                    val ukupnoZaAzuriranje = proizvodiZaAzuriranje.size

                    proizvodiZaAzuriranje.forEach { proizvod ->
                        val azuriranProizvod = proizvod.copy(kategorija = novaKategorijaPunNaziv)
                        repository.azurirajProizvod(azuriranProizvod) { success ->
                            uspesnoAzurirano++

                            Log.d("RestoreCategory",
                                "Ažuriran proizvod: ${proizvod.naziv} ($uspesnoAzurirano/$ukupnoZaAzuriranje)")

                            // Kada su svi ažurirani
                            if (uspesnoAzurirano == ukupnoZaAzuriranje) {
                                runOnUiThread {
                                    setupAdapter()
                                    Toast.makeText(this,
                                        "✅ Vraćeno $uspesnoAzurirano proizvoda u originalnu kategoriju",
                                        Toast.LENGTH_SHORT).show()
                                }

                                // Pošalji LOCAL broadcast
                                sendCategoryRestoredBroadcast(novaKategorijaPunNaziv)
                            }
                        }
                    }
                }
            }
        } else {
            // Nema modifikovane kategorije
            runOnUiThread {
                setupAdapter()
            }

            // Pošalji LOCAL broadcast
            sendCategoryRestoredBroadcast("${original.ikona} ${original.naziv}")
        }
    }

    /**
     * Pomoćna metoda za slanje LOCAL broadcast-a
     */
    private fun sendCategoryRestoredBroadcast(originalKategorija: String) {
        val intent = Intent("KATEGORIJA_VRAĆENA")
        intent.putExtra("ORIGINAL_KATEGORIJA", originalKategorija)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("RestoreCategory", "✅ LOCAL broadcast poslat: KATEGORIJA_VRAĆENA - $originalKategorija")
    }

    private fun saveCustomCategories() {
        try {
            val currentUser = FirebaseHelper(this).getCurrentUser()
            val userId = currentUser?.uid ?: "offline_user_${System.currentTimeMillis()}"

            // Proveri da li postoje trajno obrisane kategorije
            val deletedPref = getSharedPreferences("permanently_deleted_categories", Context.MODE_PRIVATE)
            val permanentlyDeleted = deletedPref.getStringSet("permanent", mutableSetOf()) ?: mutableSetOf()

            // Filtriraj pre cuvanja (koristite LATINIČNA slova)
            val kategorijeZaCuvanje = customCategories.filter {
                !permanentlyDeleted.contains(it.naziv)
            }.toMutableList()

            // IZOLACIJA: svaki korisnik ima svoje shared preferences
            val sharedPref = getSharedPreferences("categories_$userId", Context.MODE_PRIVATE)
            val categoriesJson = Gson().toJson(kategorijeZaCuvanje) // Koristite latinično "Cuvanje"

            with(sharedPref.edit()) {
                putString("custom_categories", categoriesJson)
                apply()
            }

            Log.d("KategorijeActivity", "Sacuvano ${kategorijeZaCuvanje.size} custom kategorija za user: $userId")

            // Debug info
            if (permanentlyDeleted.isNotEmpty()) {
                Log.d("KategorijeActivity", "Izostavljene obrisane kategorije: $permanentlyDeleted")
            }

        } catch (e: Exception) {
            Log.e("KategorijeActivity", "Greska pri cuvanju kategorija: ${e.message}") // I ovde latinično
        }
    }

    private fun loadCustomCategories() {
        try {
            val currentUser = FirebaseHelper(this).getCurrentUser()
            val userId = currentUser?.uid ?: "offline_user"

            // 1. Učitaj custom kategorije
            val sharedPref = getSharedPreferences("categories_$userId", Context.MODE_PRIVATE)
            val categoriesJson = sharedPref.getString("custom_categories", "[]")

            val type = object : TypeToken<MutableList<Kategorija>>() {}.type
            val loadedCategories = Gson().fromJson<MutableList<Kategorija>>(categoriesJson, type)

            // 2. Proveri GLOBALNO obrisane kategorije
            val globalDeletedPref = getSharedPreferences("permanently_deleted_categories_global", Context.MODE_PRIVATE)
            val globalDeleted = globalDeletedPref.getStringSet("global_deleted", mutableSetOf()) ?: mutableSetOf()

            // 3. Proveri obrisane za OVOG KORISNIKA
            val userDeletedPref = getSharedPreferences("permanently_deleted_$userId", Context.MODE_PRIVATE)
            val userDeleted = userDeletedPref.getStringSet("user_deleted", mutableSetOf()) ?: mutableSetOf()

            // 4. Kombinuj sve obrisane kategorije
            val allDeletedCategories = globalDeleted + userDeleted

            if (loadedCategories != null) {
                customCategories.clear()

                // 5. FILTRIRAJ - ukloni sve obrisane kategorije
                val filtriraneKategorije = loadedCategories.filter {
                    !allDeletedCategories.contains(it.naziv)
                }

                customCategories.addAll(filtriraneKategorije)

                Log.d("KategorijeActivity", "Učitano ${loadedCategories.size} kategorija")
                Log.d("KategorijeActivity", "Filtrirano na ${customCategories.size}")
                Log.d("KategorijeActivity", "Obrisane kategorije: $allDeletedCategories")

                // 6. Ako je bilo obrisanih, sačuvaj filtriranu listu
                if (loadedCategories.size != customCategories.size) {
                    saveCustomCategories()
                    Log.d("KategorijeActivity", "? Filtrirana lista sačuvana")
                }
            }

        } catch (e: Exception) {
            Log.e("KategorijeActivity", "Greška pri učitavanju kategorija: ${e.message}")
        }
    }

    private fun clearCurrentUserData() {
        try {
            val currentUser = FirebaseHelper(this).getCurrentUser()
            val userId = currentUser?.uid ?: "offline_user"

            // Obriši shared preferences za ovog korisnika
            val sharedPref = getSharedPreferences("categories_$userId", Context.MODE_PRIVATE)
            sharedPref.edit().clear().apply()

            Log.d("KategorijeActivity", "Obrisani podaci za user: $userId")
        } catch (e: Exception) {
            Log.e("KategorijeActivity", "Greška pri brisanju podataka: ${e.message}")
        }
    }

    private fun handleKategorijaClick(kategorija: Kategorija) {
        Log.d("KategorijeActivity", "Kliknuta kategorija: ${kategorija.naziv}")
        Log.d("KategorijeActivity", "Kategorija ikona: ${kategorija.ikona}")

        when (kategorija.naziv) {
            "Dodaj proizvod" -> {
                startActivity(Intent(this, DodajIzmeniActivity::class.java))
            }
            "Sve stavke" -> {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    putExtra("FILTER", "ALL")
                })
            }
            "Niska zaliha" -> {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    putExtra("FILTER", "LOW_STOCK")
                })
            }
            else -> {
                // VAŽNO: Šaljemo PUNU kategoriju sa emoji
                val filter = "${kategorija.ikona} ${kategorija.naziv}"
                Log.d("KategorijeActivity", "Šaljem filter: '$filter'")

                startActivity(Intent(this, MainActivity::class.java).apply {
                    putExtra("FILTER", filter)
                })
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d("KategorijeActivity", "Kreiranje menija")

        // Ne učitavaj iz XML-a, kreiraj ručno
        menu.clear()

        // KORISTITE PRAVE UNICODE KARAKTERE (copy-paste emoji):
        menu.add(0, 1000, 1, "📊 Sortiraj kategorije").apply {
            val spannable = android.text.SpannableString(title)
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(Color.BLACK),
                0,
                spannable.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            title = spannable
        }

        // KOPIRAJTE EMOJI-JE SA OVOG SPISKA:
        // 📊 📡 🧹 📈 🚪 💾

        menu.add(0, 1001, 1, "📡 Cloud Sync").apply {
            // Forsiraj crni tekst
            val spannable = android.text.SpannableString(title)
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(Color.BLACK),
                0,
                spannable.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            // Podebljaj tekst
            spannable.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                0,
                spannable.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            title = spannable
        }

        menu.add(0, 1002, 2, "🧹 Očisti duplikate").apply {
            val spannable = android.text.SpannableString(title)
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(Color.BLACK),
                0,
                spannable.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                0,
                spannable.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            title = spannable
        }

        menu.add(0, 1003, 3, "📈 Statistike").apply {
            val spannable = android.text.SpannableString(title)
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(Color.BLACK),
                0,
                spannable.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                0,
                spannable.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            title = spannable
        }

        menu.add(0, 1004, 4, "🚪 Odjava").apply {
            val spannable = SpannableString(title)
            spannable.setSpan(
                ForegroundColorSpan(Color.BLACK),
                0,
                spannable.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                0,
                spannable.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            title = spannable
        }

        menu.add(0, 1005, 5, "💾 Lokalni Backup").apply {
            val spannable = android.text.SpannableString(title)
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(Color.BLACK),
                0,
                spannable.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            title = spannable
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {

            1000 -> { // Sortiraj kategorije
                showSortCategoriesDialog()
                true
            }

            1001 -> { // Cloud Sync
                startActivity(Intent(this, CloudSyncActivity::class.java))
                true
            }
            1002 -> { // Očisti duplikate
                showCleanDuplicatesDialog()
                true
            }
            1003 -> { // Statistike
                startActivity(Intent(this, StatsActivity::class.java))  // OVO
                true
            }
            1004 -> { // Odjava
                showLogoutDialog()
                true
            }

            1005 -> { // LOKALNI BACKUP - OVO DODAJTE!
                startActivity(Intent(this, BackupRestoreActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadCategoriesFromAllSources() {
        // 1. U itaj lokalne custom kategorije
        loadCustomCategories()

        // 2. Proveri da li ima obrisanih kategorija i ukloni ih
        filterOutDeletedCategories()

        // 3. Proveri da li ima podataka u Cloud-u
        val firebaseHelper = FirebaseHelper(this)
        val currentUser = firebaseHelper.getCurrentUser()

        if (currentUser != null) {
            // 4. U itaj kategorije iz Cloud-a (ako postoje)
            loadCloudCategories(currentUser.uid)
        } else {
            // Ako nije prijavljen, samo poka i lokalne
            setupAdapter()
        }
    }

    private fun filterOutDeletedCategories() {
        val sharedPref = getSharedPreferences("deleted_categories", Context.MODE_PRIVATE)
        val deletedCategories = sharedPref.getStringSet("deleted", mutableSetOf()) ?: mutableSetOf()

        if (deletedCategories.isNotEmpty()) {
            // Ukloni obrisane kategorije iz liste
            val iterator = customCategories.iterator()
            while (iterator.hasNext()) {
                val category = iterator.next()
                if (deletedCategories.contains(category.naziv)) {
                    iterator.remove()
                    Log.d("FilterCategories", "Uklonjena obrisana kategorija: ${category.naziv}")
                }
            }

            // Sačuvaj a uriranu listu
            saveCustomCategories()
        }
    }
    private fun loadCloudCategories(userId: String) {
        coroutineScope.launch {
            try {
                val firebaseHelper = FirebaseHelper(this@KategorijeActivity)
                val cloudProizvodi = withContext(Dispatchers.IO) {
                    firebaseHelper.restoreFromCloud()
                }

                if (cloudProizvodi.isNotEmpty()) {
                    // 4. Ekstraktuj sve jedinstvene kategorije iz cloud proizvoda
                    val cloudCategories = cloudProizvodi
                        .map { it.kategorija }
                        .distinct()
                        .filter { !isOsnovnaKategorija(it) } // Filtriraj samo custom

                    // 5. Dodaj custom kategorije koje ne postoje lokalno
                    addMissingCategoriesFromCloud(cloudCategories)

                    withContext(Dispatchers.Main) {
                        setupAdapter()
                    }
                } else {
                    setupAdapter()
                }
            } catch (e: Exception) {
                Log.e("KategorijeActivity", "Greška pri učitavanju cloud kategorija: ${e.message}")
                setupAdapter()
            }
        }
    }

    private fun isOsnovnaKategorija(fullCategory: String): Boolean {
        val categoryName = KategorijeActivity.extractCategoryName(fullCategory)
        return Constants.Kategorije.OSNOVNE.contains(categoryName) ||
                Constants.Kategorije.SVE.contains(categoryName)
    }

    private fun addMissingCategoriesFromCloud(cloudCategories: List<String>) {
        cloudCategories.forEach { fullCategory ->
            try {
                val categoryName = KategorijeActivity.extractCategoryName(fullCategory)
                val emoji = extractEmoji(fullCategory)

                // Proveri da li već postoji
                val postoji = customCategories.any { it.naziv == categoryName }

                if (!postoji && !isOsnovnaKategorija(fullCategory)) {
                    // Kreiraj novu kategoriju
                    val novaKategorija = Kategorija(
                        ikona = emoji,
                        naziv = categoryName,
                        boja = getRandomColor()
                    )
                    customCategories.add(novaKategorija)
                }
            } catch (e: Exception) {
                Log.e("KategorijeActivity", "Greška pri dodavanju kategorije: ${e.message}")
            }
        }

        // Sačuvaj ažurirane kategorije
        saveCustomCategories()
    }

    private fun getRandomColor(): Int {
        val boje = listOf(
            R.color.green_light,
            android.R.color.holo_green_light,
            R.color.blue_light,
            android.R.color.holo_blue_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_purple,
            R.color.purple_light,
            android.R.color.holo_red_light
        )
        return boje.random()
    }




    private fun showSortCategoriesDialog() {
        val items = arrayOf(
            "Naziv (A → Z)",
            "Naziv (Z → A)",
            "Po broju proizvoda"
        )

        AlertDialog.Builder(this)
            .setTitle("🔀 Sortiraj kategorije")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> sortCategoriesByName(true)
                    1 -> sortCategoriesByName(false)
                    2 -> sortCategoriesByProductCount()
                }
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun sortCategoriesByProductCount() {
        // Ovo je kompleksnije - treba prebrojati proizvode po kategoriji
        Toast.makeText(this, "Funkcionalnost u razvoju", Toast.LENGTH_SHORT).show()
    }
    private fun sortCategoriesByName(ascending: Boolean) {
        if (ascending) {
            customCategories.sortBy { it.naziv }
        } else {
            customCategories.sortByDescending { it.naziv }
        }
        saveCustomCategories()
        setupAdapter()
        Toast.makeText(this, "Kategorije sortirane", Toast.LENGTH_SHORT).show()
    }
    /**
     * Prikazuje dijalog za odjavu
     */
    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚪 Odjava")
            .setMessage("Da li ste sigurni da želite da se odjavite?\n\n" +
                    "Napomena: Vaši lokalni podaci će ostati sačuvani.")
            .setPositiveButton("Odjavi se") { dialog, _ ->
                dialog.dismiss()
                performLogout()
            }
            .setNegativeButton("Otkaži") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }


    private fun showCleanDuplicatesDialog() {
        AlertDialog.Builder(this)
            .setTitle("🔍 Čišćenje duplikata")
            .setMessage("Da li želite da skenirate bazu i obrišete sve duplirane proizvode?\n\n" +
                    "Ova akcija će:\n" +
                    "1. Pronaći sve proizvode sa istim nazivom i kategorijom\n" +
                    "2. Zadržati prvi pronađeni proizvod\n" +
                    "3. Obrisati sve ostale duplikate")
            .setPositiveButton("Skeniraj i očisti") { dialog, _ ->
                dialog.dismiss()
                startCleaningDuplicates()
            }
            .setNegativeButton("Otkaži") { dialog, _ ->
                dialog.dismiss()
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    /**
     * Pokreće proces čišćenja duplikata
     */
    private fun startCleaningDuplicates() {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("🔍 Skeniranje duplikata")
            setMessage("Proveravam bazu podataka...\nMolimo sačekajte.")
            setCancelable(false)
            show()
        }

        repository.proveriIIspraviDuplikate { brojDuplikata ->
            runOnUiThread {
                progressDialog.dismiss()

                if (brojDuplikata > 0) {
                    AlertDialog.Builder(this@KategorijeActivity)
                        .setTitle("✅ Čišćenje završeno")
                        .setMessage("Obrisano je $brojDuplikata dupliranih proizvoda.")
                        .setPositiveButton("OK") { dialog, _ ->
                            dialog.dismiss()
                            // Osveži prikaz ako je potrebno
                        }
                        .show()
                } else {
                    Toast.makeText(
                        this@KategorijeActivity,
                        "✅ Nema dupliranih proizvoda u bazi",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    private fun performLogout() {
        // Firebase logout
        FirebaseHelper(this).logout()

        Toast.makeText(this, "Uspešno odjavljeni", Toast.LENGTH_SHORT).show()

        // Vrati na LoginActivity
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showAddCategoryDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null)
        val etNaziv = dialogView.findViewById<EditText>(R.id.etNazivKategorije)
        val spEmoji = dialogView.findViewById<Spinner>(R.id.spEmoji)
        val spBoja = dialogView.findViewById<Spinner>(R.id.spBoja)


        // Lista emoji-ja kao Unicode stringovi
        val emojiList = listOf(
            "\uD83C\uDF54", // 🍔 Hamburger
            "\uD83E\uDD64", // 🥤 Cup with straw
            "\uD83D\uDCDD", // 📝 Memo
            "\uD83D\uDC55", // 👕 T-Shirt
            "\uD83E\uDDF9", // 🧹 Broom
            "\uD83D\uDCF1", // 📱 Mobile phone
            "\uD83D\uDECD", // 🛍️ Shopping bags
            "\uD83D\uDCBC", // 💼 Briefcase
            "\uD83C\uDF7A", // 🍺 Beer mug
            "\uD83C\uDF2E", // 🌮 Taco
            "\uD83C\uDF6A", // 🍪 Cookie
            "\uD83C\uDF69", // 🍩 Doughnut
            "\uD83E\uDD5E", // 🍞 Bread
            "\uD83C\uDF53", // 🍓 Strawberry
            "\uD83E\uDDC0", // 🧀 Cheese wedge
            "\uD83D\uDD27", // 🔧 Wrench
            "\uD83D\uDE9B", // 🚛 Articulated lorry
            "\uD83D\uDE97", // 🚗 Automobile
            "\uD83C\uDFA7", // 🎧 Headphone
            "\uD83D\uDCF7", // 📷 Camera
            "\uD83D\uDCBB", // 💻 Laptop
            "\uD83D\uDCFA", // 📺 Television
            "\uD83D\uDCE6", // 📦 Package
            "\uD83D\uDED2", // 🛒 Shopping cart
            "\uD83D\uDCB0", // 💰 Money bag
            "\uD83D\uDCB5", // 💵 Dollar banknote
            "\u002B\uFE0F\u20E3", // ➕ Plus
            "\u2796", // ➖ Minus
            "\u2705", // ✅ Check mark
            "\u274C", // ❌ Cross mark
            "\u2B55", // ⭕ Hollow red circle
            "\uD83D\uDD34", // 🔴 Red circle
            "\uD83D\uDFE2", // 🟢 Green circle
            "\uD83D\uDFE1", // 🟡 Yellow circle
            "\uD83D\uDD35", // 🔵 Blue circle
            "\uD83D\uDD36",  // 🔶 Large orange diamond
            "\uD83D\uDDA5", // 🖥️ Desktop computer
            "\uD83D\uDC8E", // 💎 Gem stone
            "\uD83D\uDD0C", // 🔌 Electric plug
            "\uD83D\uDD0B", // 🔋 Battery
            "\u2699\uFE0F", // ⚙️ Gear
            "\uD83D\uDCE7", // 📧 E-mail
            "\uD83D\uDCF2", // 📲 Mobile phone with arrow
            "\uD83D\uDCF8", // 📸 Camera with flash
            "\uD83D\uDC4D", // 👍 Thumbs up
            "\uD83D\uDC4E", // 👎 Thumbs down
            "\uD83C\uDF89", // 🎉 Party popper
            "\uD83C\uDF81", // 🎁 Wrapped gift
            "\uD83C\uDF82", // 🎂 Birthday cake
            "\uD83D\uDCA1", // 💡 Light bulb
            "\uD83D\uDD28", // 🔨 Hammer
            "\uD83D\uDEE0", // 🛠️ Hammer and wrench
            "\uD83D\uDCDA", // 📚 Books
            "\uD83D\uDCD6", // 📖 Open book
            "\uD83D\uDC68\u200D\uD83D\uDCBB", // 👨‍💻 Man technologist
            "\uD83D\uDC69\u200D\uD83D\uDCBB", // 👩‍💻 Woman technologist
            "\uD83D\uDCBB", // 💻 Laptop (već postoji, ali za svaki slučaj)
            "\uD83D\uDCFB", // 📻 Radio
            "\uD83D\uDD79\uFE0F", // 🕹️ Joystick
            "\uD83D\uDCBE", // 💾 Floppy disk
            "\uD83D\uDDB2", // 🖲️ Trackball
            "\uD83D\uDDA8", // 🖨️ Printer
            "\uD83D\uDD8B", // 🖋️ Fountain pen
            "\uD83D\uDD8C", // 🖌️ Paintbrush
            "\uD83D\uDD8D", // 🖍️ Crayon
            "\uD83E\uDD16", // 🤖 Robot
            "\uD83D\uDE80", // 🚀 Rocket
            "\uD83D\uDEA8", // 🚨 Police car light
            "\uD83D\uDEF0", // 🛰️ Satellite
            "\uD83D\uDEF8", // 🛸 Flying saucer
            "\u231A", // ⌚ Watch
            "\uD83D\uDD11", // 🔑 Key
            "\uD83D\uDDDD\uFE0F", // 🗝️ Old key
            "\uD83D\uDCB3", // 💳 Credit card
            "\uD83D\uDCB8", // 💸 Money with wings
            "\uD83D\uDC5F", // 👟 Running shoe
            "\uD83C\uDFAD", // 🎭 Performing arts
            "\uD83C\uDFA8", // 🎨 Artist palette
            "\uD83C\uDFB9", // 🎹 Musical keyboard
            "\uD83C\uDFBA", // 🎺 Trumpet
            "\uD83C\uDFBB", // 🎻 Violin
            "\uD83E\uDD41", // 🥁 Drum
            "\uD83C\uDFB7", // 🎷 Saxophone
            "\uD83C\uDFB8", // 🎸 Guitar
            "\uD83D\uDD2A", // 🔪 Kitchen knife
            "\uD83C\uDF71", // 🍱 Bento box
            "\uD83C\uDF72", // 🍲 Pot of food
            "\uD83C\uDF73", // 🍳 Cooking
            "\uD83C\uDF74", // 🍴 Fork and knife
            "\uD83C\uDF75", // 🍵 Teacup without handle
            "\uD83C\uDF76", // 🍶 Sake
            "\uD83C\uDF77", // 🍷 Wine glass
            "\uD83C\uDF78", // 🍸 Cocktail glass
            "\uD83C\uDF79", // 🍹 Tropical drink
            "\uD83C\uDF7B", // 🍻 Clinking beer mugs
            "\uD83C\uDF7C", // 🍼 Baby bottle
            "\uD83C\uDF7D\uFE0F", // 🍽️ Fork and knife with plate
            "\uD83C\uDF7E", // 🍾 Bottle with popping cork
            "\uD83C\uDF7F", // 🍿 Popcorn
            "\uD83C\uDF80", // 🎀 Ribbon
            "\uD83C\uDF81", // 🎁 Wrapped gift
            "\uD83C\uDF82", // 🎂 Birthday cake
            "\uD83C\uDF83", // 🎃 Jack-o-lantern
            "\uD83C\uDF84", // 🎄 Christmas tree
            "\uD83C\uDF85", // 🎅 Santa Claus
            "\uD83C\uDF86", // 🎆 Fireworks
            "\uD83C\uDF87", // 🎇 Sparkler
            "\uD83C\uDF88", // 🎈 Balloon
            "\uD83C\uDF89", // 🎉 Party popper
            "\uD83C\uDF8A", // 🎊 Confetti ball
            "\uD83C\uDF8B", // 🎋 Tanabata tree
            "\uD83C\uDF8C", // 🎌 Crossed flags
            "\uD83C\uDF8D", // 🎍 Pine decoration
            "\uD83C\uDF8E", // 🎎 Japanese dolls
            "\uD83C\uDF8F", // 🎏 Carp streamer
            "\uD83C\uDF90", // 🎐 Wind chime
            "\uD83C\uDF91", // 🎑 Moon viewing ceremony
            "\uD83C\uDF92", // 🎒 Backpack
            "\uD83C\uDF93", // 🎓 Graduation cap
            "\uD83C\uDFA4", // 🎤 Microphone
            "\uD83C\uDFA5", // 🎥 Movie camera
            "\uD83C\uDFA6", // 🎦 Cinema
            "\uD83C\uDFA7", // 🎧 Headphone
            "\uD83C\uDFA9", // 🎩 Top hat
            "\uD83C\uDFAA", // 🎪 Circus tent
            "\uD83C\uDFAB", // 🎫 Ticket
            "\uD83C\uDFAC", // 🎬 Clapper board
            "\uD83C\uDFAF", // 🎯 Direct hit
            "\uD83C\uDFB0", // 🎰 Slot machine
            "\uD83C\uDFB1", // 🎱 Pool 8 ball
            "\uD83C\uDFB2", // 🎲 Game die
            "\uD83C\uDFB3", // 🎳 Bowling
            "\uD83C\uDFB4", // 🎴 Flower playing cards
            "\uD83C\uDFB5", // 🎵 Musical note
            "\uD83C\uDFB6", // 🎶 Musical notes
            "\uD83C\uDFBC", // 🎼 Musical score
            "\uD83C\uDFBD", // 🎽 Running shirt with sash
            "\uD83C\uDFBE", // 🎾 Tennis
            "\uD83C\uDFBF", // 🎿 Skis
            "\uD83C\uDFC0", // 🏀 Basketball
            "\uD83C\uDFC1", // 🏁 Chequered flag
            "\uD83C\uDFC2", // 🏂 Snowboarder
            "\uD83C\uDFC3", // 🏃 Runner
            "\uD83C\uDFC4", // 🏄 Surfer
            "\uD83C\uDFC5", // 🏅 Sports medal
            "\uD83C\uDFC6", // 🏆 Trophy
            "\uD83C\uDFC7", // 🏇 Horse racing
            "\uD83C\uDFC8", // 🏈 American football
            "\uD83C\uDFC9", // 🏉 Rugby football
            "\uD83C\uDFCA", // 🏊 Swimmer
            "\uD83C\uDFCB\uFE0F", // 🏋️ Weight lifter
            "\uD83C\uDFCC\uFE0F", // 🏌️ Golfer
            "\uD83C\uDFCD\uFE0F", // 🏍️ Racing motorcycle
            "\uD83C\uDFCE\uFE0F", // 🏎️ Racing car
            "\uD83C\uDFCF", // 🏏 Cricket
            "\uD83C\uDFD0", // 🏐 Volleyball
            "\uD83C\uDFD1", // 🏑 Field hockey
            "\uD83C\uDFD2", // 🏒 Ice hockey
            "\uD83C\uDFD3", // 🏓 Table tennis
            "\uD83C\uDFD4", // 🏔️ Snow-capped mountain
            "\uD83C\uDFD5\uFE0F", // 🏕️ Camping
            "\uD83C\uDFD6\uFE0F", // 🏖️ Beach with umbrella
            "\uD83C\uDFD7\uFE0F", // 🏗️ Building construction
            "\uD83C\uDFD8\uFE0F", // 🏘️ House buildings
            "\uD83C\uDFD9\uFE0F", // 🏙️ Cityscape
            "\uD83C\uDFDA\uFE0F", // 🏚️ Derelict house
            "\uD83C\uDFDB\uFE0F", // 🏛️ Classical building
            "\uD83C\uDFDC\uFE0F", // 🏜️ Desert
            "\uD83C\uDFDD\uFE0F", // 🏝️ Desert island
            "\uD83C\uDFDE\uFE0F", // 🏞️ National park
            "\uD83C\uDFDF\uFE0F", // 🏟️ Stadium
            "\uD83C\uDFE0", // 🏠 House
            "\uD83C\uDFE1", // 🏡 House with garden
            "\uD83C\uDFE2", // 🏢 Office building
            "\uD83C\uDFE3", // 🏣 Japanese post office
            "\uD83C\uDFE4", // 🏤 European post office
            "\uD83C\uDFE5", // 🏥 Hospital
            "\uD83C\uDFE6", // 🏦 Bank
            "\uD83C\uDFE7", // 🏧 ATM sign
            "\uD83C\uDFE8", // 🏨 Hotel
            "\uD83C\uDFE9", // 🏩 Love hotel
            "\uD83C\uDFEA", // 🏪 Convenience store
            "\uD83C\uDFEB", // 🏫 School
            "\uD83C\uDFEC", // 🏬 Department store
            "\uD83C\uDFED", // 🏭 Factory
            "\uD83C\uDFEE", // 🏮 Izakaya lantern
            "\uD83C\uDFEF", // 🏯 Japanese castle
            "\uD83C\uDFF0", // 🏰 European castle
            "\uD83C\uDFF3\uFE0F", // 🏳️ White flag
            "\uD83C\uDFF4", // 🏴 Black flag
            "\uD83C\uDFF5\uFE0F", // 🏵️ Rosette
            "\uD83C\uDFF7\uFE0F", // 🏷️ Label
            "\uD83C\uDFF8", // 🏸 Badminton
            "\uD83C\uDFF9", // 🏹 Bow and arrow
            "\uD83C\uDFFA", // 🏺 Amphora
            "\uD83C\uDFFB", // 🏻 Light skin tone
            "\uD83C\uDFFC", // 🏼 Medium-light skin tone
            "\uD83C\uDFFD", // 🏽 Medium skin tone
            "\uD83C\uDFFE", // 🏾 Medium-dark skin tone
            "\uD83C\uDFFF"  // 🏿 Dark skin tone
        )

        // Opisi emoji-ja za prikaz u spinneru
        val emojiOpisi = listOf(
            "🍔 Hamburger",
            "🥤 Čaša sa slamkom",
            "📝 Beleške",
            "👕 Majica",
            "🧹 Metla",
            "📱 Mobilni telefon",
            "🛍️ Shopping torbe",
            "💼 Aktovka",
            "🍺 Čaša piva",
            "🌮 Tako",
            "🍪 Keks",
            "🍩 Krofna",
            "🍞 Hleb",
            "🍓 Jagoda",
            "🧀 Sir",
            "🔧 Ključ",
            "🚛 Kamion",
            "🚗 Automobil",
            "🎧 Slušalice",
            "📷 Kamera",
            "💻 Laptop",
            "📺 Televizor",
            "📦 Paket",
            "🛒 Kolica",
            "💰 Kesica novca",
            "💵 Novčanica",
            "➕ Plus",
            "➖ Minus",
            "✅ Tačno",
            "❌ Pogrešno",
            "⭕ Krug",
            "🔴 Crveni krug",
            "🟢 Zeleni krug",
            "🟡 Žuti krug",
            "🔵 Plavi krug",
            "🔶 Narandžasti dijamant",
            "🖥️ Desktop računar",
            "💎 Dragi kamen",
            "🔌 Električni utikač ",
            "🔋 Baterija",
            "⚙️ Zupčanik",
            "📧 Email",
            "📲 Mobilni sa strelicom",
            "📸 Kamera sa blicem",
            "👍 Palac gore",
            "👎 Palac dole",
            "🎉 Konfete",
            "🎁 Poklon",
            "🎂 Rođendanska torta",
            "💡 Sijalica",
            "🔨 Čekic",
            "🛠️ Čekic i ključ",
            "📚 Knjige",
            "📖 Otvorena knjiga",
            "👨‍💻 IT stručnjak (muški)",
            "👩‍💻 IT stručnjak (ženski)",
            "💻 Laptop",
            "📻 Radio",
            "🕹️ Džojstik",
            "💾 Disketa",
            "🖲️ Trackball",
            "🖨️ Štampač",
            "🖋️ Nalivpero",
            "🖌️ Kist",
            "🖍️ Bojica",
            "🤖 Robot",
            "🚀 Raketa",
            "🚨 Policijska svetla",
            "🛰️ Satelit",
            "🛸 Leteći tanjir",
            "⌚ Sat",
            "🔑 Ključ",
            "🗝️ Stari ključ",
            "💳 Kreditna kartica",
            "💸 Novac sa krilima",
            "👟 Patike",
            "🎭 Pozorišna maska",
            "🎨 Paleta za slikanje",
            "🎹 Klavir",
            "🎺 Truba",
            "🎻 Violina",
            "🥁 Bubanj",
            "🎷 Saksofon",
            "🎸 Gitara",
            "🔪 Nož",
            "🍱 Bento kutija",
            "🍲 Lonac hrane",
            "🍳 Kuvanje",
            "🍴 Viljuška i nož",
            "🍵 Čaj bez drške",
            "🍶 Sake",
            "🍷 Čaša vina",
            "🍸 Koktel",
            "🍹 Tropsko piće",
            "🍻 Čaše piva",
            "🍼 Bočica za bebe",
            "🍽️ Tanjir sa priborom",
            "🍾 Čaša šampanjca",
            "🍿 Kokice",
            "🎀 Mašna",
            "🎁 Poklon",
            "🎂 Rođendanska torta",
            "🎃 Bundeva za Noć veštice",
            "🎄 Božićna jelka",
            "🎅 Deda Mraz",
            "🎆 Vatromet",
            "🎇 Varalica",
            "🎈 Balon",
            "🎉 Konfete",
            "🎊 Konfeti balon",
            "🎋 Tanabata drvo",
            "🎌 Ukrštene zastave",
            "🎍 Borova dekoracija",
            "🎎 Japanske lutke",
            "🎏 Karp streamer",
            "🎐 Vetrobran",
            "🎑 Mese eva ceremonija",
            "🎒 Ranac",
            "🎓 Diplomka",
            "🎤 Mikrofon",
            "🎥 Filmska kamera",
            "🎦 Bioskop",
            "🎧 Slušalice",
            "🎩 Cilindar",
            "🎪 Cirkuski šator",
            "🎫 Karta",
            "🎬 Klaker tabla",
            "🎯 Pogađanje mete",
            "🎰 Automat",
            "🎱 Bilijar loptica",
            "🎲 Kocka",
            "🎳 Kuglanje",
            "🎴 Cveće karte",
            "🎵 Nota",
            "🎶 Notice",
            "🎼 Partitura",
            "🎽 Trkačka majica",
            "🎾 Tenis",
            "🎿 Skije",
            "🏀 Košarka",
            "🏁 Karirana zastava",
            "🏂 Snowboarder",
            "🏃 Trka ",
            "🏄 Surfer",
            "🏅 Sportska medalja",
            "🏆 Trofej",
            "🏇 Trke konja",
            "🏈 Američki fudbal",
            "🏉 Ragbi",
            "🏊 Pliva ",
            "🏋️ Dizač tegova",
            "🏌️ Golfer",
            "🏍️ Trka i motor",
            "🏎️ Trka i automobil",
            "🏏 Kriket",
            "🏐 Odbojka",
            "🏑 Hokej na travi",
            "🏒 Hokej na ledu",
            "🏓 Stoni tenis",
            "🏔️ Planina sa snegom",
            "🏕️ Kampovanje",
            "🏖️ Plaža sa suncobranom",
            "🏗️ Građevina",
            "🏘️ Kuće",
            "🏙️ Gradski pejzaž",
            "🏚️ Napuštena kuća",
            "🏛️ Klasična građevina",
            "🏜️ Pustinja",
            "🏝️ Pusto ostrvo",
            "🏞️ Nacionalni park",
            "🏟️ Stadion",
            "🏠 Kuća",
            "🏡 Kuća sa baštom",
            "🏢 Poslovna zgrada",
            "🏣 Japanska pošta",
            "🏤 Evropska pošta",
            "🏥 Bolnica",
            "🏦 Banka",
            "🏧 Bankomat",
            "🏨 Hotel",
            "🏩 Love hotel",
            "🏪 Prodavnica",
            "🏫 Škola",
            "🏬 Robna kuća",
            "🏭 Fabrika",
            "🏮 Izakaya lantern",
            "🏯 Japanski dvorac",
            "🏰 Evropski dvorac",
            "🏳️ Bela zastava",
            "🏴 Crna zastava",
            "🏵️ Rozeta",
            "🏷️ Etiketa",
            "🏸 Badminton",
            "🏹 Luk i strela",
            "🏺 Amfora",
            "🏻 Svetla boja kože",
            "🏼 Srednje-svetla boja kože",
            "🏽 Srednja boja kože",
            "🏾 Srednje-tamna boja kože",
            "🏿 Tamna boja kože"
        )

        // Lista boja
        val bojeList = listOf(
            "Zelena svetla" to R.color.green_light,
            "Zelena" to android.R.color.holo_green_light,
            "Zelena tamna" to android.R.color.holo_green_dark,
            "Plava svetla" to R.color.blue_light,
            "Plava" to android.R.color.holo_blue_light,
            "Plava tamna" to android.R.color.holo_blue_dark,
            "Narandžasta" to android.R.color.holo_orange_light,
            "Narandžasta tamna" to android.R.color.holo_orange_dark,
            "Ljubičasta" to android.R.color.holo_purple,
            "Ljubičasta svetla" to R.color.purple_light,
            "Crvena" to android.R.color.holo_red_light,
            "Crvena tamna" to android.R.color.holo_red_dark,
            "Siva" to android.R.color.darker_gray,
            "Žuta" to android.R.color.holo_orange_dark,
            "Tirkizna" to android.R.color.holo_blue_bright
        )

        // Postavi adaptere za spinner-e
        val emojiAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, emojiOpisi)
        val bojeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, bojeList.map { it.first })

        emojiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bojeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spEmoji.adapter = emojiAdapter
        spBoja.adapter = bojeAdapter

        AlertDialog.Builder(this)
            .setTitle("Dodaj novu kategoriju")
            .setView(dialogView)
            .setPositiveButton("Dodaj") { _, _ ->
                val naziv = etNaziv.text.toString().trim()
                val emojiIndex = spEmoji.selectedItemPosition
                val emoji = emojiList[emojiIndex] // VAŽNO: Uzmi Unicode iz liste
                val bojaResId = bojeList[spBoja.selectedItemPosition].second

                if (naziv.isEmpty()) {
                    Toast.makeText(this, "Unesite naziv kategorije", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Proveri da li kategorija već postoji
                if (customCategories.any { it.naziv == naziv }) {
                    Toast.makeText(this, "Kategorija već postoji", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Dodaj novu kategoriju
                val novaKategorija = Kategorija(emoji, naziv, bojaResId)
                customCategories.add(novaKategorija)

                // Sačuvaj i osveži
                saveCustomCategories()
                setupAdapter()

                Toast.makeText(this, "Kategorija '$naziv' dodata", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun showDeleteSingleCategoryDialog(kategorija: Kategorija) {
        AlertDialog.Builder(this)
            .setTitle("Obriši kategoriju")
            .setMessage("Da li ste sigurni da želite da obrišete '${kategorija.naziv}'?\n\nNapomena: Proizvodi u ovoj kategoriji neće biti obrisani.")
            .setPositiveButton("Obriši") { _, _ ->
                customCategories.remove(kategorija)
                saveCustomCategories()
                setupAdapter()

                Toast.makeText(this, "Kategorija '${kategorija.naziv}' obrisana", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun showDeleteMultipleCategoriesDialog() {
        if (customCategories.isEmpty()) {
            Toast.makeText(this, "Nema custom kategorija za brisanje", Toast.LENGTH_SHORT).show()
            return
        }

        val kategorijeNazivi = customCategories.map { it.naziv }.toTypedArray()
        val checkedItems = BooleanArray(customCategories.size) { false }

        AlertDialog.Builder(this)
            .setTitle("Obriši kategorije")
            .setMultiChoiceItems(kategorijeNazivi, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Obriši označene") { _, _ ->
                val kategorijeZaBrisanje = mutableListOf<Kategorija>()

                for (i in checkedItems.indices) {
                    if (checkedItems[i]) {
                        kategorijeZaBrisanje.add(customCategories[i])
                    }
                }

                if (kategorijeZaBrisanje.isNotEmpty()) {
                    customCategories.removeAll(kategorijeZaBrisanje)
                    saveCustomCategories()
                    setupAdapter()

                    Toast.makeText(this, "Obrisano ${kategorijeZaBrisanje.size} kategorija", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    /**
     * Dijalog za editovanje (zamenjivanje) originalne kategorije
     */
    private fun showEditCategoryDialog(oldKategorija: Kategorija) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null)
        val etNaziv = dialogView.findViewById<EditText>(R.id.etNazivKategorije)
        val spEmoji = dialogView.findViewById<Spinner>(R.id.spEmoji)
        val spBoja = dialogView.findViewById<Spinner>(R.id.spBoja)

        // Postavi postojeće vrednosti
        etNaziv.setText(oldKategorija.naziv)

        // Lista emoji-ja kao Unicode stringovi
        val emojiList = listOf(
            "\uD83C\uDF54", // 🍔 Hamburger
            "\uD83E\uDD64", // 🥤 Cup with straw
            "\uD83D\uDCDD", // 📝 Memo
            "\uD83D\uDC55", // 👕 T-Shirt
            "\uD83E\uDDF9", // 🧹 Broom
            "\uD83D\uDCF1", // 📱 Mobile phone
            "\uD83D\uDECD", // 🛍️ Shopping bags
            "\uD83D\uDCBC", // 💼 Briefcase
            "\uD83C\uDF7A", // 🍺 Beer mug
            "\uD83C\uDF2E", // 🌮 Taco
            "\uD83C\uDF6A", // 🍪 Cookie
            "\uD83C\uDF69", // 🍩 Doughnut
            "\uD83E\uDD5E", // 🍞 Bread
            "\uD83C\uDF53", // 🍓 Strawberry
            "\uD83E\uDDC0", // 🧀 Cheese wedge
            "\uD83D\uDD27", // 🔧 Wrench
            "\uD83D\uDE9B", // 🚛 Articulated lorry
            "\uD83D\uDE97", // 🚗 Automobile
            "\uD83C\uDFA7", // 🎧 Headphone
            "\uD83D\uDCF7", // 📷 Camera
            "\uD83D\uDCBB", // 💻 Laptop
            "\uD83D\uDCFA", // 📺 Television
            "\uD83D\uDCE6", // 📦 Package
            "\uD83D\uDED2", // 🛒 Shopping cart
            "\uD83D\uDCB0", // 💰 Money bag
            "\uD83D\uDCB5", // 💵 Dollar banknote
            "\u002B\uFE0F\u20E3", // ➕ Plus
            "\u2796", // ➖ Minus
            "\u2705", // ✅ Check mark
            "\u274C", // ❌ Cross mark
            "\u2B55", // ⭕ Hollow red circle
            "\uD83D\uDD34", // 🔴 Red circle
            "\uD83D\uDFE2", // 🟢 Green circle
            "\uD83D\uDFE1", // 🟡 Yellow circle
            "\uD83D\uDD35", // 🔵 Blue circle
            "\uD83D\uDD36"  // 🔶 Large orange diamond
        )

        // Opisi emoji-ja za prikaz u spinneru
        val emojiOpisi = listOf(
            "🍔 Hamburger",
            "🥤 Čaša sa slamkom",
            "📝 Beleške",
            "👕 Majica",
            "🧹 Metla",
            "📱 Mobilni telefon",
            "🛍️ Shopping torbe",
            "💼 Aktovka",
            "🍺 Čaša piva",
            "🌮 Tako",
            "🍪 Keks",
            "🍩 Krofna",
            "🍞 Hleb",
            "🍓 Jagoda",
            "🧀 Sir",
            "🔧 Ključ",
            "🚛 Kamion",
            "🚗 Automobil",
            "🎧 Slušalice",
            "📷 Kamera",
            "💻 Laptop",
            "📺 Televizor",
            "📦 Paket",
            "🛒 Kolica",
            "💰 Kesica novca",
            "💵 Novčanica",
            "➕ Plus",
            "➖ Minus",
            "✅ Tačno",
            "❌ Pogrešno",
            "⭕ Krug",
            "🔴 Crveni krug",
            "🟢 Zeleni krug",
            "🟡 Žuti krug",
            "🔵 Plavi krug",
            "🔶 Narandžasti dijamant"
        )
        // Lista boja
        val bojeList = listOf(
            "Zelena svetla" to R.color.green_light,
            "Zelena" to android.R.color.holo_green_light,
            "Zelena tamna" to android.R.color.holo_green_dark,
            "Plava svetla" to R.color.blue_light,
            "Plava" to android.R.color.holo_blue_light,
            "Plava tamna" to android.R.color.holo_blue_dark,
            "Narandžasta" to android.R.color.holo_orange_light,
            "Narandžasta tamna" to android.R.color.holo_orange_dark,
            "Ljubičasta" to android.R.color.holo_purple,
            "Ljubičasta svetla" to R.color.purple_light,
            "Crvena" to android.R.color.holo_red_light,
            "Crvena tamna" to android.R.color.holo_red_dark,
            "Siva" to android.R.color.darker_gray,
            "Žuta" to android.R.color.holo_orange_dark,
            "Tirkizna" to android.R.color.holo_blue_bright
        )

        // Postavi adaptere za spinner-e
        val emojiAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, emojiOpisi)
        val bojeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, bojeList.map { it.first })

        emojiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bojeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spEmoji.adapter = emojiAdapter
        spBoja.adapter = bojeAdapter

        // Selektuj trenutni emoji i boju
        val emojiPosition = emojiList.indexOf(oldKategorija.ikona).takeIf { it >= 0 } ?: 0
        spEmoji.setSelection(emojiPosition)

        // Pronađi indeks boje
        val bojaIndex = bojeList.indexOfFirst { it.second == oldKategorija.boja }
        if (bojaIndex >= 0) {
            spBoja.setSelection(bojaIndex)
        }

        AlertDialog.Builder(this)
            .setTitle("Zameni kategoriju '${oldKategorija.naziv}'")
            .setView(dialogView)
            .setPositiveButton("Zameni") { _, _ ->
                val noviNaziv = etNaziv.text.toString().trim()
                val emojiIndex = spEmoji.selectedItemPosition
                val noviEmoji = emojiList[emojiIndex] // VAŽNO: Uzmi Unicode iz liste
                val novaBoja = bojeList[spBoja.selectedItemPosition].second

                if (noviNaziv.isEmpty()) {
                    Toast.makeText(this, "Unesite naziv kategorije", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (customCategories.any { it.naziv == noviNaziv }) {
                    Toast.makeText(this, "Kategorija '$noviNaziv' već postoji", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (noviNaziv == oldKategorija.naziv &&
                    noviEmoji == oldKategorija.ikona &&
                    novaBoja == oldKategorija.boja) {
                    Toast.makeText(this, "Niste napravili nikakve promene", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // KREIRAJ NOVU KATEGORIJU
                val novaKategorija = Kategorija(noviEmoji, noviNaziv, novaBoja)
                customCategories.add(novaKategorija)

                // OBELEŽI STARU KATEGORIJU KAO ZAMENJENU
                val sharedPref = getSharedPreferences("replaced_categories", Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putString(oldKategorija.naziv, "$noviEmoji|$noviNaziv|$novaBoja")
                    apply()
                }

                // AŽURIRAJ SVE PROIZVODE
                val staraKategorijaPunNaziv = "${oldKategorija.ikona} ${oldKategorija.naziv}"
                val novaKategorijaPunNaziv = "$noviEmoji $noviNaziv"

                Log.d("EditCategory", "Stara kategorija: '$staraKategorijaPunNaziv'")
                Log.d("EditCategory", "Nova kategorija: '$novaKategorijaPunNaziv'")

                repository.sviProizvodi { sviProizvodi ->
                    var azuriraniProizvodi = 0
                    val proizvodiZaAzuriranje = mutableListOf<Proizvod>()

                    // Pronađi sve proizvode sa starom kategorijom
                    sviProizvodi.forEach { proizvod ->
                        Log.d("EditCategory", "Proveravam proizvod: ${proizvod.naziv}, kategorija: '${proizvod.kategorija}'")

                        // VAŽNO: Proveri da li proizvod ima TAČNU staru kategoriju
                        if (proizvod.kategorija == staraKategorijaPunNaziv) {
                            Log.d("EditCategory", "Pronađen proizvod za ažuriranje: ${proizvod.naziv}")
                            val azuriranProizvod = proizvod.copy(
                                kategorija = novaKategorijaPunNaziv
                            )
                            proizvodiZaAzuriranje.add(azuriranProizvod)
                            azuriraniProizvodi++
                        }

                        // DODATNO: Proveri i da li proizvod ima samo naziv bez emoji
                        val kategorijaBezEmoji = extractCategoryName(proizvod.kategorija)
                        if (kategorijaBezEmoji == oldKategorija.naziv && proizvod.kategorija != staraKategorijaPunNaziv) {
                            Log.d("EditCategory", "Pronađen proizvod sa nazivom bez emoji: ${proizvod.naziv}")
                            val azuriranProizvod = proizvod.copy(
                                kategorija = novaKategorijaPunNaziv
                            )
                            proizvodiZaAzuriranje.add(azuriranProizvod)
                            azuriraniProizvodi++
                        }
                    }

                    // Ažuriraj sve proizvode
                    if (proizvodiZaAzuriranje.isNotEmpty()) {
                        var uspešnoAžurirano = 0
                        proizvodiZaAzuriranje.forEach { azuriranProizvod ->
                            repository.azurirajProizvod(azuriranProizvod) { success ->
                                if (success) {
                                    uspešnoAžurirano++
                                    Log.d("EditCategory",
                                        "Ažuriran proizvod: ${azuriranProizvod.naziv} " +
                                                "($staraKategorijaPunNaziv -> $novaKategorijaPunNaziv)")
                                }

                                // Kada su svi ažurirani, osveži prikaz
                                if (uspešnoAžurirano == proizvodiZaAzuriranje.size) {
                                    runOnUiThread {
                                        // Sačuvaj custom kategorije
                                        saveCustomCategories()

                                        // Osveži prikaz
                                        setupAdapter()

                                        // Prikaži rezultat
                                        val poruka = "Kategorija '${oldKategorija.naziv}' zamenjena sa '$noviNaziv'. " +
                                                "$azuriraniProizvodi proizvoda ažurirano."
                                        Toast.makeText(this, poruka, Toast.LENGTH_LONG).show()

                                        // Pošalji LOCAL broadcast za osvežavanje MainActivity
                                        val intent = Intent("KATEGORIJA_PROMENJENA")
                                        intent.putExtra("STARA_KATEGORIJA", staraKategorijaPunNaziv)
                                        intent.putExtra("NOVA_KATEGORIJA", novaKategorijaPunNaziv)
                                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                                    }
                                }
                            }
                        }
                    } else {
                        runOnUiThread {
                            // Sačuvaj custom kategorije
                            saveCustomCategories()

                            // Nema proizvoda za ažuriranje
                            setupAdapter()
                            Toast.makeText(this,
                                "Kategorija '${oldKategorija.naziv}' zamenjena sa '$noviNaziv'. " +
                                        "Nema proizvoda za ažuriranje.",
                                Toast.LENGTH_LONG).show()

                            // Pošalji LOCAL broadcast za osvežavanje MainActivity
                            val intent = Intent("KATEGORIJA_PROMENJENA")
                            intent.putExtra("STARA_KATEGORIJA", staraKategorijaPunNaziv)
                            intent.putExtra("NOVA_KATEGORIJA", novaKategorijaPunNaziv)
                            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                        }
                    }
                }
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }
    /**
     * Pomoćna metoda za ekstrakciju naziva bez emoji
     */
    private fun extractCategoryName(fullCategory: String): String {
        // Ukloni emoji i dodatni whitespace sa početka
        return fullCategory.replace(Regex("^[\\p{So}\\s]+"), "").trim()
    }

    // Pomoćna metoda za ekstrakciju naziva bez emoji
    private fun extractNameWithoutEmoji(text: String): String {
        // Ukloni sve emoji i whitespace sa početka
        return text.replace(Regex("^[\\p{So}\\s]+"), "").trim()
    }

    // Pomoćna metoda za ekstrakciju emoji iz teksta
    private fun extractEmoji(fullCategory: String): String {
        // Ekstraktuj emoji iz početka stringa
        val match = Regex("([\\p{So}])").find(fullCategory)
        return match?.value ?: "📁" // Podrazumevana ikona
    }
}