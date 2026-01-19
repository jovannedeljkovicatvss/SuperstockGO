![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![GitHub](https://img.shields.io/github/license/jovannedeljkovicatvss/SuperstockGO)
![GitHub last commit](https://img.shields.io/github/last-commit/jovannedeljkovicatvss/SuperstockGO)


# SuperstockGO 📱

Android aplikacija za upravljanje zalihama i inventarom.

## 📸 Screenshots
<table>
  <tr>
    <td><img src="./screenshots/main.jpg" width="200" alt="Glavni ekran"></td>
    <td><img src="./screenshots/screenshot_1.jpg" width="200" alt="Lista artikala"></td>
    <td><img src="./screenshots/screenshot_2.jpg" width="200" alt="Detalji"></td>
  </tr>
  <tr>
    <td><b>Glavni ekran</b></td>
    <td><b>Lista artikala</b></td>
    <td><b>Detalji artikla</b></td>
  </tr>
  <tr>
    <td><img src="./screenshots/screenshot_3.jpg" width="200" alt="Statistike"></td>
    <td><img src="./screenshots/screenshot_4.jpg" width="200" alt="Kategorije"></td>
    <td><img src="./screenshots/screenshot_5.jpg" width="200" alt="Backup"></td>
  </tr>
  <tr>
    <td><b>Statistike</b></td>
    <td><b>Kategorije</b></td>
    <td><b>Backup</b></td>
  </tr>
</table>

## ✨ Funkcionalnosti
- 📊 Upravljanje inventarom
- ☁️ Cloud sinhronizacija sa Firebase-om
- 💾 Lokalni backup i restore
- 📈 Statistike i izveštaj
- 🔔 SMS notifikacije za nisku zalihu
- 🎨 Custom kategorije sa emoji

## 🛠 Tehnologije
- **Kotlin** - Glavni programski jezik
- **Firebase** - Realtime Database i Authentication
- **SQLite** - Lokalna baza podataka
- **MPAndroidChart** - Grafikoni i statistike
- **Material Design** - UI komponente

## 📁 Struktura projekta
SuperstockGO/
├── app/
│ ├── src/main/java/com/jovannedeljkovic/superstockgo/
│ │ ├── MainActivity.kt
│ │ ├── KategorijeActivity.kt
│ │ ├── CloudSyncActivity.kt
│ │ ├── StatsActivity.kt
│ │ └── ...
│ ├── src/main/res/ # Resursi (layout, drawable, values)
│ └── build.gradle
├── screenshots/ # Folder sa screenshotovima
│ ├── main.jpg
│ ├── screenshot_1.jpg
│ └── ...
├── gradle/
└── build.gradle