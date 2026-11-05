# Struktur modul blog

Seluruh kode khusus blog berada di `com.ilham.personal_finance_api.blog`:

- `controller`: endpoint autentikasi CMS, kategori, tag, dan create artikel.
- `dto`: request dan response API blog.
- `entity`: model akun database BlogAccount dan jenis taxonomy.
- `repository`: BlogStore, akses JDBC dan transaksi khusus database blog.
- `service`: autentikasi, aturan taxonomy, dan pembuatan artikel.
- `config`: datasource serta migration Flyway blog.

Blog menggunakan JDBC, sehingga model pada entity bukan entity JPA dan tidak
masuk persistence unit keuangan. BlogStore didaftarkan melalui BlogConfiguration
agar selalu memakai datasource blog. Endpoint dan format JSON tetap sama.

Database, sesi CMS, dan migration blog sudah terpisah dari finance. Migration tetap
berada di `src/main/resources/db/blog-migration`.

Saat mengekstrak menjadi aplikasi tersendiri, sertakan migration dan konfigurasi
BLOG_*, dependency JDBC/PostgreSQL/Flyway/validation/web/Jsoup, serta Clock bean.
Saat ini modul masih memakai utilitas bersama WebResponse, PaginationResponse,
BCrypt, penanganan error global dan konfigurasi web/CORS. Komponen bersama tersebut
perlu dibawa atau diganti dalam aplikasi baru. Pemindahan package ini belum membuat
microservice atau modul build Maven terpisah.
