const tutorialPages = [
  {
    pill: "Tutorial 1 of 4",
    title: "What NullByte does",
    text: "NullByte creates a fresh share-ready copy of your photo without the common metadata that can reveal more than you intended.",
    points: [
      "Scans common GPS, camera, time, and author-style tags locally.",
      "Explains the flow in simple language before the user does anything.",
    ],
  },
  {
    pill: "Tutorial 2 of 4",
    title: "What NullByte does not do",
    text: "The app is clear about its limits so users know exactly what they can trust it to handle.",
    points: [
      "It does not upload files or require an account.",
      "It does not overwrite or delete the original image.",
    ],
  },
  {
    pill: "Tutorial 3 of 4",
    title: "How to use it",
    text: "The core flow stays short on purpose so the user never has to guess what happens next.",
    points: [
      "Pick or share in photos.",
      "Review the local scan summary.",
      "Sanitize and share the clean copy instead.",
    ],
  },
  {
    pill: "Tutorial 4 of 4",
    title: "Optional reminders",
    text: "Local notifications are there if the user wants a nudge before posting sensitive images online.",
    points: [
      "Reminder notifications stay on-device.",
      "Each notification can open NullByte or reopen the tutorial.",
    ],
  },
];

const state = {
  screen: "onboarding",
  device: "phone",
  tutorialIndex: 0,
  remindersOn: true,
};

const screenTabs = Array.from(document.querySelectorAll("#screen-tabs .tab"));
const deviceTabs = Array.from(document.querySelectorAll("#device-tabs .tab"));
const screens = Array.from(document.querySelectorAll(".screen"));
const device = document.getElementById("device");
const previewToggle = document.getElementById("preview-toggle");
const tutorialPrev = document.getElementById("tutorial-prev");
const tutorialNext = document.getElementById("tutorial-next");
const tutorialDots = document.getElementById("tutorial-dots");
const onboardingScreen = document.querySelector(".screen-onboarding");

function renderTutorialDots() {
  tutorialDots.innerHTML = "";
  tutorialPages.forEach((_, index) => {
    const dot = document.createElement("span");
    dot.className = `tutorial-dot${index === state.tutorialIndex ? " is-current" : ""}`;
    tutorialDots.appendChild(dot);
  });
}

function renderTutorialPage() {
  const page = tutorialPages[state.tutorialIndex];
  onboardingScreen.querySelector(".screen-pill").textContent = page.pill;
  onboardingScreen.querySelector(".screen-title").textContent = page.title;
  onboardingScreen.querySelector(".screen-text").textContent = page.text;

  const list = onboardingScreen.querySelector(".bullet-list");
  list.innerHTML = "";

  page.points.forEach((point) => {
    const item = document.createElement("li");
    item.textContent = point;
    list.appendChild(item);
  });

  tutorialPrev.disabled = state.tutorialIndex === 0;
  tutorialNext.textContent = state.tutorialIndex === tutorialPages.length - 1 ? "Finish" : "Next";
  renderTutorialDots();
}

function renderScreen() {
  screens.forEach((screen) => {
    screen.classList.toggle("is-visible", screen.dataset.screen === state.screen);
  });

  screenTabs.forEach((tab) => {
    tab.classList.toggle("is-active", tab.dataset.screen === state.screen);
  });
}

function renderDevice() {
  device.classList.toggle("device-tablet", state.device === "tablet");
  device.classList.toggle("device-phone", state.device === "phone");

  deviceTabs.forEach((tab) => {
    tab.classList.toggle("is-active", tab.dataset.device === state.device);
  });
}

function renderToggle() {
  previewToggle.classList.toggle("is-on", state.remindersOn);
}

screenTabs.forEach((tab) => {
  tab.addEventListener("click", () => {
    state.screen = tab.dataset.screen;
    renderScreen();
  });
});

deviceTabs.forEach((tab) => {
  tab.addEventListener("click", () => {
    state.device = tab.dataset.device;
    renderDevice();
  });
});

previewToggle.addEventListener("click", () => {
  state.remindersOn = !state.remindersOn;
  renderToggle();
});

tutorialPrev.addEventListener("click", () => {
  if (state.tutorialIndex > 0) {
    state.tutorialIndex -= 1;
    renderTutorialPage();
  }
});

tutorialNext.addEventListener("click", () => {
  if (state.tutorialIndex < tutorialPages.length - 1) {
    state.tutorialIndex += 1;
    renderTutorialPage();
    return;
  }

  state.screen = "home";
  renderScreen();
});

renderTutorialPage();
renderScreen();
renderDevice();
renderToggle();
