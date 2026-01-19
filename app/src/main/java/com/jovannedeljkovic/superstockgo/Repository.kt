package com.jovannedeljkovic.superstockgo

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Exception
import kotlinx.coroutines.delay

class Repository(private val context: Context) {
    private val localDbHelper = DBHelper(context)
    private val firebaseHelper = FirebaseHelper(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val TAG = "Repository"

    fun getCurrentUserId(): String {
        val currentUser = firebaseHelper.getCurrentUser()
        return currentUser?.uid ?: "offline_user_${System.currentTimeMillis()}"
    }

    fun getCurrentUserEmail(): String? {
        return firebaseHelper.getCurrentUser()?.email
    }

    fun isUserLoggedIn(): Boolean {
        return firebaseHelper.getCurrentUser() != null
    }

    fun migrirajPodatkeZaTrenutnogKorisnika() {
        coroutineScope.launch {
            try {
                val userId = getCurrentUserId()
                if (userId != "unknown" && !userId.startsWith("offline_user")) {
                    localDbHelper.migrirajStarePodatke(userId)
                    Log.d(TAG, "Migracija završena za user: $userId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Greška pri migraciji: ${e.message}")
            }
        }
    }

    // ========== CRUD OPERACIJE - BEZ FIREBASE ==========

    fun dodajProizvod(proizvod: Proizvod, onComplete: (Boolean, String?) -> Unit) {
        coroutineScope.launch {
            try {
                val userId = getCurrentUserId()

                // Proveri duplikate
                val sviProizvodi = localDbHelper.sviProizvodi(userId)
                val postoji = sviProizvodi.any {
                    it.naziv == proizvod.naziv && it.kategorija == proizvod.kategorija
                }

                if (postoji) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Proizvod već postoji")
                    }
                    return@launch
                }

                val localId = localDbHelper.dodajProizvod(proizvod, userId)

                withContext(Dispatchers.Main) {
                    if (localId > 0) {
                        onComplete(true, "sačuvan")
                        // LOCAL broadcast više ne šaljemo odavde
                        // Pozivajuća aktivnost će poslati LOCAL broadcast
                    } else {
                        onComplete(false, "greška pri čuvanju")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Greška: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete(false, "sistemska greška")
                }
            }
        }
    }

    fun sviProizvodi(onComplete: (List<Proizvod>) -> Unit) {
        coroutineScope.launch {
            try {
                val userId = getCurrentUserId()
                val proizvodi = withContext(Dispatchers.IO) {
                    localDbHelper.sviProizvodi(userId)
                }

                // POPRAVI KATEGORIJE PRE NEGO ŠTO IH VRATIŠ
                val popravljeniProizvodi = proizvodi.map { proizvod ->
                    val popravljenaKategorija = proizvod.kategorija
                        .replace("Pie", "Pi e")
                        .replace("PI E", "Pi e")
                        .replace("PICE", "Pi e")
                        .replace("Pice", "Pi e") // za svaki slučaj

                    proizvod.copy(kategorija = popravljenaKategorija)
                }

                withContext(Dispatchers.Main) {
                    onComplete(popravljeniProizvodi)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Greška pri u itavanju: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete(emptyList())
                }
            }
        }
    }

    // VAŽNO: Ova metoda je ključna - UKLONJEN FIREBASE
    fun azurirajProizvod(proizvod: Proizvod, onComplete: (Boolean) -> Unit) {
        coroutineScope.launch {
            try {
                val userId = getCurrentUserId()
                val success = localDbHelper.azurirajProizvod(proizvod, userId) > 0

                // SAMO LOKALNO - NEMA FIREBASE POZIVA!
                withContext(Dispatchers.Main) {
                    onComplete(success)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Greška pri ažuriranju: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun obrisiProizvod(proizvod: Proizvod, onComplete: (Boolean) -> Unit) {
        coroutineScope.launch {
            try {
                val userId = getCurrentUserId()
                // SAMO LOKALNO - NEMA FIREBASE!
                val success = localDbHelper.obrisiProizvod(proizvod.id, userId) > 0

                withContext(Dispatchers.Main) {
                    onComplete(success)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Greška pri brisanju: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun pronadjiProizvod(id: Int, onComplete: (Proizvod?) -> Unit) {
        coroutineScope.launch {
            try {
                val userId = getCurrentUserId()
                val sviProizvodi = localDbHelper.sviProizvodi(userId)
                val proizvod = sviProizvodi.find { it.id == id }
                withContext(Dispatchers.Main) {
                    onComplete(proizvod)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Greška pri pronalaženju: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete(null)
                }
            }
        }
    }

    suspend fun getProizvodiSuspended(): List<Proizvod> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId()
            localDbHelper.sviProizvodi(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Greška u getProizvodiSuspended: ${e.message}")
            emptyList()
        }
    }

    fun brojProizvodaZaKorisnika(onComplete: (Int) -> Unit) {
        coroutineScope.launch {
            try {
                val userId = getCurrentUserId()
                val broj = localDbHelper.brojProizvodaPoKorisniku(userId)
                withContext(Dispatchers.Main) {
                    onComplete(broj)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Greška pri brojanju proizvoda: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete(0)
                }
            }
        }
    }

    fun proveriIIspraviDuplikate(onComplete: (Int) -> Unit) {
        coroutineScope.launch {
            try {
                val userId = getCurrentUserId()
                val sviProizvodi = withContext(Dispatchers.IO) {
                    localDbHelper.sviProizvodi(userId)
                }

                if (sviProizvodi.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onComplete(0)
                    }
                    return@launch
                }

                val mapaProizvoda = mutableMapOf<String, MutableList<Proizvod>>()
                val duplikatiZaBrisanje = mutableListOf<Proizvod>()

                sviProizvodi.forEach { proizvod ->
                    val key = proizvod.getUniqueKey()
                    val lista = mapaProizvoda.getOrPut(key) { mutableListOf() }
                    lista.add(proizvod)

                    if (lista.size > 1) {
                        duplikatiZaBrisanje.addAll(lista.drop(1))
                    }
                }

                var uspesnoObrisano = 0
                var greske = 0

                if (duplikatiZaBrisanje.isNotEmpty()) {
                    duplikatiZaBrisanje.forEach { duplikat ->
                        try {
                            val result = withContext(Dispatchers.IO) {
                                localDbHelper.obrisiProizvod(duplikat.id, userId)
                            }

                            if (result > 0) {
                                uspesnoObrisano++
                                Log.d("Repository", "✅ Obrisan duplikat: '${duplikat.naziv}'")
                            } else {
                                greske++
                                Log.e("Repository", "❌ Nije uspelo brisanje: '${duplikat.naziv}'")
                            }
                        } catch (e: Exception) {
                            greske++
                            Log.e(TAG, "Exception pri brisanju '${duplikat.naziv}': ${e.message}")
                        }
                        delay(50)
                    }
                }

                withContext(Dispatchers.Main) {
                    onComplete(uspesnoObrisano)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Greška pri skeniranju duplikata: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onComplete(0)
                }
            }
        }
    }
}