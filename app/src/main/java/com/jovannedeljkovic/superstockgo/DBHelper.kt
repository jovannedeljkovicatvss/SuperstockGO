package com.jovannedeljkovic.superstockgo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "superstock.db"
        private const val DATABASE_VERSION = 2  // POVIŠENA VERZIJA ZBOG MIGRACIJE
        private const val TABLE_NAME = "inventar"
        private const val COLUMN_ID = "id"
        private const val COLUMN_USER_ID = "user_id"  // NOVA KOLONA ZA IZOLACIJU
        private const val COLUMN_NAZIV = "naziv"
        private const val COLUMN_KATEGORIJA = "kategorija"
        private const val COLUMN_KOLICINA = "kolicina"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USER_ID TEXT NOT NULL,
                $COLUMN_NAZIV TEXT NOT NULL,
                $COLUMN_KATEGORIJA TEXT NOT NULL,
                $COLUMN_KOLICINA INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTable)
        Log.d("DBHelper", "Tabela $TABLE_NAME kreirana sa user_id kolonom")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // MIGRACIJA: dodaj user_id kolonu za stare podatke
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_USER_ID TEXT DEFAULT 'unknown'")
            Log.d("DBHelper", "Migracija: dodata user_id kolona u tabelu")
        }
    }

    // === CRUD METODE SA USER_ID ===

    fun dodajProizvod(proizvod: Proizvod, userId: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_USER_ID, userId)
            put(COLUMN_NAZIV, proizvod.naziv)
            put(COLUMN_KATEGORIJA, proizvod.kategorija)
            put(COLUMN_KOLICINA, proizvod.kolicina)
        }
        val id = db.insert(TABLE_NAME, null, values)
        Log.d("DBHelper", "Dodat proizvod za user $userId: ${proizvod.naziv}, ID: $id")
        return id
    }

    fun sviProizvodi(userId: String): List<Proizvod> {
        val proizvodi = mutableListOf<Proizvod>()
        val db = readableDatabase

        // SAMO PROIZVODI ZA ODOGOVARAJUĆEG KORISNIKA
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_NAME WHERE $COLUMN_USER_ID = ? ORDER BY $COLUMN_NAZIV ASC",
            arrayOf(userId)
        )

        Log.d("DBHelper", "Učitavam proizvode za user: $userId")

        with(cursor) {
            while (moveToNext()) {
                val id = getInt(getColumnIndexOrThrow(COLUMN_ID))
                val naziv = getString(getColumnIndexOrThrow(COLUMN_NAZIV))
                val kategorija = getString(getColumnIndexOrThrow(COLUMN_KATEGORIJA))
                val kolicina = getInt(getColumnIndexOrThrow(COLUMN_KOLICINA))

                Log.d("DBHelper", "Pročitano: ID=$id, Naziv='$naziv', Kategorija='$kategorija', Količina=$kolicina")

                proizvodi.add(Proizvod(id, naziv, kategorija, kolicina))
            }
            close()
        }

        Log.d("DBHelper", "Ukupno pročitano proizvoda za user $userId: ${proizvodi.size}")
        return proizvodi
    }

    fun azurirajProizvod(proizvod: Proizvod, userId: String): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAZIV, proizvod.naziv)
            put(COLUMN_KATEGORIJA, proizvod.kategorija)
            put(COLUMN_KOLICINA, proizvod.kolicina)
        }

        // SAMO PROIZVODI KOJI PRIpadaju OVOM KORISNIKU
        val result = db.update(
            TABLE_NAME,
            values,
            "$COLUMN_ID = ? AND $COLUMN_USER_ID = ?",
            arrayOf(proizvod.id.toString(), userId)
        )
        Log.d("DBHelper", "Ažuriran proizvod ID=${proizvod.id} za user $userId, promena: $result")
        return result
    }

    fun obrisiProizvod(id: Int, userId: String): Int {
        val db = writableDatabase

        // SAMO PROIZVODI KOJI PRIpadaju OVOM KORISNIKU
        val result = db.delete(
            TABLE_NAME,
            "$COLUMN_ID = ? AND $COLUMN_USER_ID = ?",
            arrayOf(id.toString(), userId)
        )
        Log.d("DBHelper", "Obrisan proizvod ID=$id za user $userId, rezultat: $result")
        return result
    }

    fun pronadjiProizvod(id: Int, userId: String): Proizvod? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null,
            "$COLUMN_ID = ? AND $COLUMN_USER_ID = ?",
            arrayOf(id.toString(), userId),
            null, null, null
        )

        var proizvod: Proizvod? = null
        cursor.use {
            if (it.moveToFirst()) {
                proizvod = Proizvod(
                    id = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID)),
                    naziv = it.getString(it.getColumnIndexOrThrow(COLUMN_NAZIV)),
                    kategorija = it.getString(it.getColumnIndexOrThrow(COLUMN_KATEGORIJA)),
                    kolicina = it.getInt(it.getColumnIndexOrThrow(COLUMN_KOLICINA))
                )
            }
        }
        return proizvod
    }

    // === MIGRACIJA STARIH PODATAKA ===

    fun migrirajStarePodatke(userId: String) {
        val db = writableDatabase
        try {
            // Proveri da li ima podataka bez user_id ili sa starim user_id
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM $TABLE_NAME WHERE $COLUMN_USER_ID = 'unknown' OR $COLUMN_USER_ID = '' OR $COLUMN_USER_ID IS NULL",
                null
            )

            cursor.use {
                if (it.moveToFirst() && it.getInt(0) > 0) {
                    // Ažuriraj stare podatke sa novim user_id
                    val values = ContentValues().apply {
                        put(COLUMN_USER_ID, userId)
                    }
                    val updated = db.update(
                        TABLE_NAME,
                        values,
                        "$COLUMN_USER_ID = 'unknown' OR $COLUMN_USER_ID = '' OR $COLUMN_USER_ID IS NULL",
                        null
                    )
                    Log.d("DBHelper", "Migrirano $updated podataka za user: $userId")
                } else {
                    Log.d("DBHelper", "Nema podataka za migraciju za user: $userId")
                }
            }
        } catch (e: Exception) {
            Log.e("DBHelper", "Greška pri migraciji: ${e.message}")
        }
    }

    // === PROVERA TABELE (ZA DEBUG) ===

    fun proveriTabelu(): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='$TABLE_NAME'",
            null
        )
        val exists = cursor.count > 0
        cursor.close()
        Log.d("DBHelper", "Tabela $TABLE_NAME postoji: $exists")
        return exists
    }

    fun brojProizvodaPoKorisniku(userId: String): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_NAME WHERE $COLUMN_USER_ID = ?",
            arrayOf(userId)
        )
        cursor.use {
            return if (it.moveToFirst()) {
                it.getInt(0)
            } else {
                0
            }
        }
    }
}