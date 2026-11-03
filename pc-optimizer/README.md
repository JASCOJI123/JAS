# Ultimate PC Optimizer — Gaming Booster

Windows 10 / 11 (x64) uchun PC Performance Management va Gaming Optimization dasturi.
C# **.NET 10**, **WPF** (dark neon gaming UI), **SQLite**, **WMI**, **Performance Counters**, **Windows API**, **LibreHardwareMonitor**.

> Asosiy tamoyil: har bir o'zgarish **ruxsat bilan**, **jurnalga yozilgan**, **qaytariladigan** bo'lishi kerak.

---

## Arxitektura

```
pc-optimizer/
├── src/
│   ├── UltimatePcOptimizer.Core       ← sof biznes-mantiq (net10.0, Windows'siz test qilinadi)
│   │   ├── Abstractions/   interfeyslar (monitor, process, startup, cleanup, registry, power, restore…)
│   │   ├── Changes/        Change Engine: PlannedChange → journal → apply → Undo
│   │   ├── Safety/         xavfsizlik siyosatlari (registry allow-list, himoyalangan jarayonlar, tozalash yo'llari)
│   │   ├── Optimization/   One Click qadamlari, tweak katalogi, rejimlar, Automatic Optimization
│   │   ├── Gaming/         o'yin aniqlash, Gaming Booster sessiyasi (avtomatik tiklash bilan)
│   │   ├── Monitoring/     monitoring servisi, FPS statistikasi (avg, 1% low, P99), benchmark
│   │   ├── Analysis/       Health Score, throttling, RAM bosimi, tavsiyalar, grafik profil
│   │   ├── Games/          o'yinlar katalogi (anti-cheat va Reflex belgilari bilan)
│   │   └── Parsing/        Steam VDF, Epic, Riot, powercfg, PresentMon CSV parserlari
│   ├── UltimatePcOptimizer.Data       ← SQLite (Microsoft.Data.Sqlite), migratsiyalar, repository'lar
│   ├── UltimatePcOptimizer.Platform   ← Windows implementatsiyalari (WMI, P/Invoke, LHM, registry, powercfg, DISM)
│   └── UltimatePcOptimizer.App        ← WPF UI (MVVM, CommunityToolkit.Mvvm, DI host)
├── tests/UltimatePcOptimizer.Tests    ← xUnit: Core + Data (real SQLite bilan)
├── installer/UltimatePcOptimizer.iss  ← Inno Setup x64 installer
├── scripts/publish.ps1                ← test → publish → installer
└── tools/                             ← PresentMon.exe shu yerga (FPS monitoring uchun)
```

Qatlamlar bir tomonlama bog'langan: `App → Platform/Data → Core`. Core hech qanday Windows API'ga bog'liq emas,
shuning uchun barcha xavfsizlik va hisoblash mantiqi CI'da Linux/Windows'da test qilinadi.

### Change Engine (Undo asosi)

Dastur tizimni **faqat** `ChangeExecutor` orqali o'zgartiradi:

1. `PlannedChange` — taklif (registry qiymati, power plan, startup, jarayon ustuvorligi, fayl tozalash).
2. Foydalanuvchi har bir o'zgarishni alohida tanlaydi va tasdiqlaydi (`IConsentService` → dialog).
3. Muhim o'zgarishlardan oldin **Windows Restore Point** yaratiladi.
4. Handler oldingi holatni o'qiydi → yozuv **jurnalga Pending** holatda saqlanadi → keyin o'zgarish qo'llanadi.
5. **Undo** guruhdagi barcha o'zgarishlarni teskari tartibda aynan oldingi qiymatga qaytaradi
   (qiymat avval yo'q bo'lgan bo'lsa — o'chiriladi).

Dastur o'zgarish o'rtasida yopilib qolsa ham jurnal saqlanib qoladi; tugallanmagan Gaming Booster
sessiyalari keyingi ishga tushishda avtomatik tiklanadi (shuningdek Windows'dan chiqishda/o'chirishda).

## Funksiyalar (TZ bo'yicha)

