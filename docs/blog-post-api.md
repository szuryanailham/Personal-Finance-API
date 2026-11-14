# Create artikel blog Artoku

`POST /api/blog/posts` memerlukan `Authorization: Bearer <token CMS>` dan
`Content-Type: application/json`. Modul harus diaktifkan dengan `BLOG_ENABLED=true`.
Token finance tidak dapat dipakai.

```json
{
  "title": "Cara mengatur keuangan bulanan",
  "slug": "cara-mengatur-keuangan-bulanan",
  "excerpt": "Panduan sederhana membuat anggaran.",
  "contentHtml": "<h2>Mulai dari anggaran</h2><p>Catat pemasukan dan pengeluaran.</p>",
  "status": "DRAFT",
  "featuredMediaId": null,
  "categoryIds": ["11111111-1111-4111-8111-111111111111"],
  "tagIds": ["22222222-2222-4222-8222-222222222222"]
}
```

Ganti UUID contoh dengan ID dari API kategori dan tag. Minimal satu kategori;
`tagIds` wajib dikirim, boleh `[]`. Masing-masing maksimal 100 ID unik.
AUTHOR dapat membuat DRAFT; ADMIN/EDITOR dapat membuat DRAFT atau PUBLISHED.
Status default DRAFT jika tidak dikirim atau null. Penulis selalu pengguna CMS
pada sesi; klien tidak memilih authorId. Waktu publikasi PUBLISHED diisi server.
`featuredMediaId` opsional: isi UUID gambar yang sudah ada di tabel `media`, atau
null jika tanpa gambar utama. ID yang tidak ada menghasilkan HTTP 400.
URL gambar tersebut tampil sebagai `heroImage` pada list CMS. Endpoint upload
media belum tersedia. Scheduling dan SEO belum menjadi bagian endpoint ini.

Title wajib, maksimal 250 karakter; slug wajib lowercase ASCII dengan pemisah
hyphen, maksimal 200 karakter. Excerpt opsional maksimal 10.000 karakter.
contentHtml wajib maksimal 1.000.000 karakter dan harus menyisakan teks terbaca
setelah sanitasi HTML. Script, event handler, serta URL berbahaya dibuang melalui
Jsoup Safelist; respons berisi HTML hasil sanitasi. Waktu baca dihitung dari teks,
200 kata/menit, minimal satu menit.

Sukses HTTP 201 memakai WebResponse, `message: "Blog post created successfully"`,
Cache-Control no-store, dan data:
`id`, `authorId`, `title`, `slug`, `excerpt`, `contentHtml`, `status`,
`publishedAt` (null pada draft), `readingTimeMinutes`, `categoryIds`, `tagIds`,
`createdAt`, `updatedAt`.
Artikel, reservasi slug (trigger), dan relasi kategori/tag ditulis dalam satu
transaksi pada database blog. Kategori/tag tidak dibuat otomatis.

Error: 400 input atau referensi tidak valid/ID berulang; 401 sesi CMS tidak valid;
403 role tidak sesuai; 409 slug sudah digunakan atau direservasi artikel lain.

## Hak akses database produksi

Jalankan sebagai owner/migrator pada database artoku_blog setelah migration.
Hak berikut merupakan tambahan untuk hak autentikasi CMS dan taxonomy yang sudah ada:

```sql
GRANT USAGE ON SCHEMA public TO artoku_blog_app;
GRANT SELECT, INSERT ON posts TO artoku_blog_app;
GRANT SELECT, INSERT ON post_categories, post_tags TO artoku_blog_app;
-- SELECT FOR KEY SHARE pada taxonomy memerlukan privilege UPDATE.
GRANT SELECT, UPDATE ON categories, tags TO artoku_blog_app;
-- Trigger reserve_post_slug berjalan dengan hak runtime.
GRANT SELECT, INSERT, DELETE ON post_slug_routes TO artoku_blog_app;
```

Jalankan seluruh migration blog hingga V7; V7 mengizinkan pemakaian ulang slug setelah soft delete.

## List artikel CMS

