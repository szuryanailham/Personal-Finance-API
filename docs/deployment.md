# Deployment production otomatis

Workflow `.github/workflows/release.yml` menjalankan pengujian untuk pull request
(PR) yang menargetkan `release/v.0.1`. Setiap push ke branch tersebut, termasuk
hasil merge, menjalankan Maven `clean verify` dengan Java 21, termasuk pengujian
integrasi Testcontainers yang sudah tersedia. Hanya proses pengujian push yang
berhasil yang dapat melanjutkan ke deployment production melalui SSH. Proses
pengujian PR tidak mendapatkan akses ke secrets VPS.

## Deployment yang sudah berjalan di VPS

Artoku sudah terpasang dan berjalan dari `/opt/artoku`. Langkah deployment
manual saat ini adalah:

```bash
cd /opt/artoku
git pull origin release/v.0.1
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
```

Pipeline mengotomatiskan deployment tersebut setelah pengujian berhasil.
Tetap gunakan direktori `/opt/artoku`, file `.env` production, dan nama project
Compose yang sudah digunakan agar container tetap memakai volume PostgreSQL
dan Redis yang sama. Docker, Spring Boot, database, dan Git tidak perlu dipasang ulang.

## 1. Periksa direktori proyek dan akses Git

Masuk menggunakan akun VPS yang biasa dipakai untuk deployment manual.
Nama akun ini akan menjadi nilai `VPS_USER` di GitHub. Jalankan pemeriksaan
berikut sekali saat menyiapkan otomatisasi:

```bash
cd /opt/artoku
git branch --show-current
git status --short
GIT_TERMINAL_PROMPT=0 GIT_SSH_COMMAND='ssh -o BatchMode=yes -o StrictHostKeyChecking=yes' git fetch origin
docker compose version
docker compose -f docker-compose.prod.yml ps
command -v curl
command -v flock
```

Branch aktif harus `release/v.0.1`, keluaran `git status --short` harus kosong,
dan fetch harus berhasil tanpa meminta password, passphrase, atau konfirmasi
host key. Jika branch aktif berbeda, selesaikan perubahan lokal sebelum beralih
ke `release/v.0.1`. Akun deployment memerlukan akses tulis ke direktori proyek
dan akses Docker tanpa permintaan `sudo` interaktif. Compose harus mendukung
`up --wait --wait-timeout`; pasang atau perbarui hanya alat yang belum tersedia.

Pertahankan `/opt/artoku/.env` yang sudah ada; tidak perlu menyalin ulang dari
`.env.example`. Gunakan izin akses `600`. Secrets aplikasi tetap berada di VPS;
pipeline tidak memindahkannya atau menghapus volume.

### Jika autentikasi Git yang ada perlu disesuaikan

Jika fetch tanpa interaksi di atas berhasil, pertahankan remote dan kredensial
Git saat ini, lalu lanjutkan ke langkah 2. Tidak perlu membuat deploy key
GitHub baru.

Jika fetch gagal karena VPS belum memiliki akses repository tanpa interaksi,
siapkan **deploy key GitHub dengan akses baca saja** untuk akun deployment.
Key ini berbeda dari key yang digunakan GitHub Actions untuk masuk ke VPS.
Gunakan nama file lain jika file key berikut sudah ada:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/artoku_github -C artoku-vps-github -N ''
```

Tambahkan isi `~/.ssh/artoku_github.pub` melalui **Settings → Deploy keys**
di repository, tanpa mengaktifkan akses tulis. Atur `~/.ssh/config` milik akun VPS:

```sshconfig
Host github.com
  HostName github.com
  User git
  IdentityFile ~/.ssh/artoku_github
  IdentitiesOnly yes