| Bo'lim | Nima qiladi |
|---|---|
| **Dashboard** | CPU/GPU/RAM/Disk yuklanishi (animatsiyali gauge), CPU/GPU harorati, holat, Windows yuklanish vaqti, uptime, jarayonlar soni, 2 daqiqalik grafiklar, System Health Score, tavsiyalar |
| **One Click Optimize** | Temp fayllar (WER, Windows Temp, User Temp), Startup tahlili, og'ir fon jarayonlari, Power Plan, Game Mode, Game DVR, animatsiyalar → har biri alohida checkbox, restore point, natija |
| **Optimization Modes** | Gaming, Laptop, Desktop, Balanced, Maximum Performance, Custom |
| **Gaming Booster** | O'yin ishga tushganda avtomatik Gaming Mode: power plan, bildirishnomalarni vaqtincha o'chirish, Game Mode, o'yin ustuvorligi, fon dasturlari ustuvorligini pasaytirish; o'yin yopilganda hammasi tiklanadi |
| **FPS Booster** | GPU/vendor/drayver, HAGS holati, NVIDIA Reflex/AMD Anti-Lag tavsiyasi, o'yin uchun grafik profil, FPS'ga ta'sir qilayotgan jarayonlar |
| **Game Library** | Steam, Epic Games, Riot o'yinlarini aniqlash, qo'lda qo'shish, profil yaratish, o'yin uchun yuqori unumli GPU |
| **FPS Monitoring** | FPS, Average, 1% Low, Frametime, P99, CPU/GPU yuklanish va harorat, RAM, VRAM, in-game overlay, benchmark va oldin/keyin solishtirish |
| **CPU / GPU / RAM** | Monitoring, model/yadro/oqim, throttling aniqlash, Power Plan boshqaruvi, VRAM, drayver, pagefile, RAM bosimi |
| **Background Processes** | Jarayonlar ro'yxati, ustuvorlikni o'zgartirish, tasdiq bilan yopish |
| **Windows Cleaner** | Temp, Windows/User Temp, DirectX Shader Cache, Thumbnail Cache, Recycle Bin, Windows Update Cleanup (DISM), Browser Cache — har birining hajmi |
| **Startup Manager** | Nomi, ta'siri, RAM, joylashuvi, Enable/Disable (Task Manager mexanizmi), yuklanish vaqti |
| **Restore & History** | Restore Point yaratish, Windows System Restore, Optimization History, Undo |
| **Auto PC Detection** | CPU, GPU, RAM, motherboard, Windows, disk turi (HDD/SSD/NVMe), drayverlar, rezolyutsiya, refresh rate + tavsiyalar |
| **Network** | Ulanish diagnostikasi (adapter, gateway, DNS, internet), latency/jitter/packet loss monitoring |
| **Settings** | Automatic Optimization, restore point, yangilanishlar, latency hostlari |

## Xavfsizlik kafolatlari

Kod darajasida majburlanadi (va testlar bilan tekshiriladi):

- **Registry allow-list** (`Core/Safety/RegistryPolicy.cs`): dastur faqat ro'yxatdagi hujjatlashtirilgan
  HKCU qiymatlarini (Game Mode, Game DVR, bildirishnomalar, animatsiyalar, per-app GPU preference) yoza oladi.
  Windows Defender, Windows Update, servislar va boshqa har qanday kalit — rad etiladi.
- **Himoyalangan jarayonlar** (`ProcessSafetyPolicy`): tizim (session 0 dahil), antivirus va **anti-cheat**
  (Vanguard, EasyAntiCheat, BattlEye, FACEIT, Ricochet…) jarayonlari hech qachon o'zgartirilmaydi yoki yopilmaydi.
  Kernel anti-cheat'li o'yinlarning jarayoniga ham tegilmaydi. Realtime ustuvorlik taqiqlangan.
- **RAM** sun'iy "tozalanmaydi", faol dasturlar majburan yopilmaydi.
- **Tozalash** (`CleanupPathPolicy`): faqat aniq temp/kesh papkalari ichida; drive root, `C:\Windows`,
  `Program Files`, user profili ildizi taqiqlangan; symlink/junction'larga kirilmaydi; temp fayllar 24 soatdan
  eski bo'lishi kerak; band fayllar o'tkazib yuboriladi. Brauzer keshi faqat brauzer yopiq bo'lsa va rozilik bilan.
  Shader cache One Click'ga kiritilmagan (keyin o'yinlarda stutter beradi).