`GET /api/blog/posts?skip=0&limit=20` memerlukan Bearer token CMS dengan role
AUTHOR, EDITOR, atau ADMIN. Semua role tersebut dapat melihat seluruh artikel CMS
(DRAFT, PUBLISHED, ARCHIVED); soft-deleted tidak ditampilkan. Ini bukan feed publik.

- `skip`: jumlah artikel yang dilewati, default 0, minimal 0 (integer 64-bit).
- `limit`: jumlah maksimum artikel, default 20, rentang 1–100.
- Urutan tetap `created_at DESC, id DESC` agar timestamp sama mempunyai urutan stabil.
- Halaman berikutnya: `skip=20&limit=20`, lalu `skip=40&limit=20`.
- Melebihi total: HTTP 200, data kosong, hasMore false.
- Nilai parameter tidak valid: HTTP 400; tanpa sesi: 401; tanpa role CMS: 403.

Respons HTTP 200, Cache-Control no-store:

```json
{
  "data": [],
  "paging": {
    "skip": 0,
    "limit": 20,
    "totalItem": 0,
    "hasMore": false
  }
}
```

Setiap item berisi id, authorId, title, slug, excerpt, heroImage, status, publishedAt,
readingTimeMinutes, createdAt, dan updatedAt. Konten HTML serta relasi kategori/tag
tidak dimuat pada list. Total dan halaman dibaca pada snapshot database yang sama.
Field `heroImage` berisi URL dari `media.url` melalui `posts.featured_media_id`,
atau `null` jika artikel belum memiliki gambar utama. Contoh: `"heroImage": "https://example.com/blog/hero.jpg"`.
Role database runtime memerlukan `SELECT ON media` untuk membaca gambar utama:

```sql
GRANT SELECT ON media TO artoku_blog_app;
```

Search, filter tanggal, sort custom, dan endpoint detail belum ditambahkan pada list ini.

## Update parsial artikel CMS

`PATCH /api/blog/posts/{id}` dengan Bearer token CMS dan Content-Type application/json.
Tidak ada endpoint PUT. Body wajib berisi minimal satu field yang didukung.

```json
{
  "title": "Judul baru",
  "contentHtml": "<h2>Anggaran</h2><p>Isi artikel yang diperbarui.</p>",
  "categoryIds": ["11111111-1111-4111-8111-111111111111"],
  "tagIds": [],
  "status": "DRAFT"
}
```

Field yang didukung: title, slug, excerpt, contentHtml, categoryIds, tagIds, status.
Field yang tidak dikirim dipertahankan. `excerpt: null` mengosongkan excerpt;
field lainnya tidak menerima null. categoryIds minimal satu ID; tagIds boleh []
untuk menghapus semua tag. Array mengganti seluruh pilihan taxonomy, bukan menambah
satu per satu. ID harus ada dan unik. Batas panjang dan sanitasi HTML sama dengan create.

AUTHOR hanya dapat mengedit DRAFT miliknya sendiri dan tidak dapat mengubahnya
menjadi PUBLISHED/ARCHIVED. ADMIN/EDITOR dapat mengedit seluruh artikel aktif dan
mengubah status ke DRAFT, PUBLISHED, atau ARCHIVED. Artikel soft-deleted menghasilkan
404. authorId tidak dapat diubah melalui endpoint ini.

Transisi ke PUBLISHED mengisi publishedAt dari waktu server. Edit biasa pada artikel
PUBLISHED mempertahankan publishedAt. Perubahan ke DRAFT/ARCHIVED mempertahankan
riwayat publishedAt, dan publikasi ulang mengisinya dengan waktu publikasi baru.
Perubahan ke status selain DRAFT menghapus scheduledAt. Slug lama tetap direservasi selama artikel belum dihapus.
Semua perubahan termasuk taxonomy berada dalam satu transaksi; gagal validasi atau
konflik tidak menyimpan perubahan parsial.

Sukses: HTTP 200, Cache-Control no-store, WebResponse berisi BlogPostResponse dan
message `Blog post updated successfully`. Error: 400 input/referensi tidak valid,
401 sesi tidak valid, 403 hak akses tidak sesuai, 404 artikel tidak ditemukan,
409 slug sudah dipakai/direservasi.

