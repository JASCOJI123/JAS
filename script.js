/* ============================================================
   AI STUDIO — script.js
   Config at top: change TELEGRAM_USERNAME once admin is known.
   ============================================================ */

const CONFIG = {
  TELEGRAM_USERNAME: "your_ai_studio_admin", // TODO: adminning haqiqiy Telegram username'i
  PRICE_PHOTO: 50000,
  PRICE_VIDEO: 100000,
};

// ---------- State ----------
const state = {
  photoCount: 0,
  videoCount: 0,
  selectedModels: new Set(),
};

// ---------- Helpers ----------
function formatSom(n) {
  return n.toLocaleString("ru-RU").replace(/,/g, " ") + " so'm";
}

// ---------- Mobile menu ----------
const hamburger = document.querySelector(".hamburger");
const mobileMenu = document.querySelector(".mobile-menu");
if (hamburger) {
  hamburger.addEventListener("click", () => {
    mobileMenu.classList.toggle("open");
  });
  mobileMenu.querySelectorAll("a").forEach((a) =>
    a.addEventListener("click", () => mobileMenu.classList.remove("open"))
  );
}

// ---------- Portfolio tabs ----------
const tabButtons = document.querySelectorAll(".tab-btn");
const panels = document.querySelectorAll(".portfolio-panel");
tabButtons.forEach((btn) => {
  btn.addEventListener("click", () => {
    tabButtons.forEach((b) => b.classList.remove("active"));
    panels.forEach((p) => p.classList.remove("active"));
    btn.classList.add("active");
    document.getElementById(btn.dataset.target).classList.add("active");
  });
});

// ---------- Model selection ----------
const modelCards = document.querySelectorAll(".model-card");
const modelCountEl = document.getElementById("modelCount");

modelCards.forEach((card) => {
  card.addEventListener("click", () => {
    const id = card.dataset.modelId;
    if (state.selectedModels.has(id)) {
      state.selectedModels.delete(id);
      card.classList.remove("selected");
    } else {
      state.selectedModels.add(id);
      card.classList.add("selected");
    }
    updateModelCount();
  });
});

function updateModelCount() {
  if (modelCountEl) {
    modelCountEl.innerHTML = `<strong>${state.selectedModels.size}</strong> ta model tanlandi`;
  }
}

// ---------- Price calculator ----------
const photoCountEl = document.getElementById("photoCount");
const videoCountEl = document.getElementById("videoCount");
const totalSumEl = document.getElementById("totalSum");

function updateTotal() {
  const total =
    state.photoCount * CONFIG.PRICE_PHOTO + state.videoCount * CONFIG.PRICE_VIDEO;
  photoCountEl.textContent = state.photoCount;
  videoCountEl.textContent = state.videoCount;
  totalSumEl.textContent = formatSom(total);
}

document.querySelectorAll("[data-step]").forEach((btn) => {
  btn.addEventListener("click", () => {
    const target = btn.dataset.target; // "photo" | "video"
    const dir = parseInt(btn.dataset.step, 10);
    if (target === "photo") {
      state.photoCount = Math.max(0, state.photoCount + dir);
    } else if (target === "video") {
      state.videoCount = Math.max(0, state.videoCount + dir);
    }
    updateTotal();
  });
});

updateTotal();
updateModelCount();

// ---------- Order → Telegram ----------
const orderBtn = document.getElementById("orderBtn");

function buildOrderMessage() {
  const lines = ["AI STUDIO BUYURTMA", "Xizmat:"];
  if (state.photoCount > 0) lines.push(`AI Rasm — ${state.photoCount} dona`);
  if (state.videoCount > 0) lines.push(`AI Video — ${state.videoCount} dona`);
  if (state.photoCount === 0 && state.videoCount === 0) {
    lines.push("(hali tanlanmagan)");
  }

  if (state.selectedModels.size > 0) {
    lines.push("Model:");
    state.selectedModels.forEach((id) => lines.push(`Model ${id}`));
  }

  const total =
    state.photoCount * CONFIG.PRICE_PHOTO + state.videoCount * CONFIG.PRICE_VIDEO;
  lines.push("Jami:");
  lines.push(formatSom(total));

  return lines.join("\n");
}

if (orderBtn) {
  orderBtn.addEventListener("click", () => {
    const message = buildOrderMessage();
    const url = `https://t.me/${CONFIG.TELEGRAM_USERNAME}?text=${encodeURIComponent(
      message
    )}`;
    window.open(url, "_blank");
  });
}

// ---------- Package order buttons ----------
document.querySelectorAll("[data-package]").forEach((btn) => {
  btn.addEventListener("click", () => {
    const pkg = btn.dataset.package;
    const details = {
      start: "Boshlang'ich paket — 5 ta AI rasm + 2 ta AI video — 390 000 so'm",
      standard: "Standart paket — 10 ta AI rasm + 5 ta AI video — 850 000 so'm",
      premium: "Premium paket — 20 ta AI rasm + 10 ta AI video — 1 650 000 so'm",
    };
    const message = `AI STUDIO BUYURTMA (oylik paket)\n${details[pkg]}`;
    const url = `https://t.me/${CONFIG.TELEGRAM_USERNAME}?text=${encodeURIComponent(
      message
    )}`;
    window.open(url, "_blank");
  });
});
