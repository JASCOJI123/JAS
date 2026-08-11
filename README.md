# AI STUDIO — landing sayt

AI rasm/video xizmatlarini sotuvchi landing sayt. Statik HTML/CSS/JS, GitHub Pages orqali deploy qilinadi.

## Struktura

```
ai-studio/
├── index.html
├── style.css
├── script.js
├── assets/
│   ├── images/{models,portfolio}/   ← model va portfolio rasmlari shu yerga (CDN'ga yuklab, index.html'da URL bilan almashtiring)
│   └── icons/
└── .github/workflows/pages.yml      ← avtomatik deploy
```

## Ishga tushirish

1. `Settings → Pages → Source: GitHub Actions` yoqing.
2. `main` branchga push qiling — sayt avtomatik deploy bo'ladi.

## Media qo'shish (MUHIM: base64 taqiqlangan)

Hozircha portfolio va model bloklari — placeholder (gradient) div'lar. Haqiqiy rasm/video tayyor bo'lganda:

1. Faylni CDN'ga (Cloudflare R2 / Cloudinary / Bunny CDN) yuklang.
2. `index.html` ichida tegishli `.portfolio-item` yoki `.model-card` blokini quyidagiga almashtiring:

```html
<img src="https://cdn.example.com/images/portfolio/01.webp" loading="lazy" decoding="async" alt="...">
```

```html
<video preload="metadata" poster="https://cdn.example.com/images/video-01.webp" muted loop playsinline>
  <source src="https://cdn.example.com/videos/video-01.mp4" type="video/mp4">
</video>
```

Hero rasmini ham xuddi shunday CDN'dan ulang (lazy loading kerak emas — birinchi ekranda ko'rinadi).

## Sozlash

`script.js` faylining boshida:

```js
const CONFIG = {
  TELEGRAM_USERNAME: "your_ai_studio_admin", // haqiqiy admin username bilan almashtiring
  PRICE_PHOTO: 50000,
  PRICE_VIDEO: 100000,
};
```

Footer'dagi Telegram/Instagram/telefon linklarini `index.html` footer bo'limida yangilang.

## Nima ishlaydi

- Model tanlash (bir nechta tanlash, orange border + check)
- Narx hisoblagich (+/− tugmalar, real-time jami summa)
- Portfolio tab'lari (Rasm / Video)
- "Buyurtma berish" — tanlangan xizmat/model/summani Telegramga tayyor xabar sifatida ochadi
- Mobil hamburger menyu
- To'liq responsive (mobil / tablet / desktop)
