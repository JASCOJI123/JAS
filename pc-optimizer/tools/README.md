# tools

FPS Monitoring uchun **Intel PresentMon** (console versiyasi) shu papkaga joylanadi:

1. https://github.com/GameTechDev/PresentMon/releases sahifasidan `PresentMon-<versiya>-x64.exe` ni yuklab oling.
2. Faylni shu papkaga (`pc-optimizer/tools/`) qo'ying — build vaqtida avtomatik `tools/` ga nusxalanadi.

PresentMon Windows ETW present eventlarini kuzatadi va o'yinga hech narsa inject qilmaydi — anti-cheat tizimlari bilan xavfsiz.
`.exe` fayllar git'ga commit qilinmaydi (`.gitignore`); CI ularni release vaqtida yuklab oladi.
