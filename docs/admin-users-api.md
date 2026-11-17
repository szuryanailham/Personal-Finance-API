# Daftar pengguna untuk analisis admin

`GET /api/admin/users?page=0&size=20`

Memerlukan `app.blog.enabled=true` dan header `Authorization: Bearer <token login CMS>` dengan role `ADMIN`. Akun CMS dan akun aplikasi keuangan merupakan akun terpisah. Role AUTHOR/EDITOR tidak diizinkan melihat daftar ini.

`page` dimulai dari 0; `size` bernilai 1–100. Ambil halaman berikutnya sampai `paging.totalPage` untuk menampilkan seluruh pengguna. Urutan stabil berdasarkan email lalu ID.

```json
{
  "data": [
    {
      "id": "cf3a5101-88e7-41af-9959-0d82820f8254",
      "firstName": "Ilham",
      "lastName": "Suryana",
      "email": "ilham@example.com",
      "transactionCount": 12,
      "activeTransactionCount": 10
    }
  ],
  "message": "Get registered users successfully",
  "errors": null,
  "paging": { "currentPage": 0, "totalPage": 1, "size": 20, "totalItem": 1 }
}
```

- Sumber data: pengguna terdaftar aplikasi keuangan, bukan daftar akun CMS. Registrasi OTP yang belum selesai belum menjadi pengguna.
- `transactionCount`: seluruh transaksi yang masih tercatat di database, termasuk soft-delete. Penghapusan permanen tidak dapat dihitung sebagai riwayat.
- `activeTransactionCount`: transaksi yang tidak dihapus.
- Pengguna tanpa transaksi tetap ditampilkan dengan jumlah 0.
- Password dan token tidak dikirimkan.
- Status: 401 tanpa sesi valid, 403 bukan ADMIN, 400 parameter halaman tidak valid.

## Integrasi `/admin/users`

Tampilkan kolom Nama (gabungkan firstName dan lastName), Email, Total transaksi, dan Transaksi aktif. Gunakan `paging.totalItem` untuk total pengguna dan `paging.totalPage` untuk navigasi halaman. Sertakan kondisi memuat, gagal, dan data kosong. Kirim token sesi CMS pada permintaan API; jangan memakai token pengguna keuangan atau menaruh token di URL.

Halaman frontend belum diubah karena folder frontend tidak dapat diakses dari sesi implementasi API ini.
