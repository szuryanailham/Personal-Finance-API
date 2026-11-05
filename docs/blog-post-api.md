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
  "categoryIds": ["11111111-1111-4111-8111-111111111111"],
  "tagIds": ["22222222-2222-4222-8222-222222222222"]
}
```

Ganti UUID contoh dengan ID dari API kategori dan tag. Minimal satu kategori;
`tagIds` wajib dikirim, boleh `[]`. Masing-masing maksimal 100 ID unik.
AUTHOR dapat membuat DRAFT; ADMIN/EDITOR dapat membuat DRAFT atau PUBLISHED.
Status default DRAFT jika tidak dikirim atau null. Penulis selalu pengguna CMS
pada sesi; klien tidak memilih authorId. Waktu publikasi PUBLISHED diisi server.
Scheduling, media, dan SEO belum menjadi bagian endpoint ini.

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
GRANT SELECT, INSERT ON post_slug_routes TO artoku_blog_app;
```

Tidak diperlukan migration baru; endpoint memakai schema blog yang sudah tersedia.