```

Jika menggunakan konfigurasi SSH ini, atur remote di dalam `/opt/artoku`:

```bash
cd /opt/artoku
git remote set-url origin git@github.com:szuryanailham/Personal-Finance-API.git
```

Verifikasi host key SSH GitHub menggunakan fingerprint resmi sebelum
menambahkannya ke `known_hosts` akun VPS. Pastikan `git fetch origin` berhasil
tanpa meminta masukan.

## 2. Buat key untuk akses GitHub Actions ke VPS

Di komputer lokal yang tepercaya, buat key khusus:

```bash
ssh-keygen -t ed25519 -f ./artoku_actions -C artoku-github-actions -N ''
```

Tambahkan public key (`artoku_actions.pub`) ke `~/.ssh/authorized_keys` milik
akun deployment di VPS. Gunakan izin `700` untuk direktori `.ssh` dan `600`
untuk `authorized_keys`. Jangan masukkan private key ke repository.

Ambil host key SSH VPS melalui konsol VPS yang tepercaya. Contoh:

```bash
sudo cat /etc/ssh/ssh_host_ed25519_key.pub
sudo ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
```

Susun entri `known_hosts` menggunakan public key yang sudah diverifikasi:

```text
YOUR_VPS_HOST ssh-ed25519 AAAA...verified-key...
```

Ganti `YOUR_VPS_HOST` dengan hostname atau IP VPS, dan bagian key dengan public
key sebenarnya. Untuk port SSH selain port standar, gunakan bentuk
`[YOUR_VPS_HOST]:2222` pada kolom pertama, dengan port yang sesuai.
Hostname/IP harus sama persis dengan nilai `VPS_HOST`. Jangan menonaktifkan
verifikasi host SSH atau mempercayai hasil `ssh-keyscan` yang belum diverifikasi.

## 3. Konfigurasikan GitHub

Buat environment melalui **Settings → Environments → production** di repository.
Batasi branch deployment ke `release/v.0.1`. Biarkan pengaturan required reviewers
nonaktif jika deployment harus berjalan otomatis setelah pengujian.
Tambahkan secrets berikut pada environment tersebut:

| Secret | Nilai |
| --- | --- |
| `VPS_HOST` | Alamat IPv4 atau hostname DNS VPS |
| `VPS_PORT` | Port SSH; boleh dikosongkan untuk menggunakan `22` |
| `VPS_USER` | Nama akun VPS yang biasa digunakan untuk deployment manual |
| `VPS_SSH_KEY` | Isi lengkap private key `artoku_actions`, termasuk baris pembuka dan penutup |
| `VPS_KNOWN_HOSTS` | Entri `known_hosts` yang sudah diverifikasi pada langkah 2 |
| `VPS_APP_DIR` | `/opt/artoku` |

Pastikan firewall VPS mengizinkan akses SSH dari runner GitHub Actions.
Jika VPS berada di jaringan privat atau firewall hanya menerima IP sumber
tertentu, gunakan runner dengan akses jaringan yang sesuai dan sesuaikan `runs-on`.

Commit workflow, script, dan perubahan Compose, lalu buat PR ke `release/v.0.1`.
Setelah workflow PR pertama berjalan, atur ruleset branch yang mewajibkan PR
dan pemeriksaan status **Tests** sebelum merge. Push langsung juga memicu
deployment, sehingga aturan branch diperlukan agar perubahan hanya masuk
melalui merge PR.

## 4. Jalankan dan periksa deployment

Merge PR, lalu buka **Actions → Artoku release** di repository. Tahap `Tests`
harus berhasil sebelum `Deploy VPS` dimulai. Deployment dijalankan bergantian;
proses dalam antrean akan dilewati jika commit-nya sudah bukan commit terbaru
pada branch release. Commit terbaru harus lulus pengujiannya sendiri sebelum
dapat di-deploy.

Script VPS menolak direktori proyek yang memiliki perubahan lokal atau riwayat
yang menyimpang. Script mengambil branch release, melakukan fast-forward ke
commit yang sudah diuji, membangun image API, lalu menjalankan Compose production
di `/opt/artoku`. Pembaruan memilih commit yang benar-benar lulus CI.
Proses build dan menjalankan container dipisahkan agar kegagalan build tidak
mengganti API yang sedang berjalan:

```bash
# Dijalankan otomatis setelah memperbarui proyek ke commit yang sudah diuji:
cd /opt/artoku
docker compose -f docker-compose.prod.yml build api
docker compose -f docker-compose.prod.yml up -d --no-build --wait --wait-timeout 180
```

Script menunggu pemeriksaan kesehatan PostgreSQL/Redis, lalu memeriksa respons
API melalui endpoint login yang hanya menerima POST; permintaan GET harus
menghasilkan status 405. Pemeriksaan singkat ini memastikan API sudah merespons
HTTP, tetapi tidak memantau kesehatan database, Redis, atau SMTP secara terus-menerus.

Deployment tidak menjalankan `compose down`. Penggantian container API dapat
menyebabkan jeda layanan singkat. Jika build gagal, container yang ada tetap
berjalan. Jika startup atau migrasi gagal, job ditandai gagal tanpa memulihkan
versi sebelumnya secara otomatis. Periksa kondisi di VPS dengan:

```bash
cd /opt/artoku
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=100 api
```

Periksa log sebelum membagikannya karena log aplikasi dapat memuat data sensitif.
Cadangkan PostgreSQL production sebelum merilis perubahan skema. Untuk memulihkan
rilis bermasalah, biasanya buat PR yang membatalkan perubahan kode, lalu biarkan CI
melakukan deployment. Pembatalan migrasi database memerlukan rencana pemulihan
tersendiri yang sudah ditinjau.

## Referensi

- [Environment deployment dan pengaturan proses bersamaan di GitHub](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/control-deployments)
- [Action untuk menyiapkan Java](https://github.com/actions/setup-java)
- [Opsi up dan wait pada Docker Compose](https://docs.docker.com/reference/cli/docker/compose/up/)
- [Fingerprint key SSH GitHub](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/githubs-ssh-key-fingerprints)