Tambahan privilege runtime PostgreSQL, jalankan sebagai owner pada artoku_blog:

```sql
GRANT SELECT, UPDATE ON posts TO artoku_blog_app;
GRANT SELECT, INSERT, DELETE ON post_categories, post_tags TO artoku_blog_app;
```

## Detail artikel CMS

`GET /api/blog/posts/{id}` dengan header `Authorization: Bearer <token CMS>`.
Gunakan ID dari respons create atau daftar post untuk mengambil data sebelum PATCH.
AUTHOR dapat melihat artikel miliknya sendiri dalam status DRAFT, PUBLISHED, dan
ARCHIVED. ADMIN/EDITOR dapat melihat seluruh artikel yang belum dihapus.

Sukses HTTP 200, `Cache-Control: no-store`, dengan `data` berisi BlogPostResponse
lengkap: id, authorId, title, slug, excerpt, contentHtml, status, publishedAt,
readingTimeMinutes, categoryIds, tagIds, createdAt, updatedAt. Message:
`Blog post retrieved successfully`.

Error: 400 format UUID tidak valid, 401 token tidak valid/tidak dikirim,
403 role tidak sesuai atau artikel milik author lain, 404 ID tidak ditemukan atau
artikel sudah dihapus. Endpoint ini memerlukan autentikasi CMS.

Contoh pengujian manual:

```bash
curl '{{baseUrl}}/api/blog/posts/{{postId}}' \
  -H 'Authorization: Bearer <token CMS>'
```

Request `Detail Post CMS` juga tersedia di koleksi Postman blog CMS.

## Mengatasi 404 saat tes manual

`GET /api/blog/posts` adalah daftar; `GET /api/blog/posts/{id}` adalah detail.
Keduanya memerlukan Bearer token CMS. Daftar kosong tetap menghasilkan HTTP 200.
Jika URL daftar menghasilkan 404, pastikan versi aplikasi terbaru berjalan dan
modul blog aktif. Untuk membaca `BLOG_ENABLED=true` dari `.env`, restart dengan:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Di IDE, gunakan active profile `local` dan working directory root proyek.
Pastikan database blog tersedia dan kredensial `BLOG_DB_*` sesuai. Tanpa token,
endpoint blog yang aktif menghasilkan 401, bukan 404.

## Soft delete artikel CMS

`DELETE /api/blog/posts/{id}` dengan `Authorization: Bearer <token CMS>`, tanpa body.
AUTHOR hanya dapat menghapus DRAFT miliknya. ADMIN/EDITOR dapat menghapus artikel
DRAFT, PUBLISHED, maupun ARCHIVED. Pemeriksaan kepemilikan/status dan perubahan
berada dalam satu transaksi dengan row lock untuk mencegah balapan dengan PATCH.

Sukses HTTP 200 dengan `Cache-Control: no-store` dan message
`Blog post deleted successfully`. `deleted_at` diisi waktu server dan `scheduled_at`
dikosongkan. Konten dan kategori/tag tetap tersimpan. Reservasi slug aktif maupun
historis dilepas sehingga bisa dipakai post lain. Artikel
hilang dari daftar dan jumlah total, serta GET detail/PATCH/DELETE berikutnya
menghasilkan 404. Endpoint pemulihan belum tersedia.

Error: 400 UUID tidak valid, 401 token tidak valid/tidak dikirim, 403 tidak berhak,
404 artikel tidak ditemukan atau sudah dihapus.

```bash
curl -X DELETE 'http://localhost:8080/api/blog/posts/<postId>' \
  -H 'Authorization: Bearer <token CMS>'
```

Role runtime blog memerlukan `SELECT, UPDATE ON posts` (sama seperti PATCH).
Request `Soft Delete Post CMS` tersedia pada koleksi Postman blog CMS.

## Artikel publik untuk www.myartoku.com

Dua endpoint baca tanpa token CMS:

| Method | URL | Hasil |
|---|---|---|
| GET | `/api/blog/published?skip=0&limit=20` | Ringkasan artikel dan paging |
| GET | `/api/blog/published/{slug}` | Detail artikel beserta kategori dan tag |

