# Montaj v2 — iOS va Android uchun bitta kod (Kotlin Multiplatform)

Video montaj va "mini After Effects" (Motion) ilovasi. UI, mantiq va **kadr chizuvchi renderer** hammasi umumiy kodda yozilgan, shuning uchun iPhone va Android'da bir xil ishlaydi. Tayyor video ham ikkala telefonda piksel darajasida bir xil chiqadi.

## Yuklab olish

| Platforma | Havola |
|---|---|
| Android (APK) | https://github.com/JASCOJI123/JAS/releases/download/montaj-v2-android-latest/Montaj.apk |
| iPhone (IPA) | https://github.com/JASCOJI123/JAS/releases/download/montaj-v2-ios-latest/Montaj.ipa |

- **Android:** APK'ni telefonda oching va "noma'lum manbalardan o'rnatish"ga ruxsat bering.
- **iPhone:** IPA'ni Windows'da **Sideloadly** orqali o'rnating (bepul Apple ID, har 7 kunda qayta o'rnatiladi).

## Imkoniyatlar

### Umumiy
- 💾 **Loyihalar avtomatik saqlanadi.** Ilova yopilsa ham ish yo'qolmaydi. 📁 tugmasida loyihalar ro'yxati bor: yangi loyiha ochish, nomini o'zgartirish, o'chirish
- ↩️ **Undo / Redo** (slider surishlar bitta qadamga birlashtiriladi)

### Montaj
- Kliplarni ulash, qirqish, tartibini o'zgartirish, tezlik (0.25x – 3x)
- 🎬 **O'tish effektlari:** Crossfade, Qora orqali, Surish, Yuqoriga, Zoom, Wipe, Glitch. Pauzada chegaraga sursangiz ko'rinadi
- 🎵 Fon musiqasi (takrorlanadi), asl ovoz balandligi
- 🎤 **Ovoz yozish (voiceover):** videoning joriy joyidan yoziladi, balandligini sozlash mumkin
- 📝 Matn va subtitrlar, **avto-subtitr** (OpenAI Whisper, o'zbek tili ham)
- Filtrlar: Yorqin, Iliq, Sovuq, Xira, Retro, Xrom, Oq-qora, Nuar

### Motion (mini After Effects)
- Qatlamlar: video (ovozi bilan), rasm, matn, shakl, solid
- Keyframe: joyi, o'lchami, burilishi, shaffofligi va effekt miqdori. Easing ham bor
- 📈 **Graph editor:** keyframe'lar orasidagi egri chiziqni tutqichlar bilan chizish (cubic-bezier, overshoot, anticipate)
- 🔗 **Parenting:** qatlamni boshqa qatlamga bog'lash (null object kabi)
- 〰️ **Wiggle:** joy, burilish va o'lchamda silliq tebranish. **Loop** (loopOut): animatsiyani takrorlash
- ✨ **Animatsiya presetlari:** Paydo bo'lish, Sakrab chiqish, Zoom, O'ngdan/Chapdan/Pastdan kirish, Tushish, Aylanish, Sakrash, Puls
- 🧩 **Shablonlar:** Intro sarlavha, Lower third, "Obuna bo'ling", 3-2-1 sanoq
- Matn animatsiyalari, effektlar (Blur, Glow, Chromatic, Shake, rang, Vignette), mask, blend mode
- Barmoq bilan surish, kattalashtirish va burish

## Avto-subtitr haqida

Nutqni matnga aylantirish uchun **OpenAI Whisper** ishlatiladi. Telefonlarning o'z nutq tanish tizimi o'zbek tilini ishonchli tanimaydi.

1. https://platform.openai.com → API keys bo'limida kalit oching.
2. Ilovada: **Matn → Avto-subtitr**, kalitni kiriting va tilni tanlang.

Kalit faqat telefoningizda saqlanadi. Narxi taxminan **1 daqiqa nutq uchun ~0.006$**.

## Tuzilma

```
mobile/
├── shared/src/
│   ├── commonMain/      ← UMUMIY KOD (iOS + Android)
│   │   ├── core/        ← formatlash, Undo/Redo, loyihalarni saqlash, subtitr
│   │   ├── montaj/      ← Montaj modellari va editori
│   │   ├── motion/      ← keyframe, bezier, parenting, wiggle, presetlar, shablonlar
│   │   ├── render/      ← kadr renderer (matn, effekt, mask, o'tishlar)
│   │   ├── platform/    ← platforma interfeyslari (expect)
│   │   └── ui/          ← barcha ekranlar (Compose Multiplatform)
│   ├── androidMain/     ← Media3 (pleyer, eksport), MediaRecorder, fayllar
│   └── iosMain/         ← AVFoundation (pleyer, AVAssetWriter), PHPicker, Photos
├── androidApp/          ← Android ilova qobig'i (MainActivity)
└── iosApp/              ← iOS ilova qobig'i (SwiftUI → Compose)
```

Yangi funksiya qo'shganda kod `commonMain` ga bir marta yoziladi va ikkala ilovada paydo bo'ladi. Platformaga xos qism faqat video o'qish, yozish va saqlash.

## Yig'ish

GitHub Actions: `.github/workflows/mobile-android.yml` (Ubuntu) va `mobile-ios.yml` (macOS). Har push'dan keyin APK va IPA yuqoridagi havolalarga yangilanadi.
