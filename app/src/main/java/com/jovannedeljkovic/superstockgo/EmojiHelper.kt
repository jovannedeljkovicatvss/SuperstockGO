package com.jovannedeljkovic.superstockgo

object EmojiHelper {

    // === UI EMOJI (za TextView, RecyclerView, Toolbar) ===
    object UI {
        // Kategorije
        const val HRANA = "🍔"
        const val PICE = "🥤"
        const val OPREMA = "📝"
        const val ODECA = "👕"
        const val HIGIJENA = "🧹"
        const val TEHNIKA = "📱"
        const val SVE_STAVKE = "📜"
        const val NISKA_ZALIHA = "⚠️"
        const val DODAJ_PROIZVOD = "➕"

        // Akcije
        const val CHECK = "✅"
        const val WARNING = "⚠️"
        const val ERROR = "❌"
        const val INFO = "ℹ️"
        const val CLOUD = "☁️"
        const val FOLDER = "📂"
        const val STATS = "📊"
        const val BACKUP = "💾"
        const val SYNC = "🔄"
        const val SMS = "📨"
        const val EMAIL = "📧"
        const val PDF = "📄"
        const val CSV = "📋"
        const val LOGOUT = "🚪"
        const val CLEAN = "🧹"
        const val ADD = "➕"
        const val EDIT = "✏️"
        const val DELETE = "🗑️"
        const val HOME = "🏠"
        const val USER = "👤"
        const val OFFLINE = "📴"
        const val UPLOAD = "⬆️"
        const val DOWNLOAD = "⬇️"
    }

    // === SYSTEM EMOJI (za Toast, Log, SMS) - jednostavni simboli ===
    object System {
        const val CHECK = "[✓]"
        const val WARNING = "[!]"
        const val ERROR = "[✗]"
        const val INFO = "[i]"
        const val CLOUD = "[C]"
        const val FOLDER = "[F]"
        const val STATS = "[#]"
        const val BACKUP = "[B]"
        const val SYNC = "[⇄]"
        const val SMS = "[SMS]"
        const val EMAIL = "[@]"
        const val PDF = "[PDF]"
        const val CSV = "[CSV]"
        const val LOGOUT = "[X]"
        const val CLEAN = "[C]"
        const val ADD = "[+]"
        const val EDIT = "[E]"
        const val DELETE = "[D]"
        const val HOME = "[H]"
        const val USER = "[U]"
        const val OFFLINE = "[O]"
        const val UPLOAD = "[↑]"
        const val DOWNLOAD = "[↓]"
    }

    // === ALTERNATIVNI SIMBOLI (za SMS/Email) ===
    object Text {
        const val CHECK = "[OK]"
        const val WARNING = "[UPOZORENJE]"
        const val ERROR = "[GREŠKA]"
        const val INFO = "[INFO]"
        const val CLOUD = "[CLOUD]"
        const val BACKUP = "[BACKUP]"
        const val SYNC = "[SINHRONIZACIJA]"
        const val SMS = "[SMS]"
        const val LOW_STOCK = "[NISKA ZALIHA]"
        const val OUT_OF_STOCK = "[NEMA NA STANJU]"
    }

    // === POMOĆNE METODE ===

    // Za Toast poruke
    fun forToast(type: String): String {
        return when(type.lowercase()) {
            "check", "success", "ok" -> System.CHECK
            "warning", "alert" -> System.WARNING
            "error", "fail" -> System.ERROR
            "info" -> System.INFO
            "cloud" -> System.CLOUD
            "backup" -> System.BACKUP
            "sync" -> System.SYNC
            "sms" -> System.SMS
            "email" -> System.EMAIL
            "pdf" -> System.PDF
            "csv" -> System.CSV
            "logout" -> System.LOGOUT
            "clean" -> System.CLEAN
            "add" -> System.ADD
            "edit" -> System.EDIT
            "delete" -> System.DELETE
            else -> ""
        }
    }

