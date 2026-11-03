# tools

FPS Monitoring uchun **Intel PresentMon** (console, MIT litsenziyasi — `PresentMon-LICENSE.txt`).

- **CI** (`.github/workflows/pc-optimizer.yml`) PresentMon `v2.6.0` ni rasmiy GitHub release'dan yuklab,
  installer ichiga avtomatik qo'shadi (`<o'rnatish papkasi>\tools\`).
- **Lokal build** uchun https://github.com/GameTechDev/PresentMon/releases dan `PresentMon-<versiya>-x64.exe` ni
  shu papkaga qo'ying — build vaqtida chiqish papkasidagi `tools/` ga nusxalanadi.

PresentMon Windows ETW present eventlarini kuzatadi va o'yinga hech narsa inject qilmaydi.
`.exe` fayllar git'ga commit qilinmaydi.
