# CRUD kategori dan tag CMS blog

Modul memerlukan `BLOG_ENABLED=true` serta database blog yang sudah dimigrasikan.
Tidak ada migration baru: tabel memakai V2 yang sudah tersedia. Semua endpoint
memerlukan `Authorization: Bearer <token CMS>` dari `/api/blog/auth/login`.
Token API keuangan dan registration key tidak dapat menggantikan token CMS.

## Akses

- Akun CMS ACTIVE dengan sesi valid dapat membaca daftar dan detail.
- ADMIN atau EDITOR dapat membuat, mengubah, dan menghapus.
- AUTHOR tidak dapat mengubah kategori/tag. Pemeriksaan role/status berlaku pada
  setiap request sehingga perubahan role langsung efektif.

Nama akun `superadmin` tidak otomatis memberikan role ADMIN. Untuk menambah ADMIN
pada akun tersebut, operator dapat menjalankan SQL berikut **di database artoku_blog**:

```sql
INSERT INTO user_roles(user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE lower(u.email) = lower('superadmin@gmail.com') AND r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
```

Tidak ada perubahan role otomatis melalui API ini. Untuk role runtime database
terbatas, tambahkan SELECT, INSERT, UPDATE, DELETE pada `categories` dan `tags`.
Trigger `updated_at` V2 tetap digunakan. API tidak mengubah tabel penghubung artikel.

## Endpoint

Base lokal: `http://localhost:8080`. Ganti `{resource}` dengan `categories` atau `tags`.

| Method | Path | Respons sukses |
|---|---|---|
| POST | `/api/blog/{resource}` | 201, objek baru |
| GET | `/api/blog/{resource}?page=0&size=20&search=finance` | 200, daftar dan paging |
| GET | `/api/blog/{resource}/{id}` | 200, detail |
| PUT | `/api/blog/{resource}/{id}` | 200, objek setelah pembaruan |
| DELETE | `/api/blog/{resource}/{id}` | 200, data null dan pesan sukses |

POST dan PUT menggunakan `Content-Type: application/json`.

Body kategori:

```json
{
  "name": "Personal Finance",
  "slug": "personal-finance",
  "description": "Tips mengelola keuangan pribadi"
}
```

Body tag:

```json
{
  "name": "Budgeting",
  "slug": "budgeting"
}
```

`name` dan `slug` wajib, di-trim. Nama maksimum 100 karakter. Slug harus diisi manual,
maksimum 150 karakter, hanya huruf kecil ASCII/angka dipisahkan satu tanda `-`.
Contoh valid: `personal-finance`, `tips-2026`. Slug unik per jenis; kategori dan tag
boleh memakai slug sama. Nama tidak wajib unik. Deskripsi kategori opsional,
maksimum 10000 karakter. PUT mengganti field yang dapat diedit; deskripsi kategori
yang tidak dikirim menjadi null. Tag tidak mempunyai deskripsi; responsnya
menggunakan model yang sama dengan `description: null`.

Respons objek memakai envelope WebResponse:

```json
{
  "data": {
    "id": "<uuid>",
    "name": "Personal Finance",
    "slug": "personal-finance",
    "description": "Tips mengelola keuangan pribadi",
    "createdAt": "2026-11-04T03:00:00Z",
    "updatedAt": "2026-11-04T03:00:00Z"
  },
  "errors": null,
  "message": null,
  "paging": null
}
```

Daftar menggunakan `data: [...]` dan `paging` berisi `currentPage`, `totalPage`,
`size`, `totalItem`. Page mulai dari 0, size 1–100 (default 20). Search maksimal
150 karakter; mencari substring name/slug tanpa membedakan kapital. `%` dan `_`
diperlakukan literal. Urutan tetap: nama case-insensitive, lalu UUID. Halaman di
luar hasil mengembalikan daftar kosong. Daftar kosong memiliki totalPage 0.

## Error

- 400: validasi body, UUID, page, size, atau search gagal.
- 401: sesi hilang, salah, kedaluwarsa, dicabut, atau akun nonaktif.
- 403: akun tidak memiliki ADMIN/EDITOR untuk operasi tulis.
- 404: UUID kategori/tag tidak ditemukan.
- 409: slug sudah dipakai atau objek yang akan dihapus masih direferensikan artikel.

Delete bersifat permanen dan mengikuti foreign key RESTRICT. Artikel yang sudah
soft-delete juga tetap menghalangi penghapusan selama relasinya masih ada.
Semua respons sukses memakai Cache-Control: no-store.

## Postman dan pengujian

Import [koleksi Postman](postman/blog-cms.postman_collection.json). Isi variabel
`email` dan `password`, jalankan Login CMS; skrip koleksi menyimpan token otomatis.
Request Create menyimpan `categoryId`/`tagId` untuk Get, Update, dan Delete berikutnya.
Jalankan dengan akun ADMIN/EDITOR. Koleksi tidak memuat password atau token asli.

```bash
./mvnw -Dtest=BlogAuthControllerTest test
```

Pengujian integrasi PostgreSQL mencakup CRUD kedua resource, paginasi/pencarian,
validasi, slug duplikat saat create/update, akses AUTHOR/ADMIN/EDITOR, pencabutan
role, expiry, serta penolakan penghapusan objek yang terhubung ke artikel.

Delete sukses mengisi `message` dengan `Category deleted successfully` atau
`Tag deleted successfully`, sesuai resource.