- **Startup**: Windows Security, kiritish, audio/touchpad drayverlari va anti-cheat startup'lari o'chirilmaydi.
  O'chirish Task Manager'dagi `StartupApproved` mexanizmi orqali — asl yozuv saqlanadi.
- **Drayverlar** o'zgartirilmaydi, **overclock** qo'llanmaydi, **o'yin fayllari** o'zgartirilmaydi.
- Tashqi vositalar (`powercfg`, `dism`, `wevtutil`) faqat `System32` dan, shell'siz, qat'iy argumentlar bilan ishga tushiriladi.
- Avtomatik rejim faqat foydalanuvchi yoqsa va faqat xavfsiz temp fayllarni tozalaydi — tizim sozlamalarini hech qachon avtomatik o'zgartirmaydi.

## Ishga tushirish (dasturchi uchun)

Talablar: Windows 10/11 x64, [.NET 10 SDK](https://dotnet.microsoft.com/download/dotnet/10.0), Visual Studio 2026 yoki Rider (ixtiyoriy).

```powershell
cd pc-optimizer
dotnet test tests/UltimatePcOptimizer.Tests
dotnet run --project src/UltimatePcOptimizer.App
```

Dastur administrator huquqini so'raydi (`app.manifest`): restore point, HKLM startup, Windows Temp, sensorlar va PresentMon uchun kerak.

Ma'lumotlar: `%LOCALAPPDATA%\UltimatePcOptimizer\` — `optimizer.db` (jurnal, profillar, benchmarklar, sozlamalar) va `logs\`.

### FPS Monitoring

[Intel PresentMon](https://github.com/GameTechDev/PresentMon/releases) console versiyasini `tools/` papkasiga qo'ying
(batafsil: `tools/README.md`). U bo'lmasa FPS bo'limi sababini ko'rsatadi, qolgan funksiyalar ishlayveradi.
Overlay borderless/windowed rejimda ko'rinadi (exclusive fullscreen ustida emas) — bu inject qilmaslikning narxi.

### Sensorlar

GPU sensorlari (NVIDIA/AMD/Intel) LibreHardwareMonitor orqali to'g'ridan-to'g'ri ishlaydi. CPU harorati uchun
LibreHardwareMonitor low-level drayveri kerak; u mavjud bo'lmasa ACPI thermal zone ishlatiladi yoki "N/A" ko'rsatiladi.

## Release va installer

```powershell
./scripts/publish.ps1 -Version 1.0.0          # test → self-contained publish → Inno Setup installer
```

Natija: `artifacts/installer/UltimatePcOptimizer-Setup-1.0.0-x64.exe` (Inno Setup 6 kerak).

Muqobil — NSIS (Windows'da ham, Linux'da ham ishlaydi):

```bash
dotnet publish src/UltimatePcOptimizer.App -c Release -r win-x64 --self-contained true -p:Version=1.0.0 -p:DebugType=none -o artifacts/publish
cd installer && makensis -DAPP_VERSION=1.0.0 -DPUBLISH_DIR=../artifacts/publish -DOUT_DIR=../artifacts/installer UltimatePcOptimizer.nsi
```

CI (`.github/workflows/pc-optimizer.yml`, windows-latest): har push'da build + test + installer artifact.
`pc-optimizer-v1.2.0` tegi push qilinganda GitHub Release yaratiladi va installer biriktiriladi.

**Avtomatik yangilanish:** `appsettings.json` → `Updates:Repository` ga `owner/repo` yozing. Dastur ishga tushganda
GitHub Releases'ni tekshiradi va yangi versiya haqida xabar beradi; foydalanuvchi installer'ni o'zi yuklab ishga tushiradi
(jim o'rnatish yo'q).

## Kengaytirish

- **Yangi tweak**: `TweakCatalog` ga qo'shing va qiymatni `RegistryPolicy` allow-list'iga kiriting (test buni tekshiradi).
- **Yangi o'yin**: `GameCatalog.Games` ga exe nomlari, anti-cheat va Reflex belgilari bilan.
- **Yangi One Click qadami**: `IOptimizationStep` implementatsiyasi + `ServiceRegistration.AddCore`.
- **Yangi o'zgarish turi**: `ChangeKind` + `IChangeHandler` (Validate/Capture/Apply/Revert).
- **Yangi DB jadvali**: `Migrations.All` ga yangi versiyali migratsiya qo'shing (eskilarini o'zgartirmang).
