![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![GitHub](https://img.shields.io/github/license/jovannedeljkovicatvss/SuperstockGO)
![GitHub last commit](https://img.shields.io/github/last-commit/jovannedeljkovicatvss/SuperstockGO)


# SuperstockGO 📱

Android aplikacija za upravljanje zalihama i inventarom.

## 📸 Screenshots


<table>
  <!-- PRVI RED: 5 SLIKA -->
  <tr>
    <td><img src="./screenshots/main.jpg" width="180" alt="Glavni ekran"></td>
    <td><img src="./screenshots/screenshot_1.jpg" width="180" alt="Ekran 1"></td>
    <td><img src="./screenshots/screenshot_2.jpg" width="180" alt="Ekran 2"></td>
    <td><img src="./screenshots/screenshot_3.jpg" width="180" alt="Ekran 3"></td>
    <td><img src="./screenshots/screenshot_4.jpg" width="180" alt="Ekran 4"></td>
  </tr>
  <tr>
    <td><center><b>Login ekran</b></center></td>
    <td><center><b>Lokalni Backup & Restore</b></center></td>
    <td><center><b>Statistike</b></center></td>
    <td><center><b>Statistike</b></center></td>
    <td><center><b>Kategorije</b></center></td>
  </tr>
  
  <!-- DRUGI RED: 5 SLIKA -->
  <tr>
    <td><img src="./screenshots/screenshot_5.jpg" width="180" alt="Ekran 5"></td>
    <td><img src="./screenshots/screenshot_6.jpg" width="180" alt="Ekran 6"></td>
    <td><img src="./screenshots/screenshot_7.jpg" width="180" alt="Ekran 7"></td>
    <td><img src="./screenshots/screenshot_8.jpg" width="180" alt="Ekran 8"></td>
    <td><img src="./screenshots/screenshot_9.jpg" width="180" alt="Ekran 9"></td>
  </tr>
  <tr>
    <td><center><b>Kategorije</b></center></td>
    <td><center><b>Lista artikala</b></center></td>
    <td><center><b>Sortiranje artikala</b></center></td>
    <td><center><b>Glavni Meni</b></center></td>
    <td><center><b>Cloud sinhronizacija</b></center></td>
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