    // Za UI (TextView, Toolbar, CardView)
    fun forUI(type: String): String {
        return when(type.lowercase()) {
            "check", "success", "ok" -> UI.CHECK
            "warning", "alert" -> UI.WARNING
            "error", "fail" -> UI.ERROR
            "info" -> UI.INFO
            "cloud" -> UI.CLOUD
            "backup" -> UI.BACKUP
            "sync" -> UI.SYNC
            "sms" -> UI.SMS
            "email" -> UI.EMAIL
            "pdf" -> UI.PDF
            "csv" -> UI.CSV
            "logout" -> UI.LOGOUT
            "clean" -> UI.CLEAN
            "add" -> UI.ADD
            "edit" -> UI.EDIT
            "delete" -> UI.DELETE
            "home" -> UI.HOME
            "user" -> UI.USER
            "offline" -> UI.OFFLINE
            "upload" -> UI.UPLOAD
            "download" -> UI.DOWNLOAD
            else -> ""
        }
    }

    // Za SMS/Email
    fun forText(type: String): String {
        return when(type.lowercase()) {
            "check", "success", "ok" -> Text.CHECK
            "warning", "alert" -> Text.WARNING
            "error", "fail" -> Text.ERROR
            "info" -> Text.INFO
            "cloud" -> Text.CLOUD
            "backup" -> Text.BACKUP
            "sync" -> Text.SYNC
            "sms" -> Text.SMS
            "low_stock" -> Text.LOW_STOCK
            "out_of_stock" -> Text.OUT_OF_STOCK
            else -> ""
        }
    }

    // === POSTOJEĆE METODE (sačuvajte ih) ===

    // Osnovne kategorije emoji
    val osnovneKategorijeEmoji = listOf(
        UI.HRANA to "Hrana",
        UI.PICE to "Piće",
        UI.OPREMA to "Kancelarijski materijal",
        UI.ODECA to "Odeća i obuća",
        UI.HIGIJENA to "Sredstva za čišćenje",
        UI.TEHNIKA to "Elektronika",
        UI.SVE_STAVKE to "Sve stavke",
        UI.NISKA_ZALIHA to "Niska zaliha",
        UI.DODAJ_PROIZVOD to "Dodaj proizvod"
    )

    // Lista svih dostupnih emoji za spinner
    val sviEmoji = listOf(
        UI.HRANA, UI.PICE, UI.OPREMA, UI.ODECA, UI.HIGIJENA, UI.TEHNIKA,
        "🛍️", "💼", "🍺", "🌮", "🍪", "🍩", "🍞", "🍓", "🧀", "🧠",
        "🔧", "🚛", "🚗", "🎧", "📷", "💻", "📺", "📦", "🛒", "💰",
        "💵", UI.ADD, "➖", UI.CHECK, UI.ERROR, "⭕", "🔴", "🟢", "🟡", "🔵", "🔶"
    )

    // Konvertuj emoji u čitljiv tekst (za debug)
    fun emojiToText(emoji: String): String {
        return when(emoji) {
            UI.HRANA -> "Hamburger"
            UI.PICE -> "Cup with straw"
            UI.OPREMA -> "Memo"
            UI.ODECA -> "T-Shirt"
            UI.HIGIJENA -> "Broom"
            UI.TEHNIKA -> "Mobile phone"
            UI.SVE_STAVKE -> "Briefcase"
            UI.NISKA_ZALIHA -> "Warning"
            UI.DODAJ_PROIZVOD -> "Plus"
            else -> "Unknown emoji"
        }
    }

    // Provera da li string sadrži emoji
    fun containsEmoji(text: String): Boolean {
        val regex = Regex("""\p{So}""")
        return regex.containsMatchIn(text)
    }

    // Ekstraktuj emoji iz teksta
    fun extractEmoji(text: String): String {
        val regex = Regex("""(\p{So})""")
        val match = regex.find(text)
        return match?.value ?: ""
    }

    // Ekstraktuj naziv bez emoji
    fun extractNameWithoutEmoji(text: String): String {
        val regex = Regex("""\p{So}\s*""")
        return text.replace(regex, "").trim()
    }
}