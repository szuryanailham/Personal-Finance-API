# Autentikasi CMS blog

Modul opsional ini menambahkan API CMS pada aplikasi yang sama, dengan database
`artoku_blog`, connection pool, transaksi, pengguna, dan token yang terpisah.
Aktifkan BLOG_ENABLED hanya setelah database blog tersedia.

## Konfigurasi lokal

```bash
export BLOG_ENABLED=true
export BLOG_DB_URL=jdbc:postgresql://localhost:5432/artoku_blog
export BLOG_DB_USERNAME=postgres
# Isi BLOG_DB_PASSWORD lewat environment lokal, jangan commit password.
# Generate BLOG_REGISTRATION_KEY dengan openssl rand -hex 32 dan simpan sebagai secret.
export BLOG_FLYWAY_ENABLED=true
./mvnw spring-boot:run
```

Password di atas tidak diisi oleh aplikasi; BLOG_DB_PASSWORD harus tersedia.
Akun postgres hanya untuk development lokal. V1–V5 tetap; V6 menambahkan tabel
blog_auth_sessions. Flyway blog berjalan saat startup jika BLOG_FLYWAY_ENABLED=true,
sebelum BlogStore siap dipakai. Migration utama tetap menuju database keuangan.
Database blog tidak dibuat otomatis oleh aplikasi.

Di Docker Compose produksi, gunakan BLOG_DB_URL dengan host `postgres:5432`.
Untuk aplikasi langsung di host VPS sesuai compose produksi, gunakan
`localhost:15432`. Konfigurasi opsional tersedia dalam [.env.example](../.env.example).
Default BLOG_ENABLED=false menjaga aplikasi keuangan dapat berjalan tanpa blog.

Untuk produksi, pisahkan role runtime blog dari migrator; runtime membutuhkan
CONNECT database, USAGE schema, SELECT users/roles/user_roles/blog_auth_sessions,
INSERT users/user_roles/blog_auth_sessions, dan DELETE blog_auth_sessions untuk
endpoint saat ini. Berikan privilege pada objek lain hanya saat fitur memerlukannya.
Jalankan CLI migration terlebih dahulu atau isi BLOG_FLYWAY_USERNAME/PASSWORD dengan
akun DDL yang tepat. Bila memakai CLI, set BLOG_FLYWAY_ENABLED=false; schema harus
sudah mencapai V6. Owner NOLOGIN bisa tetap menjadi pemilik objek, sementara migrator
LOGIN diberi membership owner. Jangan gunakan superuser sebagai akun runtime VPS.

## Kebijakan registrasi

Registrasi CMS ditujukan untuk provisioning akun internal. Setiap request wajib
membawa X-Blog-Registration-Key yang cocok dengan BLOG_REGISTRATION_KEY. Key kosong
menonaktifkan registrasi (403). Key ini bukan token pengguna: jangan menaruhnya
pada JavaScript frontend publik atau memberikannya kepada pengunjung situs.
Gunakan script/server pengelola atau API client tepercaya. Rotasi/hapus key setelah
provisioning jika registrasi tidak diperlukan lagi. Tidak ada OTP/email verification
pada alur internal ini.

Akun baru selalu ACTIVE dengan role AUTHOR. Field role/status/id dari request tidak
dipakai untuk menentukan hak akses. Untuk admin pertama, operator database dapat
memberikan role ADMIN kepada akun yang sudah dibuat, pada database blog:

```sql
INSERT INTO user_roles(user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE lower(u.email) = lower('admin@example.com') AND r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
```

Ini menambah ADMIN tanpa menghapus AUTHOR. Belum ada endpoint pengelolaan role.
CRUD kategori/tag memeriksa role ADMIN/EDITOR untuk operasi tulis; lihat
[API kategori dan tag](blog-taxonomy-api.md). Role pada respons belum otomatis
mengamankan endpoint konten lain yang akan dibuat nanti;
endpoint tersebut harus memakai verifikasi sesi blog dan pemeriksaan role.

## Endpoint

| Method | Path | Akses | Hasil |
|---|---|---|---|
| POST | /api/blog/auth/register | Kunci registrasi server | 201, profil user |
| POST | /api/blog/auth/login | Email/password CMS | 200, token dan profil |
| GET | /api/blog/auth/me | Bearer token CMS | 200, profil dan role terkini |
| POST | /api/blog/auth/logout | Bearer token CMS | 200, sesi dicabut |

