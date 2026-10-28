# Montaj — Android uchun video montaj va Motion ilovasi

Kotlin, Jetpack Compose va Media3 (ExoPlayer va Transformer) asosida yozilgan. Android 10 va undan yuqori versiyalarda ishlaydi. Funksiyalari iOS versiyasi bilan bir xil, video vertikal **9:16 (1080×1920)** formatda chiqadi.

## APK ni telefonga o'rnatish

1. GitHub'da repozitoriyning **Actions** bo'limiga kiring.
2. **"Android ilova (Montaj) — APK yig'ish"** workflow'ining oxirgi yashil (✅) ishga tushishini oching.
3. Pastdagi **Artifacts** bo'limidan **Montaj-apk** ni yuklab oling (zip ichida `Montaj.apk` bor).
4. Faylni telefonga o'tkazing (yoki to'g'ridan-to'g'ri telefondan yuklab oling) va oching.
5. "Noma'lum manbalardan o'rnatish" so'ralsa, brauzer yoki fayl menejeriga ruxsat bering.

Kompyuter shart emas va iOS'dagi kabi 7 kunlik cheklov yo'q. Keyingi versiyalar eskisining ustiga o'rnatiladi.

## Montaj

- Bir nechta videoni ulash, joyini almashtirish, boshini va oxirini qirqish
- Tezlik: 0.25x – 3x (tovush balandligi buzilmaydi)
- Asl ovoz balandligi, fon musiqasi (qisqa bo'lsa takrorlanadi)
- Vaqtga bog'langan matn va subtitrlar: joyi, o'lchami, rangi, orqa foni
- Filtrlar: Yorqin, Iliq, Sovuq, Xira, Retro, Xrom, Oq-qora, Nuar
- Tayyor video galereyaga (`Movies/Montaj`) saqlanadi, "Ulashish" tugmasi bor

## Motion (mini After Effects)

- Qatlamlar: Video, Rasm, Matn, Shakl, Solid. Ko'rinishi, tartibi, nusxasi va In/Out vaqtini sozlash mumkin
- Keyframe: Joyi, O'lcham, Burilish, Shaffoflik va effekt miqdori (⏱ / ◆)
- Easing: Chiziqli, Ease In, Ease Out, Easy Ease, Overshoot, Hold
- Ko'rish oynasida qatlamni barmoq bilan surish, ikki barmoq bilan kattalashtirish va burish
- Matn animatsiyalari: Yozuv mashinkasi, Harfma-harf, Pastdan chiqish, Sakrab chiqish, To'lqin
- Effektlar: Blur, Glow, Chromatic aberration, Shake, Yorqinlik, To'yinganlik, Hue, Vignette
- Mask: to'rtburchak yoki ellips, feather, teskari
- Blend mode: Normal, Add, Screen, Multiply, Overlay, Soft Light, Lighten, Darken, Difference

Cheklovlar:
- Motion rejimida video qatlamlar ovozsiz chiqadi.
- Ijro paytida video qatlamlarning ko'rinishi silliq bo'lmasligi mumkin, tayyor eksport esa aniq chiqadi.
- Loyiha ilova yopilganda saqlanmaydi.

## Tuzilma

```
android/app/src/main/java/com/jas/montaj/
├── MainActivity.kt          ← Montaj / Motion tab'lari
├── common/                  ← import, galereya, eksport (Transformer), umumiy UI
├── montaj/                  ← modellar, Media3 kompozitsiya, ViewModel, ui/
└── motion/
    ├── model/               ← Animatable/Keyframe/Easing, qatlam, effekt, mask
    ├── engine/              ← Canvas renderer, matn/shakl, effektlar, eksport
    └── ui/                  ← ko'rish oynasi, timeline, inspektor
```

Ko'rish oynasi va eksport kadrlarni bir xil renderer bilan chizadi (`TextOverlayPainter`, `MotionRenderer`).
