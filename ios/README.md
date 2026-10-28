# Montaj — iPhone uchun video montaj ilovasi

SwiftUI va AVFoundation'da yozilgan native iOS ilova (iOS 16 va undan yuqori). Video vertikal 9:16 formatda (Reels, TikTok, Shorts) chiqadi.

## Imkoniyatlar

- **Kliplar:** galereyadan bir nechta videoni tanlash, ketma-ket ulash, joyini o'zgartirish, o'chirish
- **Qirqish:** har bir klipning boshi va oxirini kesish
- **Tezlik:** 0.25x – 3x (ovoz ham tezlik bilan birga o'zgaradi, tovush balandligi buzilmaydi)
- **Ovoz:** asl ovoz balandligi yoki uni o'chirish, fon musiqasi (Fayllar ilovasidan) va uning balandligi. Musiqa qisqa bo'lsa takrorlanadi, video oxirida sekin pasayadi
- **Matn / subtitr:** bir nechta yozuv qo'shish mumkin, har birining vaqti, joyi, o'lchami, rangi va orqa foni sozlanadi
- **Filtrlar:** Yorqin, Iliq, Sovuq, Xira, Retro, Xrom, Oq-qora, Nuar
- **Saqlash:** 1080×1920 .mp4 fayl galereyaga saqlanadi, "Ulashish" tugmasi ham bor

Gorizontal videolar 9:16 kadrga to'liq sig'diriladi, bo'sh joy xira fon bilan to'ldiriladi.

## iPhone'ga o'rnatish (Windows orqali, Mac kerak emas)

### 1. .ipa faylini yuklab olish
1. GitHub'da repozitoriyning **Actions** bo'limiga kiring.
2. **"iOS ilova (Montaj) — .ipa yig'ish"** workflow'ining oxirgi yashil (✅) ishga tushishini oching.
3. Pastdagi **Artifacts** bo'limidan **Montaj-ipa** ni yuklab oling va zip arxivni oching. Ichida `Montaj.ipa` bor.

### 2. Windows'da o'rnatish (Sideloadly)
1. Apple saytidan **iTunes** va **iCloud** ni o'rnating (Microsoft Store versiyasi emas, Apple saytidagi versiya).
2. [sideloadly.io](https://sideloadly.io) dan **Sideloadly** ni yuklab o'rnating.
3. iPhone'ni kabel bilan ulang va telefonda "Bu kompyuterga ishonish" ni bosing.
4. Sideloadly'da `Montaj.ipa` ni tanlang, Apple ID'ingizni kiriting va **Start** ni bosing.
5. iPhone'da: **Sozlamalar → Umumiy → VPN va qurilmalarni boshqarish** bo'limiga kiring, Apple ID'ingizni tanlab **Ishonish** ni bosing.
6. **Sozlamalar → Maxfiylik va xavfsizlik → Developer Mode** ni yoqing (telefon qayta yonadi).

> Bepul Apple ID bilan o'rnatilgan ilova **7 kun** ishlaydi. Keyin Sideloadly'da yana **Start** ni bosish kifoya. Ilovadagi ma'lumotlar o'chmaydi.

## Texnik tuzilma

```
ios/
├── project.yml              ← XcodeGen loyiha tavsifi (.xcodeproj CI'da yaratiladi)
├── Resources/               ← ikonka, rang
└── Sources/
    ├── App/                 ← ilovaga kirish nuqtasi
    ├── Models/              ← Clip, TextOverlay, VideoFilter, EditorSnapshot
    ├── Engine/              ← kompozitsiya, kadr kompozitori, matn renderi, eksport
    ├── Editor/              ← EditorViewModel (holat va amallar)
    ├── Views/               ← SwiftUI ekranlar
    └── Support/             ← import, formatlash, xatolar
```

Ko'rish (preview) va eksport bir xil `MontajCompositor` orqali chiziladi, shuning uchun ekranda ko'ringan narsa tayyor videoda ham aynan shunday chiqadi.

## Mac'da ishlab chiqish (ixtiyoriy)

```bash
brew install xcodegen
cd ios && xcodegen generate && open Montaj.xcodeproj
```
