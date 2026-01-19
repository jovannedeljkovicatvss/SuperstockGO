package com.jovannedeljkovic.superstockgo

import java.util.Locale

data class Proizvod(
    val id: Int = 0,
    val naziv: String = "",
    val kategorija: String = "",
    val kolicina: Int = 0
) {
    // Prazan konstruktor potreban za Firebase
    constructor() : this(0, "", "", 0)

    // VAŽNO: Dodajte ovu metodu za jedinstveni ključ
    fun getUniqueKey(): String {
        return "${naziv.trim().toLowerCase(Locale.getDefault())}|${kategorija.trim().toLowerCase(Locale.getDefault())}"
    }

    // Dodatna pomoćna metoda za debug
    fun logInfo(tag: String = "Proizvod") {
        println("$tag: ${this.naziv} | ${this.kategorija} | ID: ${this.id} | Key: ${getUniqueKey()}")
    }
}