Semua respons memakai WebResponse (`data`, `message`, `errors`, `paging`), dengan
Cache-Control: no-store untuk respons sukses. Error menggunakan errors dari advice
proyek. Tidak ada password/hash password dalam respons.

Registrasi menggunakan application/json dan header X-Blog-Registration-Key:

```json
{
  "displayName": "Ilham",
  "email": "ilham@example.com",
  "password": "contoh-password-kuat"
}
```

Nama di-trim dan wajib 1–150 karakter. Email di-trim, diperiksa formatnya,
maksimum 254 karakter, lalu disimpan lowercase. Password tidak di-trim: wajib
nonblank, minimum 12 karakter, maksimum 72 karakter dan 72 byte UTF-8 untuk
mencegah truncation BCrypt. Password disimpan sebagai hash BCrypt cost 12.

Login menggunakan application/json:

```json
{
  "email": "ilham@example.com",
  "password": "contoh-password-kuat"
}
```

Respons login (contoh data):

```json
{
  "data": {
    "token": "<opaque-token>",
    "tokenType": "Bearer",
    "expiresAt": "2026-11-04T18:00:00Z",
    "user": {
      "id": "<uuid>",
      "displayName": "Ilham",
      "email": "ilham@example.com",
      "status": "ACTIVE",
      "roles": ["AUTHOR"]
    }
  }
}
```

Kirim `Authorization: Bearer <token>` pada /me dan /logout. Skema Bearer
tidak peka huruf besar/kecil; nilai token tetap peka huruf besar/kecil. Token opaque berasal
dari 32 random bytes, hanya SHA-256 token disimpan pada blog_auth_sessions.
TTL tetap 8 jam; tidak ada refresh token. Banyak sesi diperbolehkan, logout
mencabut sesi request tersebut saja. INACTIVE/SUSPENDED langsung ditolak pada
login dan verifikasi sesi; kedaluwarsa tepat expiresAt juga ditolak.
Login menghapus sesi pengguna yang sudah kedaluwarsa. Cleanup global dapat
menjalankan DELETE WHERE expires_at <= CURRENT_TIMESTAMP berkala di kemudian hari.

Token utama Artoku tidak berlaku untuk CMS; token CMS tidak berlaku pada API
keuangan. Tidak ada cookie autentikasi atau session browser otomatis.

## Error dan batas implementasi

- 400: input tidak valid, termasuk password lebih dari 72 byte UTF-8.
- 401: kredensial salah, akun nonaktif, token salah/kedaluwarsa/dicabut.
- 403: key registrasi salah, hilang, atau registrasi dinonaktifkan.
- 409: email CMS sudah terdaftar, termasuk konflik huruf besar/kecil atau insert bersamaan.

Gunakan HTTPS saat deployment. Sebelum endpoint login dibuka ke internet, terapkan
rate limit login dan registrasi pada reverse proxy/gateway. Aplikasi belum mempunyai
rate limiter CMS, reset password CMS, atau MFA. Kunci registrasi ditujukan untuk
server provisioning sehingga tidak ditambahkan pada CORS header frontend publik.

## Verifikasi

BlogAuthControllerTest menggunakan PostgreSQL blog terpisah dari PostgreSQL
keuangan. Pengujian mencakup migration kedua database, isolasi user dan token,
registrasi terbatas dengan role AUTHOR, duplikasi email case-insensitive, validasi
input/byte UTF-8, login, penyimpanan hash token, expiry, status akun, dan logout.
Pengujian juga memastikan logout tidak mencabut sesi lain, login membersihkan sesi
kedaluwarsa, dan /me membaca perubahan role tanpa perlu login ulang.

Jalankan pengujian autentikasi CMS:

```bash
./mvnw -Dtest=BlogAuthControllerTest test
```

Untuk seluruh suite dengan banyak Spring application context pada PostgreSQL
Testcontainers berlimit koneksi default, batasi pool pengujian:

```bash
./mvnw -Dspring.datasource.hikari.maximum-pool-size=3 -Dspring.datasource.hikari.minimum-idle=1 test
```
