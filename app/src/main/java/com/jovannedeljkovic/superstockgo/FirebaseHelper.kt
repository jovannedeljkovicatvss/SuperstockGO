package com.jovannedeljkovic.superstockgo

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FirebaseHelper(private val context: Context) {

    private val auth: FirebaseAuth = Firebase.auth
    private val database: FirebaseDatabase = Firebase.database("https://superstockgo-default-rtdb.europe-west1.firebasedatabase.app")
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getDatabase(): FirebaseDatabase {
        return database
    }

    fun getAuth(): FirebaseAuth {
        return auth
    }

    companion object {
        const val PATH_INVENTORY = "inventory"
        const val PATH_USERS = "users"
    }

    // Login sistem
    fun login(email: String, password: String, onComplete: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    onComplete(true, userId)
                } else {
                    onComplete(false, task.exception?.message)
                }
            }
    }

    // Register
    fun register(email: String, password: String, role: String = "user", onComplete: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    val userRef = database.reference.child(PATH_USERS).child(userId!!)
                    userRef.setValue(mapOf(
                        "email" to email,
                        "role" to role,
                        "createdAt" to System.currentTimeMillis()
                    ))
                        .addOnSuccessListener {
                            onComplete(true, userId)
                        }
                        .addOnFailureListener {
                            onComplete(false, it.message)
                        }
                } else {
                    onComplete(false, task.exception?.message)
                }
            }
    }

    fun logout() {
        auth.signOut()
    }

    fun getCurrentUser(): com.google.firebase.auth.FirebaseUser? {
        return auth.currentUser
    }

    // Dodajte ovu metodu u FirebaseHelper klasu:
    suspend fun backupCategoriesToCloud(categories: List<Kategorija>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid ?: return@withContext false

                val categoriesRef = database.reference.child("categories").child(userId)

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
                Log.d("FirebaseHelper", "✅ Kategorije sačuvane u Cloud: ${categories.size}")
                true
            } catch (e: Exception) {
                Log.e("FirebaseHelper", "❌ Greška pri čuvanju kategorija: ${e.message}")
                false
            }
        }
    }

    fun restoreCategoriesFromCloud(onComplete: (List<Kategorija>) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid ?: return@launch onComplete(emptyList())

                val categoriesRef = database.reference.child("categories").child(userId)
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

                withContext(Dispatchers.Main) {
                    onComplete(kategorije)
                    Log.d("FirebaseHelper", "✅ Učitano ${kategorije.size} kategorija iz Cloud-a")
                }
            } catch (e: Exception) {
                Log.e("FirebaseHelper", "Greška pri učitavanju kategorija: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete(emptyList())
                }
            }
        }
    }
    // Cloud backup
    suspend fun backupToCloud(proizvodi: List<Proizvod>): Boolean {
        return withTimeout(30000) {
            try {
                Log.d("FirebaseHelper", "=== BACKUP START ===")
                Log.d("FirebaseHelper", "Broj proizvoda: ${proizvodi.size}")

                val userId = auth.currentUser?.uid
                if (userId == null) {
                    Log.e("FirebaseHelper", "❌ User ID je NULL")
                    return@withTimeout false
                }

                Log.d("FirebaseHelper", "✅ User ID: $userId")

                // Proveri duplikate pre slanja
                val jedinstveniProizvodi = mutableListOf<Proizvod>()
                val naziviSet = mutableSetOf<String>()

                proizvodi.forEach { proizvod ->
                    val kljuc = "${proizvod.naziv}|${proizvod.kategorija}"
                    if (!naziviSet.contains(kljuc)) {
                        naziviSet.add(kljuc)
                        jedinstveniProizvodi.add(proizvod)
                    } else {
                        Log.w("FirebaseHelper", "Preskačem duplikat: ${proizvod.naziv}")
                    }
                }

                Log.d("FirebaseHelper", "Original: ${proizvodi.size}, Bez duplikata: ${jedinstveniProizvodi.size}")

                // Kreiraj putanju
                val userInventoryRef = database.reference.child("inventory").child(userId)

                // Obriši stari inventar
                Log.d("FirebaseHelper", "Brišem stari inventar...")
                try {
                    userInventoryRef.removeValue().await()
                    Log.d("FirebaseHelper", "✅ Stari inventar obrisan")
                } catch (e: Exception) {
                    Log.e("FirebaseHelper", "⚠️ Greška pri brisanju: ${e.message}")
                }

                // Dodaj sve proizvode
                Log.d("FirebaseHelper", "Dodajem ${jedinstveniProizvodi.size} proizvoda...")

                var uspešnoDodato = 0
                for ((index, proizvod) in jedinstveniProizvodi.withIndex()) {
                    try {
                        val proizvodId = proizvod.id.toString()
                        if (proizvodId == "0") {
                            // Ako je ID 0, generiši novi
                            val newId = (System.currentTimeMillis() % 1000000).toInt() + index + 1
                            val proizvodMap = mapOf(
                                "id" to newId,
                                "naziv" to proizvod.naziv,
                                "kategorija" to proizvod.kategorija,
                                "kolicina" to proizvod.kolicina,
                                "lastUpdated" to System.currentTimeMillis()
                            )
                            userInventoryRef.child(newId.toString()).setValue(proizvodMap).await()
                        } else {
                            val proizvodMap = mapOf(
                                "id" to proizvod.id,
                                "naziv" to proizvod.naziv,
                                "kategorija" to proizvod.kategorija,
                                "kolicina" to proizvod.kolicina,
                                "lastUpdated" to System.currentTimeMillis()
                            )
                            userInventoryRef.child(proizvodId).setValue(proizvodMap).await()
                        }

                        uspešnoDodato++
                        Log.d("FirebaseHelper", "✅ ${proizvod.naziv} dodat")

                    } catch (e: Exception) {
                        Log.e("FirebaseHelper", "❌ Greška pri dodavanju ${proizvod.naziv}: ${e.message}")
                    }

                    // Mali delay između proizvoda
                    kotlinx.coroutines.delay(50)
                }

                Log.d("FirebaseHelper", "=== BACKUP COMPLETE ===")
                Log.d("FirebaseHelper", "Uspešno dodato $uspešnoDodato/${jedinstveniProizvodi.size} proizvoda")

                return@withTimeout uspešnoDodato > 0

            } catch (e: Exception) {
                Log.e("FirebaseHelper", "❌ EXCEPTION u backupToCloud: ${e.message}", e)
                false
            }
        }
    }
    fun backupCategoriesToCloud(categories: List<Kategorija>, onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onComplete(false)

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val categoriesRef = database.reference.child("categories").child(userId)

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

                withContext(Dispatchers.Main) {
                    onComplete(true)
                    Log.d("FirebaseHelper", "Kategorije sačuvane u Cloud: ${categories.size}")
                }
            } catch (e: Exception) {
                Log.e("FirebaseHelper", "Greška pri čuvanju kategorija: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }


    fun addProductToCloud(proizvod: Proizvod, onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onComplete(false)

        val proizvodRef = database.reference
            .child(PATH_INVENTORY)
            .child(userId)
            .child(proizvod.id.toString())

        val proizvodMap = mapOf(
            "id" to proizvod.id,
            "naziv" to proizvod.naziv,
            "kategorija" to proizvod.kategorija,
            "kolicina" to proizvod.kolicina,
            "lastUpdated" to System.currentTimeMillis()
        )

        proizvodRef.setValue(proizvodMap)
            .addOnSuccessListener {
                onComplete(true)
                Log.d("FirebaseHelper", "Proizvod ${proizvod.naziv} dodat u cloud")
            }
            .addOnFailureListener {
                onComplete(false)
                Log.e("FirebaseHelper", "Greška pri dodavanju proizvoda u cloud: ${it.message}")
            }
    }

    suspend fun restoreFromCloud(): List<Proizvod> {
        return try {
            val userId = auth.currentUser?.uid ?: return emptyList()
            val snapshot = database.reference.child(PATH_INVENTORY).child(userId).get().await()

            val proizvodi = mutableListOf<Proizvod>()
            val naziviSet = mutableSetOf<String>() // Za praćenje duplikata
            var duplikati = 0

            for (child in snapshot.children) {
                val id = child.child("id").getValue(Int::class.java) ?: 0
                val naziv = child.child("naziv").getValue(String::class.java) ?: ""
                val kategorija = child.child("kategorija").getValue(String::class.java) ?: ""
                val kolicina = child.child("kolicina").getValue(Int::class.java) ?: 0

                val proizvod = Proizvod(id, naziv, kategorija, kolicina)
                val uniqueKey = proizvod.getUniqueKey() // KORISTIMO NOVU METODU

                // Proveri duplikate
                if (!naziviSet.contains(uniqueKey)) {
                    naziviSet.add(uniqueKey)
                    proizvodi.add(proizvod)
                    Log.d("FirebaseHelper", "✓ Učitano: $naziv (ID: $id, Key: $uniqueKey)")
                } else {
                    duplikati++
                    Log.w("FirebaseHelper", "✗ Duplikat preskočen: $naziv")
                }
            }

            Log.d("FirebaseHelper", "Učitano ${proizvodi.size} jedinstvenih proizvoda iz Cloud-a")
            Log.d("FirebaseHelper", "Preskočeno $duplikati duplikata")
            proizvodi
        } catch (e: Exception) {
            Log.e("FirebaseHelper", "Greška pri restore: ${e.message}")
            emptyList()
        }
    }

    // Pojednostavljena metoda za čuvanje
    fun saveProductSimple(proizvod: Proizvod, onComplete: (Boolean) -> Unit) {
        try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                onComplete(false)
                return
            }

            val ref = database.reference.child("inventory").child(userId)

            val proizvodMap = hashMapOf(
                "id" to proizvod.id,
                "naziv" to proizvod.naziv,
                "kategorija" to proizvod.kategorija,
                "kolicina" to proizvod.kolicina,
                "lastUpdated" to System.currentTimeMillis()
            )

            ref.child(proizvod.id.toString()).setValue(proizvodMap)
                .addOnSuccessListener {
                    Log.d("FirebaseHelper", "Proizvod sačuvan: ${proizvod.naziv}")
                    onComplete(true)
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseHelper", "Greška: ${e.message}")
                    onComplete(false)
                }

        } catch (e: Exception) {
            Log.e("FirebaseHelper", "Greška: ${e.message}")
            onComplete(false)
        }
    }

    fun loadProductsSimple(onComplete: (List<Proizvod>) -> Unit) {
        try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                onComplete(emptyList())
                return
            }

            val ref = database.reference.child("inventory").child(userId)

            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val proizvodi = mutableListOf<Proizvod>()

                    for (child in snapshot.children) {
                        val proizvod = Proizvod(
                            id = child.child("id").getValue(Int::class.java) ?: 0,
                            naziv = child.child("naziv").getValue(String::class.java) ?: "",
                            kategorija = child.child("kategorija").getValue(String::class.java) ?: "",
                            kolicina = child.child("kolicina").getValue(Int::class.java) ?: 0
                        )
                        proizvodi.add(proizvod)
                    }

                    Log.d("FirebaseHelper", "Učitano ${proizvodi.size} proizvoda")
                    onComplete(proizvodi)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseHelper", "Greška: ${error.message}")
                    onComplete(emptyList())
                }
            })

        } catch (e: Exception) {
            Log.e("FirebaseHelper", "Greška: ${e.message}")
            onComplete(emptyList())
        }
    }

    // Metoda za brisanje proizvoda iz Firebase
    fun obrisiProizvodIzFirebase(proizvod: Proizvod, onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onComplete(false)

        Log.d("FirebaseHelper", "Brišem proizvod iz Firebase: ${proizvod.naziv} (ID: ${proizvod.id})")

        // VAŽNO: Proveri da li koristiš pravi put
        val proizvodRef = database.reference
            .child(PATH_INVENTORY)
            .child(userId)
            .child(proizvod.id.toString())  // OVO JE KLJUČNO: .child(proizvod.id.toString())

        proizvodRef.removeValue()
            .addOnSuccessListener {
                Log.d("FirebaseHelper", "✅ Proizvod obrisan iz Firebase: ${proizvod.naziv}")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseHelper", "❌ Greška pri brisanju iz Firebase: ${e.message}")
                onComplete(false)
            }
    }

    fun isFirebaseConfigured(): Boolean {
        return try {
            // Proveri da li Firebase inicijalizovan
            Firebase.auth.currentUser != null || Firebase.database.reference.key != null
        } catch (e: Exception) {
            false
        }
    }

    // Metoda za direktno dobijanje database reference
    fun getDatabaseReference(): DatabaseReference {
        return database.reference
    }

    // ===== NOVE METODE ZA CloudSyncActivity =====

    fun backupToCloudSimple(proizvodi: List<Proizvod>, onComplete: (Boolean, String) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val success = backupToCloud(proizvodi)
                withContext(Dispatchers.Main) {
                    if (success) {
                        onComplete(true, "Backup uspešan! Sačuvano ${proizvodi.size} proizvoda.")
                    } else {
                        onComplete(false, "Backup nije uspeo.")
                    }
                }
            } catch (e: Exception) {
                Log.e("FirebaseHelper", "Greška u backupToCloudSimple: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete(false, "Greška: ${e.message}")
                }
            }
        }
    }

    fun restoreFromCloudSimple(onComplete: (List<Proizvod>) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val proizvodi = restoreFromCloud()
                withContext(Dispatchers.Main) {
                    onComplete(proizvodi)
                }
            } catch (e: Exception) {
                Log.e("FirebaseHelper", "Greška u restoreFromCloudSimple: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete(emptyList())
                }
            }
        }
    }

    fun deleteProductFromCloud(proizvod: Proizvod, onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onComplete(false)

        database.reference
            .child(PATH_INVENTORY)
            .child(userId)
            .child(proizvod.id.toString())
            .removeValue()
            .addOnSuccessListener {
                onComplete(true)
                Log.d("FirebaseHelper", "Proizvod obrisan iz cloud-a: ${proizvod.naziv}")
            }
            .addOnFailureListener { e ->
                onComplete(false)
                Log.e("FirebaseHelper", "Greška pri brisanju iz cloud-a: ${e.message}")
            }
    }
}