List memakai `skip >= 0`, `limit` 1–100 (default 20), urutan publishedAt DESC lalu
id DESC. Respons `{ "data": [...], "paging": { "skip": 0, "limit": 20,
"totalItem": 0, "hasMore": false } }`. Daftar kosong tetap 200. Ringkasan berisi
id, title, slug, excerpt, publishedAt, readingTimeMinutes, tanpa contentHtml.

Detail memakai envelope WebResponse dengan `data` berisi id, title, slug, excerpt,
contentHtml, publishedAt, readingTimeMinutes, categories, tags, updatedAt. Setiap
kategori/tag berisi id, name, slug. Field CMS seperti authorId/status tidak diekspos.
Gunakan slug aktif dari list; slug lama setelah rename menghasilkan 404.

Kedua endpoint hanya mengambil PUBLISHED dengan deleted_at IS NULL dan published_at
sudah tercapai menurut waktu server. Draft, arsip, jadwal masa depan, dan artikel
yang dihapus tidak muncul, termasuk dalam totalItem. Detail yang tidak memenuhi
filter menghasilkan 404. Respons memakai Cache-Control no-store agar pengarsipan
atau penghapusan tidak tertahan oleh cache respons API.

Konfigurasi browser:

```env
BLOG_PUBLIC_ALLOWED_ORIGINS=https://www.myartoku.com
# Origin dashboard CMS produksi dan pengembangan lokal:
CORS_ALLOWED_ORIGINS=https://cms.myartoku.com,http://localhost:30001,http://localhost:3000
```

CORS khusus artikel hanya mengizinkan GET/OPTIONS dari origin publik yang dikonfigurasi.
Endpoint CMS `/api/blog/posts`, `/api/blog/categories`, dan `/api/blog/tags` tetap
memerlukan token CMS dan mengikuti aturan role. Registrasi/login CMS tetap mengikuti
aturan autentikasi yang sudah ada. Jangan menaruh token CMS atau registration key
pada frontend publik. CORS membatasi browser, bukan pemanggilan server/curl; pengamanan
CMS menggunakan token dan role. Jika SSR/service utama memanggil API dari server,
header Origin tidak diperlukan.

Contoh konsumsi browser (API_BASE_URL adalah alamat server API):

```javascript
const response = await fetch(`${API_BASE_URL}/api/blog/published?skip=0&limit=20`);
if (!response.ok) throw new Error('Gagal memuat artikel');
const { data, paging } = await response.json();

const detailResponse = await fetch(
  `${API_BASE_URL}/api/blog/published/${encodeURIComponent(data[0].slug)}`
);
if (!detailResponse.ok) throw new Error('Artikel tidak tersedia');
const { data: article } = await detailResponse.json();
```

Periksa data.length sebelum mengambil item pertama. Dua request publik tersedia di
koleksi Postman dengan auth noauth; isi articleSlug dari respons list. Role runtime
blog memerlukan SELECT pada posts, categories, tags, post_categories, dan post_tags.
Restart aplikasi setelah konfigurasi baru diterapkan.

## Penggunaan ulang slug setelah soft delete

Migration V7 mengganti constraint unik global dengan unique index untuk post yang
`deleted_at IS NULL`. Trigger melepaskan seluruh reservasi slug saat soft delete,
termasuk slug lama sebelum rename. Migration juga membersihkan reservasi milik
artikel yang sudah dihapus sebelum V7. Nilai slug dan konten pada baris terhapus
tetap tersimpan. POST/PATCH dapat menggunakan ulang slug tersebut; slug aktif
atau historis milik post yang belum dihapus tetap menghasilkan HTTP 409.

Jika `BLOG_FLYWAY_ENABLED=true`, restart aplikasi untuk menerapkan V7 menggunakan
akun migrator. Jika false, jalankan migration blog via Flyway CLI terlebih dahulu.
Role runtime memerlukan DELETE pada post_slug_routes untuk trigger soft delete:

```sql
GRANT SELECT, INSERT, DELETE ON post_slug_routes TO artoku_blog_app;
```

Jika kelak fitur restore ditambahkan, slug yang sudah dipakai post lain harus
diganti sebelum restore. Endpoint restore belum tersedia.
