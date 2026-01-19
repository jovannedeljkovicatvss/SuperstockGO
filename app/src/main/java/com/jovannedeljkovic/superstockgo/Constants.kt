// Constants.kt
package com.jovannedeljkovic.superstockgo

object Constants {

    // OSNOVNE KATEGORIJE - LAKO PROMENITI NA JEDNOM MESTU
    object Kategorije {
        const val HRANA = "Hrana"
        const val PICE = "Piće"
        const val OPREMA = "Oprema"
        const val ODECA = "Odeća"
        const val HIGIJENA = "Higijena"
        const val TEHNIKA = "Tehnika"
        const val SVE_STAVKE = "Sve stavke"
        const val NISKA_ZALIHA = "Niska zaliha"
        const val DODAJ_PROIZVOD = "Dodaj proizvod"

        object SyncSettings {
            var AUTO_SYNC_ENABLED = false  // Postavite na false da onemogućite automatsku sinhronizaciju
        }
        // Lista svih osnovnih kategorija
        val OSNOVNE = listOf(HRANA, PICE, OPREMA, ODECA, HIGIJENA, TEHNIKA)
        val SVE = listOf(HRANA, PICE, OPREMA, ODECA, HIGIJENA, TEHNIKA,
            SVE_STAVKE, NISKA_ZALIHA, DODAJ_PROIZVOD)

        // Mapiranje kategorija na Unicode emoji
        val EMOJI_MAP = mapOf(
            HRANA to "🍔",       // 🍔
            PICE to "🥤",        // 🥤
            OPREMA to "📝",      // 📝
            ODECA to "👕",       // 👕
            HIGIJENA to "🧹",    // 🧹
            TEHNIKA to "📱",     // 📱
            SVE_STAVKE to "📜",  // 📜
            NISKA_ZALIHA to "⚠️", // ⚠️
            DODAJ_PROIZVOD to "➕" // ➕
        )

        // Mapiranje kategorija na boje
        val BOJA_MAP = mapOf(
            HRANA to android.R.color.holo_green_light,
            PICE to android.R.color.holo_blue_light,
            OPREMA to android.R.color.holo_orange_light,
            ODECA to android.R.color.holo_purple,
            HIGIJENA to android.R.color.holo_blue_dark,
            TEHNIKA to android.R.color.holo_red_light,
            SVE_STAVKE to android.R.color.darker_gray,
            NISKA_ZALIHA to android.R.color.holo_red_dark,
            DODAJ_PROIZVOD to android.R.color.holo_green_dark
        )
    }
}