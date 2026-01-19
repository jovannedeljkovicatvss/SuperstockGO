package com.jovannedeljkovic.superstockgo

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var tvStatus: TextView
    private lateinit var btnContinue: Button
    private lateinit var btnLogout: Button
    private lateinit var tvSwitchUser: TextView

    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var repository: Repository
    private lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        firebaseHelper = FirebaseHelper(this)
        repository = Repository(this)
        sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Poveži view-ove
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        tvStatus = findViewById(R.id.tvStatus)
        btnContinue = findViewById(R.id.btnContinue)
        btnLogout = findViewById(R.id.btnLogout)
        tvSwitchUser = findViewById(R.id.tvSwitchUser)

        // Proveri da li treba forsirati logout (ako je korisnik izabrao "Promeni korisnika")
        val forceLogout = sharedPref.getBoolean("force_logout", false)
        if (forceLogout) {
            // Eksplicitno logout
            Firebase.auth.signOut()
            sharedPref.edit().putBoolean("force_logout", false).apply()
            Log.d("LoginActivity", "Forced logout izvršen")
        }

        // Proveri da li treba prikazati login formu odmah
        if (intent.getBooleanExtra("SHOW_LOGIN_FORM", false) || forceLogout) {
            showLoginForm()
            tvStatus.text = "Prijavite se drugim nalogom"
        } else {
            checkCurrentUser()
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Unesite email i lozinku", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            tvStatus.text = "Prijava u toku..."

            firebaseHelper.login(email, password) { success, message ->
                btnLogin.isEnabled = true

                if (success) {
                    tvStatus.text = "Uspešna prijava!"
                    Toast.makeText(this, "Dobrodošli!", Toast.LENGTH_SHORT).show()

                    // Sačuvaj email za kasnije
                    saveUserEmail(email)

                    // Migriraj stare podatke za ovog korisnika
                    repository.migrirajPodatkeZaTrenutnogKorisnika()

                    // Prebaci na glavnu app
                    startMainApp()
                } else {
                    tvStatus.text = "Greška pri prijavi"
                    Toast.makeText(this, "Greška: $message", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Unesite email i lozinku", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Lozinka mora imati najmanje 6 karaktera", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            tvStatus.text = "Registracija u toku..."

            firebaseHelper.register(email, password, "user") { success, message ->
                btnRegister.isEnabled = true

                if (success) {
                    tvStatus.text = "Registracija uspešna!"
                    Toast.makeText(this, "Nalog kreiran. Sada se možete prijaviti.", Toast.LENGTH_LONG).show()

                    // Sačuvaj email za kasnije
                    saveUserEmail(email)

                    // Automatski login nakon registracije
                    firebaseHelper.login(email, password) { loginSuccess, loginMessage ->
                        if (loginSuccess) {
                            // Migriraj stare podatke za novog korisnika
                            repository.migrirajPodatkeZaTrenutnogKorisnika()
                            startMainApp()
                        } else {
                            Toast.makeText(this, "Auto-login greška: $loginMessage", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    tvStatus.text = "Greška pri registraciji"
                    Toast.makeText(this, "Greška: $message", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnContinue.setOnClickListener {
            startMainApp()
        }

        btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        tvSwitchUser.setOnClickListener {
            showSwitchUserDialog()
        }
    }

    /**
     * Prikazuje login formu (sakriva continue/logout dugmad)
     */
    private fun showLoginForm() {
        etEmail.visibility = EditText.VISIBLE
        etPassword.visibility = EditText.VISIBLE
        btnLogin.visibility = Button.VISIBLE
        btnRegister.visibility = Button.VISIBLE
        btnContinue.visibility = Button.GONE
        btnLogout.visibility = Button.GONE
        tvSwitchUser.visibility = TextView.GONE

        // Obriši polja
        etEmail.text.clear()
        etPassword.text.clear()
        etEmail.requestFocus()
    }

    /**
     * Proverava trenutnog korisnika i podešava UI
     */
    private fun checkCurrentUser() {
        val currentUser = firebaseHelper.getCurrentUser()
        if (currentUser != null) {
            tvStatus.text = "Prijavljen: ${currentUser.email}"

            // Prikaži opciju za nastavak i logout
            btnContinue.text = "NASTAVI KAO ${currentUser.email}"
            btnContinue.visibility = Button.VISIBLE
            btnLogout.visibility = Button.VISIBLE
            tvSwitchUser.visibility = TextView.VISIBLE

            // Sakrij login/register polja
            etEmail.visibility = EditText.GONE
            etPassword.visibility = EditText.GONE
            btnLogin.visibility = Button.GONE
            btnRegister.visibility = Button.GONE

            // Migriraj stare podatke
            repository.migrirajPodatkeZaTrenutnogKorisnika()

            // Sačuvaj email za kasnije
            saveUserEmail(currentUser.email ?: "")
        } else {
            tvStatus.text = "Niste prijavljeni"
            showLoginForm()
        }
    }

    /**
     * Dijalog za logout sa opcijama
     */
    private fun showLogoutDialog() {
        val currentUser = firebaseHelper.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Niste prijavljeni", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf("Odjavi se", "Promeni korisnika", "Otkaži")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Opcije za ${currentUser.email}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Odjavi se
                        performLogout(false, "Da li ste sigurni da želite da se odjavite?\n\nLokalni podaci će ostati sačuvani.")
                    }
                    1 -> { // Promeni korisnika
                        performLogout(true, "Da li želite da se odjavite i prijavite drugim korisnikom?\n\nLokalni podaci će ostati sačuvani.")
                    }
                    // 2 -> Otkaži (ne radi ništa)
                }
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    /**
     * Dijalog za promenu korisnika
     */
    private fun showSwitchUserDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Promena korisnika")
            .setMessage("Da li želite da se prijavite drugim korisnikom?\n\nTrenutni lokalni podaci će ostati sačuvani.")
            .setPositiveButton("Da, prijavi drugog") { _, _ ->
                // Postavi flag za forsirani logout
                sharedPref.edit().putBoolean("force_logout", true).apply()

                // Eksplicitno logout
                Firebase.auth.signOut()

                // Prikaži login formu
                showLoginForm()
                tvStatus.text = "Prijavite se drugim nalogom"

                Toast.makeText(this, "Prijavite se drugim nalogom", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Ne", null)
            .show()
    }

    /**
     * Izvršava logout sa potvrdom
     */
    private fun performLogout(changeUser: Boolean, message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (changeUser) "Promena korisnika" else "Odjava")
            .setMessage(message)
            .setPositiveButton("Da") { _, _ ->
                // Ako želi promenu korisnika, postavi flag
                if (changeUser) {
                    sharedPref.edit().putBoolean("force_logout", true).apply()
                }

                // Firebase logout
                Firebase.auth.signOut()

                // Obriši shared preferences za trenutnog korisnika
                clearCurrentUserData()

                Toast.makeText(this,
                    if (changeUser) "Uspešno odjavljeni. Prijavite se drugim nalogom."
                    else "Uspešno odjavljeni",
                    Toast.LENGTH_SHORT).show()

                if (changeUser) {
                    // Prikaži login formu za drugog korisnika
                    showLoginForm()
                    tvStatus.text = "Prijavite se drugim nalogom"
                } else {
                    // Vrati na početno stanje
                    checkCurrentUser()
                }
            }
            .setNegativeButton("Ne", null)
            .show()
    }

    /**
     * Briše podatke trenutnog korisnika
     */
    private fun clearCurrentUserData() {
        try {
            val currentUser = firebaseHelper.getCurrentUser()
            val userId = currentUser?.uid ?: return

            // Obriši shared preferences za ovog korisnika
            val prefNames = listOf(
                "categories_$userId",
                "replaced_categories",
                "user_$userId"
            )

            prefNames.forEach { prefName ->
                getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()
            }

            Log.d("LoginActivity", "Podaci obrisani za user: $userId")
        } catch (e: Exception) {
            Log.e("LoginActivity", "Greška pri brisanju podataka: ${e.message}")
        }
    }

    /**
     * Sačuva email korisnika za kasnije korišćenje
     */
    private fun saveUserEmail(email: String) {
        if (email.isNotEmpty()) {
            // Sačuvaj u listu prethodnih naloga
            val savedAccounts = sharedPref.getStringSet("saved_accounts", mutableSetOf()) ?: mutableSetOf()
            savedAccounts.add(email)
            sharedPref.edit().putStringSet("saved_accounts", savedAccounts).apply()

            // Sačuvaj poslednji korišćeni email
            sharedPref.edit().putString("last_email", email).apply()

            Log.d("LoginActivity", "Email sačuvan: $email")
        }
    }

    private fun startMainApp() {
        val intent = Intent(this, KategorijeActivity::class.java)
        startActivity(intent)
        finish() // Zatvori LoginActivity
    }

    override fun onStart() {
        super.onStart()
        // Proveri status pri svakom otvaranju (ako nismo već u onCreate)
        if (!::etEmail.isInitialized) {
            return
        }

        val forceLogout = sharedPref.getBoolean("force_logout", false)
        if (!forceLogout) {
            checkCurrentUser()
        }
    }
}