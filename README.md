# SwiftDrop

Aplikasi Android untuk mengirim dan menerima file langsung antarperangkat di jaringan Wi-Fi yang sama. Tanpa cloud, tanpa akun, tanpa iklan.

## Status implementasi

| Fitur (PRD) | Status |
|---|---|
| Beranda Kirim / Terima | Selesai |
| Pemilihan file via SAF (`OpenMultipleDocuments`, `OpenDocumentTree`) | Selesai |
| Discovery perangkat (NSD / mDNS) | Selesai |
| Pairing dua arah dengan kode verifikasi 6 digit | Selesai |
| Transfer terenkripsi TLS 1.3 (fallback 1.2) | Selesai, belum diuji di perangkat nyata |
| Transfer banyak file + progres dan kecepatan sesaat | Selesai |
| Verifikasi SHA-256 per file | Selesai |
| Cancel | Selesai |
| Foreground service dengan notifikasi | Selesai |
| Riwayat (metadata saja) | Selesai |
| Pengaturan nama perangkat | Selesai |
| Wi-Fi Direct | **Belum** (hanya LAN) |
| Scan QR di sisi pengirim | **Belum** (QR hanya menampilkan alamat penerima) |
| Pause | **Belum** |
| Resume (P1) | **Belum** |
| Kirim folder (P1) | **Belum** |
| Tema manual, DataStore | **Belum** (tema mengikuti sistem; preferensi memakai SharedPreferences) |
| Benchmark throughput dan matriks uji perangkat | **Belum** |

## Cara kerja keamanan

1. Setiap perangkat membuat sertifikat self-signed EC P-256 yang disimpan di penyimpanan privat aplikasi.
2. Koneksi memakai TLS. Penerima mewajibkan sertifikat klien sehingga kedua sisi memiliki sertifikat peer.
3. Kedua layar menampilkan kode 6 digit yang diturunkan dari kedua sertifikat. Pengguna harus memastikan kode sama lalu menyetujui.
4. Penerima menyetujui sesi dan memilih folder tujuan. Pengirim tidak bisa menulis tanpa persetujuan.
5. Setiap file diverifikasi SHA-256. File dengan checksum tidak cocok dihapus.
6. Nama file dibersihkan (tanpa path, karakter berbahaya). Nama bentrok menjadi `nama (1).ext`, tidak menimpa diam-diam.

**Catatan jujur:** verifikasi peer tidak memakai CA, jadi keamanan bergantung pada pengguna benar-benar membandingkan kode. Mekanisme ini sengaja dipilih karena sertifikat self-signed tidak punya rantai kepercayaan. Model ancaman lengkap perlu didokumentasikan sebelum rilis.

## Izin Android

- `INTERNET`, `ACCESS_NETWORK_STATE`: koneksi socket di jaringan lokal.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`: transfer panjang.
- `POST_NOTIFICATIONS` (Android 13+): notifikasi transfer, diminta saat transfer dimulai.

Tidak meminta lokasi, kontak, penyimpanan luas, kamera, mikrofon, atau Bluetooth. NSD pada implementasi ini tidak memerlukan `NEARBY_WIFI_DEVICES`; tambahkan hanya jika uji perangkat membuktikan diperlukan.

## Build

Build berjalan otomatis di GitHub Actions (`.github/workflows/build.yml`) dan menghasilkan artefak `swiftdrop-debug-apk`.

Build lokal:

```bash
gradle wrapper --gradle-version 8.7   # sekali, untuk menambahkan gradlew
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
```

Membutuhkan JDK 17 dan Android SDK (platform 35).

## Batasan

- Hanya diuji secara logika pada unit test (`Util`). Alur jaringan, izin, dan service belum diuji di perangkat.
- Menerima dari perangkat yang tidak berada di subnet/Wi-Fi yang sama tidak didukung.
- `minSdk 26` adalah keputusan sementara sesuai PRD dan perlu divalidasi ulang.
