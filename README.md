![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![GitHub](https://img.shields.io/github/license/jovannedeljkovicatvss/SuperstockGO)
![GitHub last commit](https://img.shields.io/github/last-commit/jovannedeljkovicatvss/SuperstockGO)


# SuperstockGO 📱

Android aplikacija za upravljanje zalihama i inventarom.

## 📸 Screenshots
![App Screenshot](./screenshots/main.jpg)

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