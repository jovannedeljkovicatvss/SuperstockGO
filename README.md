![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![GitHub](https://img.shields.io/github/license/jovannedeljkovicatvss/SuperstockGO)
![GitHub last commit](https://img.shields.io/github/last-commit/jovannedeljkovicatvss/SuperstockGO)


# SuperstockGO 📱

Android aplikacija za upravljanje zalihama i inventarom.

## 📸 Screenshots
<div align="center">
  
**Glavni ekran**<br>
<img src="./screenshots/main.jpg" width="280"><br><br>

<div style="display: flex; flex-wrap: wrap; justify-content: center; gap: 15px;">
  <div><img src="./screenshots/screenshot_1.jpg" width="180"><br><small>Lista artikala</small></div>
  <div><img src="./screenshots/screenshot_2.jpg" width="180"><br><small>Detalji</small></div>
  <div><img src="./screenshots/screenshot_3.jpg" width="180"><br><small>Statistike</small></div>
  <div><img src="./screenshots/screenshot_4.jpg" width="180"><br><small>Kategorije</small></div>
  <div><img src="./screenshots/screenshot_5.jpg" width="180"><br><small>Backup</small></div>
  <div><img src="./screenshots/screenshot_6.jpg" width="180"><br><small>Notifikacije</small></div>
  <div><img src="./screenshots/screenshot_7.jpg" width="180"><br><small>Sinhronizacija</small></div>
  <div><img src="./screenshots/screenshot_8.jpg" width="180"><br><small>Podešavanja</small></div>
  <div><img src="./screenshots/screenshot_9.jpg" width="180"><br><small>Dark mode</small></div>
</div>
</div>

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