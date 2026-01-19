// app\src\main\java\com\jovannedeljkovic\superstockgo\LokalnaBazaHelper.kt
package com.jovannedeljkovic.superstockgo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class LokalnaBazaHelper(context: Context) : SQLiteOpenHelper(context, "lokalna_baza.db", null, 1) {

    companion object {
        private const val TABLE_NAME = "proizvodi"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAZIV = "naziv"
        private const val COLUMN_KATEGORIJA = "kategorija"
        private const val COLUMN_KOLICINA = "kolicina"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAZIV TEXT NOT NULL,
                $COLUMN_KATEGORIJA TEXT NOT NULL,
                $COLUMN_KOLICINA INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTable)
        Log.d("LokalnaBazaHelper", "Tabela kreirana")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun dodajProizvod(proizvod: Proizvod): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAZIV, proizvod.naziv)
            put(COLUMN_KATEGORIJA, proizvod.kategorija)
            put(COLUMN_KOLICINA, proizvod.kolicina)
        }
        val id = db.insert(TABLE_NAME, null, values)
        Log.d("LokalnaBazaHelper", "Dodat proizvod ID: $id")
        return id
    }

    fun sviProizvodi(): List<Proizvod> {
        val proizvodi = mutableListOf<Proizvod>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME", null)

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID))
                val naziv = it.getString(it.getColumnIndexOrThrow(COLUMN_NAZIV))
                val kategorija = it.getString(it.getColumnIndexOrThrow(COLUMN_KATEGORIJA))
                val kolicina = it.getInt(it.getColumnIndexOrThrow(COLUMN_KOLICINA))

                proizvodi.add(Proizvod(id, naziv, kategorija, kolicina))
            }
        }

        Log.d("LokalnaBazaHelper", "Učitano ${proizvodi.size} proizvoda")
        return proizvodi
    }

    fun azurirajProizvod(proizvod: Proizvod): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAZIV, proizvod.naziv)
            put(COLUMN_KATEGORIJA, proizvod.kategorija)
            put(COLUMN_KOLICINA, proizvod.kolicina)
        }
        return db.update(
            TABLE_NAME,
            values,
            "$COLUMN_ID = ?",
            arrayOf(proizvod.id.toString())
        )
    }

    fun obrisiProizvod(id: Int): Int {
        val db = writableDatabase
        return db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun pronadjiProizvod(id: Int): Proizvod? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null, null, null
        )

        cursor.use {
            if (it.moveToFirst()) {
                return Proizvod(
                    id = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID)),
                    naziv = it.getString(it.getColumnIndexOrThrow(COLUMN_NAZIV)),
                    kategorija = it.getString(it.getColumnIndexOrThrow(COLUMN_KATEGORIJA)),
                    kolicina = it.getInt(it.getColumnIndexOrThrow(COLUMN_KOLICINA))
                )
            }
        }
        return null
    }
}