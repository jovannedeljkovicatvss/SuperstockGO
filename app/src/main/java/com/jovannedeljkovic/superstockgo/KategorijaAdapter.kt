package com.jovannedeljkovic.superstockgo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat

class KategorijaAdapter(
    private val kategorije: List<Kategorija>,
    private val onKategorijaClick: (Kategorija) -> Unit,
    private val onEditCategoryClick: ((Kategorija) -> Unit)? = null,
    private val onDeleteCategoryClick: ((Kategorija) -> Unit)? = null,
    private val onRestoreCategoryClick: ((Kategorija) -> Unit)? = null,
    private val onUpdateCategoryClick: ((Kategorija, Kategorija) -> Unit)? = null // NOVO
) : RecyclerView.Adapter<KategorijaAdapter.KategorijaViewHolder>() {

    // OSNOVNE KATEGORIJE KONSTANTE
    private val osnovneKategorijeNazivi = Constants.Kategorije.SVE

    // POMOĆNE LISTE ZA DETEKCIJU
    private val originalOsnovneNazivi = listOf(
        "Hrana", "Piće", "Oprema", "Odeća", "Higijena", "Tehnika"
    )

    class KategorijaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.cardKategorija)
        val tvIkona: TextView = itemView.findViewById(R.id.tvIkona)
        val tvNaziv: TextView = itemView.findViewById(R.id.tvNazivKategorija)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KategorijaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_kategorija, parent, false)
        return KategorijaViewHolder(view)
    }

    override fun onBindViewHolder(holder: KategorijaViewHolder, position: Int) {
        val kategorija = kategorije[position]

        holder.tvIkona.text = kategorija.ikona
        holder.tvNaziv.text = kategorija.naziv
        holder.cardView.setCardBackgroundColor(
            ContextCompat.getColor(holder.itemView.context, kategorija.boja)
        )

        // Proveri da li je ovo originalna osnovna kategorija
        val isOriginalOsnovna = originalOsnovneNazivi.contains(kategorija.naziv)

        // Proveri da li je modifikovana verzija osnovne kategorije
        val isModifiedOsnovna = isModifiedOsnovnaCategory(kategorija.naziv)

        // LONG CLICK - različito ponašanje za različite tipove kategorija
        holder.itemView.setOnLongClickListener {
            when {
                // Ako je ORIGINALNA osnovna kategorija - dozvoli edit
                isOriginalOsnovna -> {
                    onEditCategoryClick?.invoke(kategorija)
                    true
                }

                // Ako je MODIFIKOVANA osnovna kategorija - prikaži opciju za vraćanje
                isModifiedOsnovna -> {
                    showRestoreOptionsDialog(holder.itemView.context, kategorija)
                    true
                }

                // Ako je PRAVA custom kategorija (nije osnovna) - prikaži opcije za brisanje/izmenu
                else -> {
                    showCustomCategoryOptions(holder.itemView.context, kategorija)
                    true
                }
            }
        }

        // SHORT CLICK - navigacija
        holder.itemView.setOnClickListener {
            onKategorijaClick(kategorija)
        }
    }

    override fun getItemCount(): Int = kategorije.size

    // ========== POMOĆNE METODE ==========

    /**
     * Proverava da li je kategorija modifikovana verzija osnovne kategorije
     * Na primer: "Hrana1", "Hrana (Voće)", "Piće2" itd.
     */
    private fun isModifiedOsnovnaCategory(categoryName: String): Boolean {
        // Proveri da li naziv počinje sa nekom od osnovnih kategorija
        return originalOsnovneNazivi.any { baseName ->
            categoryName.startsWith(baseName) && categoryName != baseName
        }
    }

    /**
     * Pronalazi originalni naziv za modifikovanu kategoriju
     */
    private fun findOriginalName(modifiedName: String): String? {
        return originalOsnovneNazivi.find { modifiedName.startsWith(it) }
    }

    /**
     * Dijalog opcija za vraćanje modifikovane kategorije na original
     */
    private fun showRestoreOptionsDialog(context: Context, kategorija: Kategorija) {
        val originalName = findOriginalName(kategorija.naziv)

        if (originalName != null) {
            AlertDialog.Builder(context)
                .setTitle("Opcije za '${kategorija.naziv}'")
                .setMessage("Ova kategorija je izmenjena verzija originalne '$originalName'")
                .setPositiveButton("Vrati na '$originalName'") { _, _ ->
                    // Pozovi callback za vraćanje na original
                    onRestoreCategoryClick?.invoke(
                        Kategorija(
                            ikona = Constants.Kategorije.EMOJI_MAP[originalName] ?: "\uD83D\uDCDC",
                            naziv = originalName,
                            boja = Constants.Kategorije.BOJA_MAP[originalName] ?: android.R.color.holo_blue_light
                        )
                    )
                }
                .setNegativeButton("Obriši ovu kategoriju") { _, _ ->
                    // Pozovi callback za brisanje
                    onDeleteCategoryClick?.invoke(kategorija)
                }
                .setNeutralButton("Otkaži", null)
                .show()
        } else {
            // Ako ne možemo pronaći original, tretiraj kao custom kategoriju
            showCustomCategoryOptions(context, kategorija)
        }
    }

    /**
     * Dijalog opcija za prave custom kategorije
     */
    private fun showCustomCategoryOptions(context: Context, kategorija: Kategorija) {
        val options = arrayOf("Obriši", "Izmeni", "Otkaži")

        AlertDialog.Builder(context)
            .setTitle("Opcije za '${kategorija.naziv}'")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Brisanje
                        AlertDialog.Builder(context)
                            .setTitle("Potvrda brisanja")
                            .setMessage("Da li ste sigurni da želite da obrišete '${kategorija.naziv}'?\n\nProizvodi u ovoj kategoriji NEĆE biti obrisani.")
                            .setPositiveButton("Obriši") { _, _ ->
                                onDeleteCategoryClick?.invoke(kategorija)
                            }
                            .setNegativeButton("Otkaži", null)
                            .show()
                    }
                    1 -> { // Izmena
                        showEditCustomCategoryDialog(context, kategorija)
                    }
                    // 2 -> Otkaži
                }
            }
            .show()
    }

    private fun showEditCustomCategoryDialog(context: Context, kategorija: Kategorija) {
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_add_category, null)

        val etNaziv = dialogView.findViewById<EditText>(R.id.etNazivKategorije)
        val spEmoji = dialogView.findViewById<Spinner>(R.id.spEmoji)
        val spBoja = dialogView.findViewById<Spinner>(R.id.spBoja)

        // Postavi postoje e vrednosti
        etNaziv.setText(kategorija.naziv)

        // ========== NOVA LISTA EMOJI-JA (ISTA KAO U showAddCategoryDialog) ==========
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

            // === DODAJTE NOVE EMOJI-JE (ISTE KAO U showAddCategoryDialog) ===
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
            "🔌 Električni utikač",
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
            "🖨️ Štampa ",
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
            "🎃 Bundeva za Noć  veštice",
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
            "🎪 Cirkuski  ator",
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
            "🏃 Trkač",
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

        // ========== LISTA BOJA (OSTAJE ISTA) ==========
        val bojeList = listOf(
            "Zelena svetla" to R.color.green_light,
            "Zelena" to android.R.color.holo_green_light,
            "Zelena tamna" to android.R.color.holo_green_dark,
            "Plava svetla" to R.color.blue_light,
            "Plava" to android.R.color.holo_blue_light,
            "Plava tamna" to android.R.color.holo_blue_dark,
            "Narand asta" to android.R.color.holo_orange_light,
            "Narand asta tamna" to android.R.color.holo_orange_dark,
            "Ljubi asta" to android.R.color.holo_purple,
            "Ljubi asta svetla" to R.color.purple_light,
            "Crvena" to android.R.color.holo_red_light,
            "Crvena tamna" to android.R.color.holo_red_dark,
            "Siva" to android.R.color.darker_gray,
            " uta" to android.R.color.holo_orange_dark,
            "Tirkizna" to android.R.color.holo_blue_bright
        )

        // Postavi adaptere za spinner-e
        val emojiAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, emojiOpisi)
        val bojeAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, bojeList.map { it.first })

        emojiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bojeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spEmoji.adapter = emojiAdapter
        spBoja.adapter = bojeAdapter

        // Selektuj trenutni emoji
        val emojiPosition = emojiList.indexOf(kategorija.ikona)
        if (emojiPosition >= 0) {
            spEmoji.setSelection(emojiPosition)
        } else {
            // Ako emoji nije prona en, selektuj prvi
            spEmoji.setSelection(0)
        }

        // Selektuj trenutnu boju
        val bojaIndex = bojeList.indexOfFirst { it.second == kategorija.boja }
        if (bojaIndex >= 0) {
            spBoja.setSelection(bojaIndex)
        }

        AlertDialog.Builder(context)
            .setTitle("Izmeni kategoriju '${kategorija.naziv}'")
            .setView(dialogView)
            .setPositiveButton("Sačuvaj") { _, _ ->
                val noviNaziv = etNaziv.text.toString().trim()
                val emojiIndex = spEmoji.selectedItemPosition
                val noviEmoji = emojiList[emojiIndex]
                val novaBoja = bojeList[spBoja.selectedItemPosition].second

                if (noviNaziv.isEmpty()) {
                    Toast.makeText(context, "Unesite naziv kategorije", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (noviNaziv == kategorija.naziv &&
                    noviEmoji == kategorija.ikona &&
                    novaBoja == kategorija.boja) {
                    Toast.makeText(context, "Niste napravili nikakve promene", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Kreiraj a uriranu kategoriju
                val azuriranaKategorija = Kategorija(noviEmoji, noviNaziv, novaBoja)

                // Pozovi callback za a uriranje
                onUpdateCategoryClick?.invoke(kategorija, azuriranaKategorija)

                Toast.makeText(context,
                    "Kategorija '${kategorija.naziv}' a urirana",
                    Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }
    /**
     * Update podataka u adapteru
     */
    fun updateData(newKategorije: List<Kategorija>) {
        // Ova metoda bi trebala biti u adapteru, ali koristimo notifyDataSetChanged
        // Ako želite bolje performanse, koristite ListAdapter umesto RecyclerView.Adapter
        // Za sada ćemo koristiti notifyDataSetChanged u aktivnosti
    }
}