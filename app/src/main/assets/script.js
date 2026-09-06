const API =
  "https://reelshort.vercel.app";


/* =========================
   STATE
========================= */

let currentBook = null;

let episodes = [];

let currentEpisodeIndex = 0;

let hls = null;

let currentStreamCandidates = [];

let streamCandidateIndex = 0;

let touchStartY = 0;

let touchStartX = 0;

let touchStartTime = 0;

let isSeeking = false;

let wheelLocked = false;

let selectedModeBook = null;


/* =========================
   ELEMENTS
========================= */

const homePage =
  document.getElementById("homePage");

const searchInput =
  document.getElementById("searchInput");

const searchButton =
  document.getElementById("searchButton");

const searchStatus =
  document.getElementById("searchStatus");

const searchResults =
  document.getElementById("searchResults");

const forYouStatus =
  document.getElementById("forYouStatus");

const forYouResults =
  document.getElementById("forYouResults");

const refreshForYouButton =
  document.getElementById("refreshForYouButton");

const searchSection =
  document.getElementById("searchSection");


const playerPage =
  document.getElementById("playerPage");

const backButton =
  document.getElementById("backButton");

const playerTitle =
  document.getElementById("playerTitle");

const playerEpisodeText =
  document.getElementById("playerEpisodeText");

const videoStage =
  document.getElementById("videoStage");

const video =
  document.getElementById("video");

const loading =
  document.getElementById("loading");

const videoError =
  document.getElementById("videoError");

const centerPlay =
  document.getElementById("centerPlay");

const fullscreenButton =
  document.getElementById("fullscreenButton");

const progress =
  document.getElementById("progress");

const currentTime =
  document.getElementById("currentTime");

const durationTime =
  document.getElementById("durationTime");

const previousButton =
  document.getElementById("previousButton");

const nextButton =
  document.getElementById("nextButton");

const playPauseButton =
  document.getElementById("playPauseButton");

const episodeBar =
  document.getElementById("episodeBar");

const episodePosition =
  document.getElementById("episodePosition");

const swipeIndicator =
  document.getElementById("swipeIndicator");


const episodeOverlay =
  document.getElementById("episodeOverlay");

const episodeSheet =
  document.getElementById("episodeSheet");

const closeSheetButton =
  document.getElementById("closeSheetButton");

const sheetEpisodeCount =
  document.getElementById("sheetEpisodeCount");

const episodeList =
  document.getElementById("episodeList");

const modeOverlay =
  document.getElementById("modeOverlay");

const modeSheet =
  document.getElementById("modeSheet");

const modeBookTitle =
  document.getElementById("modeBookTitle");

const modeBookCover =
  document.getElementById("modeBookCover");

const modeBookMeta =
  document.getElementById("modeBookMeta");

const modeBookThemes =
  document.getElementById("modeBookThemes");

const modeBookDesc =
  document.getElementById("modeBookDesc");

const closeModeButton =
  document.getElementById("closeModeButton");

const shortsModeButton =
  document.getElementById("shortsModeButton");

const floatingModeButton =
  document.getElementById("floatingModeButton");


/* =========================
   FOR YOU
========================= */

async function loadForYou() {

  forYouStatus.textContent =
    "Memuat rekomendasi...";

  forYouResults.innerHTML = "";

  try {

    let data;

    if (
      window.AndroidApp &&
      typeof window.AndroidApp.getForYou ===
        "function"
    ) {

      data = JSON.parse(
        window.AndroidApp.getForYou()
      );

    } else {

      const response =
        await fetch(
          `${API}/api/foryou?lang=in`
        );

      if (!response.ok) {
        throw new Error(
          `HTTP ${response.status}`
        );
      }

      data = await response.json();
    }

    if (
      !data.ok ||
      !Array.isArray(data.items)
    ) {
      throw new Error(
        "Format For You tidak valid"
      );
    }

    forYouStatus.textContent =
      `${data.items.length} rekomendasi`;

    renderMovies(
      data.items,
      forYouResults
    );

  } catch (error) {

    console.error(error);

    forYouStatus.textContent =
      `Gagal memuat rekomendasi: ${error.message}`;
  }
}


/* =========================
   SEARCH
========================= */

async function searchDrama() {

  const keyword =
    searchInput.value.trim();

  if (!keyword) {
    return;
  }

  searchStatus.textContent =
    "Mencari...";

  searchResults.innerHTML = "";

  try {

    let data;

    if (
      window.AndroidApp &&
      typeof window.AndroidApp.searchBooks ===
        "function"
    ) {

      const raw =
        window.AndroidApp.searchBooks(
          keyword
        );

      data =
        JSON.parse(raw);

    } else {

      const url =
        `${API}/search` +
        `?lang=in` +
        `&keyword=${encodeURIComponent(keyword)}`;

      const response =
        await fetch(url);

      if (!response.ok) {
        throw new Error(
          `HTTP ${response.status}`
        );
      }

      data =
        await response.json();
    }

    if (
      !data.ok ||
      !Array.isArray(data.items)
    ) {
      throw new Error(
        "Format API search tidak valid"
      );
    }

    searchStatus.textContent =
      `${data.items.length} hasil ditemukan`;

    renderMovies(
      data.items,
      searchResults
    );

    searchSection.scrollIntoView({
      behavior: "smooth",
      block: "start"
    });

  } catch (error) {

    console.error(error);

    searchStatus.textContent =
      `Gagal mencari: ${error.message}`;

  }

}


/* =========================
   MOVIE LIST
========================= */

function renderMovies(
  items,
  container = searchResults
) {

  container.innerHTML = "";

  items.forEach(book => {

    const card =
      document.createElement("article");

    card.className =
      "movie-card";

    card.tabIndex = 0;

    const themes =
      Array.isArray(book.theme)
        ? book.theme.join(" • ")
        : "";

    const collect =
      Number(book.collect_count || 0);

    card.innerHTML = `
      <div class="movie-cover-wrap">
        <img
          class="movie-cover"
          src="${escapeAttr(book.pic || "")}"
          alt="${escapeAttr(book.title || "")}"
          loading="lazy"
        >

        <span class="movie-count">
          ${book.chapter_count || 0} EP
        </span>
      </div>

      <div class="movie-info">
        <div class="movie-title">
          ${escapeHtml(book.title || "Tanpa judul")}
        </div>

        <div class="movie-meta">
          ${themes ? escapeHtml(themes) : "Drama pendek"}
        </div>

        ${collect ? `
          <div class="movie-popularity">
            ♥ ${formatCompactNumber(collect)}
          </div>
        ` : ""}
      </div>
    `;

    const open = () =>
      showModePicker(book);

    card.addEventListener(
      "click",
      open
    );

    card.addEventListener(
      "keydown",
      event => {
        if (
          event.key === "Enter" ||
          event.key === " "
        ) {
          event.preventDefault();
          open();
        }
      }
    );

    container.appendChild(card);
  });
}


function formatCompactNumber(value) {
  const number = Number(value || 0);

  if (number >= 1000000) {
    return `${(number / 1000000).toFixed(1).replace(".0", "")} jt`;
  }

  if (number >= 1000) {
    return `${(number / 1000).toFixed(1).replace(".0", "")} rb`;
  }

  return String(number);
}


/* =========================
   MODE PICKER
========================= */

async function showModePicker(book) {

  selectedModeBook = {
    ...book
  };

  renderModeDetails(
    selectedModeBook,
    true
  );

  modeOverlay.classList.remove(
    "hidden"
  );

  modeSheet.setAttribute(
    "aria-hidden",
    "false"
  );

  requestAnimationFrame(
    () => {
      modeSheet.classList.add(
        "open"
      );
    }
  );

  const bookId =
    String(book.book_id || "");

  if (!bookId) {
    return;
  }

  try {

    let details;

    if (
      window.AndroidApp &&
      typeof window.AndroidApp.getDetails ===
        "function"
    ) {

      details = JSON.parse(
        window.AndroidApp.getDetails(
          bookId
        )
      );

    } else {

      const response =
        await fetch(
          `${API}/api/details?lang=in&bookId=${encodeURIComponent(bookId)}`
        );

      if (!response.ok) {
        throw new Error(
          `HTTP ${response.status}`
        );
      }

      details = await response.json();
    }

    if (details.ok) {

      selectedModeBook = {
        ...selectedModeBook,
        ...details,
        book_id:
          details.id ||
          selectedModeBook.book_id,
        chapter_count:
          details.chapters ||
          selectedModeBook.chapter_count
      };

      renderModeDetails(
        selectedModeBook,
        false
      );
    }

  } catch (error) {
    console.error(
      "DETAILS:",
      error
    );

    modeBookDesc.textContent =
      "Detail tambahan gagal dimuat, tapi mode nonton tetap bisa dipakai.";
  }
}


function renderModeDetails(
  book,
  loadingDetails = false
) {

  modeBookTitle.textContent =
    book.title || "Drama";

  modeBookCover.src =
    book.pic || "";

  const chapters =
    book.chapters ||
    book.chapter_count ||
    0;

  const views =
    Number(book.views || 0);

  const collect =
    Number(book.collect_count || 0);

  const meta = [];

  if (chapters) {
    meta.push(`${chapters} episode`);
  }

  if (views) {
    meta.push(`${formatCompactNumber(views)} tayangan`);
  }

  if (collect) {
    meta.push(`${formatCompactNumber(collect)} favorit`);
  }

  modeBookMeta.textContent =
    meta.join(" • ") ||
    "Drama pendek";

  const themes =
    Array.isArray(book.theme)
      ? book.theme
      : [];

  modeBookThemes.innerHTML =
    themes
      .slice(0, 5)
      .map(theme => `
        <span class="detail-chip">
          ${escapeHtml(theme)}
        </span>
      `)
      .join("");

  if (book.desc) {
    modeBookDesc.textContent =
      book.desc;
  } else if (loadingDetails) {
    modeBookDesc.textContent =
      "Memuat deskripsi...";
  } else {
    modeBookDesc.textContent =
      "Pilih mode nonton di bawah.";
  }
}


function closeModePicker() {

  modeSheet.classList.remove(
    "open"
  );

  modeSheet.setAttribute(
    "aria-hidden",
    "true"
  );

  setTimeout(
    () => {
      modeOverlay.classList.add(
        "hidden"
      );
    },
    240
  );
}


function startShortsMode() {

  const book =
    selectedModeBook;

  if (!book) {
    return;
  }

  closeModePicker();

  if (
    window.AndroidApp &&
    typeof window.AndroidApp.startShorts ===
      "function"
  ) {

    window.AndroidApp.startShorts(
      String(book.book_id || ""),
      String(book.title || "")
    );

    return;
  }

  // Browser fallback: pakai Shorts HTML lama.
  openBook(book);
}


function startFloatingMode() {

  const book =
    selectedModeBook;

  if (!book) {
    return;
  }

  closeModePicker();

  if (
    window.AndroidApp &&
    typeof window.AndroidApp.startFloating ===
      "function"
  ) {

    window.AndroidApp.startFloating(
      String(book.book_id || ""),
      String(book.title || "")
    );

    return;
  }

  searchStatus.textContent =
    "Floating window tersedia di APK Android.";
}


/* =========================
   OPEN BOOK
========================= */

async function openBook(book) {

  currentBook = book;

  searchStatus.textContent =
    "Memuat episode...";

  try {

    const url =
      `${API}/api/stream/all-episode` +
      `?lang=in` +
      `&bookId=${encodeURIComponent(book.book_id)}`;

    const response =
      await fetch(url);

    if (!response.ok) {
      throw new Error(
        `HTTP ${response.status}`
      );
    }

    const data =
      await response.json();

    if (
      !data.ok ||
      !Array.isArray(data.episodes)
    ) {
      throw new Error(
        "Daftar episode tidak tersedia"
      );
    }


    episodes =
      data.episodes;

    currentEpisodeIndex = 0;


    currentBook = {
      ...book,

      title:
        data.title ||
        book.title,

      pic:
        data.pic ||
        book.pic,

      desc:
        data.desc || "",

      totalChapters:
        data.totalChapters ||
        data.chapters ||
        episodes.length
    };


    renderEpisodeSheet();


    homePage.classList.add(
      "hidden"
    );

    playerPage.classList.remove(
      "hidden"
    );


    document.body.style.overflow =
      "hidden";


    await playEpisodeByIndex(
      0
    );


    showSwipeHint();

  } catch (error) {

    console.error(error);

    searchStatus.textContent =
      `Gagal mengambil episode: ${error.message}`;

  }

}


/* =========================
   PLAY EPISODE
========================= */

async function playEpisodeByIndex(index) {

  if (
    index < 0 ||
    index >= episodes.length
  ) {
    return;
  }


  currentEpisodeIndex =
    index;


  const episode =
    episodes[index];


  updatePlayerUI(
    episode
  );


  closeEpisodeSheet();


  showLoading(true);

  hideVideoError();


  /*
    API ngasih URL proxy pakai HTTP.
    Kita coba ubah ke HTTPS dulu.

    Kalau proxy gagal, fallback ke
    sourceVideoUrl langsung.
  */

  currentStreamCandidates =
    buildStreamCandidates(
      episode
    );


  streamCandidateIndex = 0;


  if (
    !currentStreamCandidates.length
  ) {

    showLoading(false);

    showVideoError(
      "URL video tidak ditemukan"
    );

    return;

  }


  playCurrentCandidate();

}


/* =========================
   BUILD STREAM LIST
========================= */

function buildStreamCandidates(
  episode
) {

  const urls = [];


  function pushUrl(url) {

    if (!url) {
      return;
    }

    const secure =
      String(url).replace(
        /^http:\/\//i,
        "https://"
      );

    if (
      !urls.includes(secure)
    ) {
      urls.push(secure);
    }

  }


  /*
    Proxy dulu.
    Kemungkinan lebih ramah browser.
  */

  pushUrl(
    episode.videoUrl
  );


  if (
    Array.isArray(
      episode.streams
    )
  ) {

    episode.streams.forEach(
      stream => {

        pushUrl(
          stream.url
        );

      }
    );

  }


  /*
    Direct source sebagai fallback.
  */

  pushUrl(
    episode.sourceVideoUrl
  );


  if (
    Array.isArray(
      episode.streams
    )
  ) {

    episode.streams.forEach(
      stream => {

        pushUrl(
          stream.sourceUrl
        );

      }
    );

  }


  return urls;

}


/* =========================
   PLAYER STREAM
========================= */

function playCurrentCandidate() {

  destroyHLS();


  video.pause();

  video.removeAttribute(
    "src"
  );

  video.load();


  const url =
    currentStreamCandidates[
      streamCandidateIndex
    ];


  if (!url) {

    showLoading(false);

    showVideoError(
      "Semua sumber video gagal diputar"
    );

    return;

  }


  console.log(
    "PLAY URL:",
    url
  );


  if (
    window.Hls &&
    Hls.isSupported()
  ) {

    hls =
      new Hls({

        enableWorker: true,

        lowLatencyMode: false,

        backBufferLength: 20

      });


    hls.loadSource(
      url
    );


    hls.attachMedia(
      video
    );


    hls.on(
      Hls.Events.MANIFEST_PARSED,
      () => {

        showLoading(false);

        tryPlayVideo();

      }
    );


    hls.on(
      Hls.Events.ERROR,
      (
        event,
        data
      ) => {

        console.error(
          "HLS ERROR:",
          data
        );


        if (
          data.fatal
        ) {

          tryNextStream();

        }

      }
    );


    return;

  }


  if (
    video.canPlayType(
      "application/vnd.apple.mpegurl"
    )
  ) {

    video.src = url;

    video.addEventListener(
      "loadedmetadata",
      () => {

        showLoading(false);

        tryPlayVideo();

      },
      {
        once: true
      }
    );

    return;

  }


  showLoading(false);

  showVideoError(
    "Browser ini tidak mendukung HLS"
  );

}


/* =========================
   FALLBACK STREAM
========================= */

function tryNextStream() {

  streamCandidateIndex++;


  if (
    streamCandidateIndex >=
    currentStreamCandidates.length
  ) {

    showLoading(false);

    showVideoError(
      "Video gagal diputar pada browser ini"
    );

    return;

  }


  console.log(
    "Fallback stream:",
    currentStreamCandidates[
      streamCandidateIndex
    ]
  );


  playCurrentCandidate();

}


/* =========================
   PLAY
========================= */

function tryPlayVideo() {

  video
    .play()
    .then(() => {

      updatePlayButton();

    })
    .catch(() => {

      centerPlay.classList.remove(
        "hidden"
      );

      updatePlayButton();

    });

}


function togglePlay() {

  if (
    video.paused
  ) {

    video.play()
      .catch(() => {});

  } else {

    video.pause();

  }

}


/* =========================
   UI
========================= */

function updatePlayerUI(
  episode
) {

  playerTitle.textContent =
    currentBook?.title ||
    "ReelShort";


  playerEpisodeText.textContent =
    `Episode ${episode.index}`;


  episodePosition.textContent =
    `EP.${episode.index}/EP.${episodes.length}`;


  updateActiveEpisodeButton();

}


/* =========================
   NEXT / PREVIOUS
========================= */

function nextEpisode() {

  const next =
    currentEpisodeIndex + 1;


  if (
    next >= episodes.length
  ) {
    return;
  }


  playEpisodeByIndex(
    next
  );

}


function previousEpisode() {

  const previous =
    currentEpisodeIndex - 1;


  if (
    previous < 0
  ) {
    return;
  }


  playEpisodeByIndex(
    previous
  );

}


/* =========================
   EPISODE SHEET
========================= */

function renderEpisodeSheet() {

  episodeList.innerHTML =
    "";


  sheetEpisodeCount.textContent =
    `${episodes.length} episode`;


  episodes.forEach(
    (
      episode,
      index
    ) => {

      const button =
        document.createElement(
          "button"
        );


      button.className =
        "episode-item";


      button.dataset.index =
        index;


      button.textContent =
        `EP ${episode.index}`;


      button.addEventListener(
        "click",
        () => {

          playEpisodeByIndex(
            index
          );

        }
      );


      episodeList.appendChild(
        button
      );

    }
  );


  updateActiveEpisodeButton();

}


function updateActiveEpisodeButton() {

  document
    .querySelectorAll(
      ".episode-item"
    )
    .forEach(button => {

      const index =
        Number(
          button.dataset.index
        );


      button.classList.toggle(
        "active",
        index ===
          currentEpisodeIndex
      );

    });

}


function openEpisodeSheet() {

  episodeOverlay.classList.remove(
    "hidden"
  );


  requestAnimationFrame(
    () => {

      episodeSheet.classList.add(
        "open"
      );

    }
  );


  setTimeout(
    () => {

      const active =
        episodeList.querySelector(
          ".episode-item.active"
        );


      active?.scrollIntoView({
        block: "center"
      });

    },
    250
  );

}


function closeEpisodeSheet() {

  episodeSheet.classList.remove(
    "open"
  );


  setTimeout(
    () => {

      episodeOverlay.classList.add(
        "hidden"
      );

    },
    250
  );

}


/* =========================
   PROGRESS
========================= */

function updateProgress() {

  if (
    isSeeking ||
    !Number.isFinite(
      video.duration
    )
  ) {
    return;
  }


  const ratio =
    video.duration
      ? video.currentTime /
        video.duration
      : 0;


  progress.value =
    Math.floor(
      ratio * 1000
    );


  currentTime.textContent =
    formatTime(
      video.currentTime
    );


  durationTime.textContent =
    formatTime(
      video.duration
    );

}


function seekVideo() {

  if (
    !Number.isFinite(
      video.duration
    )
  ) {
    return;
  }


  const ratio =
    Number(progress.value) /
    1000;


  video.currentTime =
    ratio *
    video.duration;

}


/* =========================
   FORMAT TIME
========================= */

function formatTime(seconds) {

  if (
    !Number.isFinite(seconds)
  ) {
    return "00:00";
  }


  const total =
    Math.floor(seconds);


  const minutes =
    Math.floor(
      total / 60
    );


  const secs =
    total % 60;


  return (
    String(minutes)
      .padStart(2, "0")
    +
    ":"
    +
    String(secs)
      .padStart(2, "0")
  );

}


/* =========================
   FULLSCREEN
========================= */

async function toggleFullscreen() {

  try {

    if (
      document.fullscreenElement
    ) {

      await document.exitFullscreen();

      return;

    }


    if (
      playerPage.requestFullscreen
    ) {

      await playerPage.requestFullscreen();

    } else if (
      video.webkitEnterFullscreen
    ) {

      video.webkitEnterFullscreen();

    }

  } catch (error) {

    console.error(
      "FULLSCREEN:",
      error
    );

  }

}


/* =========================
   SWIPE SHORTS
========================= */

function handleTouchStart(
  event
) {

  if (
    episodeSheet.classList.contains(
      "open"
    )
  ) {
    return;
  }


  const touch =
    event.changedTouches[0];


  touchStartY =
    touch.clientY;


  touchStartX =
    touch.clientX;


  touchStartTime =
    Date.now();

}


function handleTouchEnd(
  event
) {

  if (
    episodeSheet.classList.contains(
      "open"
    )
  ) {
    return;
  }


  const touch =
    event.changedTouches[0];


  const deltaY =
    touch.clientY -
    touchStartY;


  const deltaX =
    touch.clientX -
    touchStartX;


  const elapsed =
    Date.now() -
    touchStartTime;


  /*
    Jangan dianggap swipe kalau
    lebih dominan horizontal.
  */

  if (
    Math.abs(deltaX) >
    Math.abs(deltaY)
  ) {
    return;
  }


  if (
    elapsed > 800
  ) {
    return;
  }


  const threshold = 70;


  /*
    Swipe atas
  */

  if (
    deltaY <
    -threshold
  ) {

    nextEpisode();

    return;

  }


  /*
    Swipe bawah
  */

  if (
    deltaY >
    threshold
  ) {

    previousEpisode();

  }

}


/* =========================
   DESKTOP WHEEL
========================= */

function handleWheel(
  event
) {

  if (
    wheelLocked
  ) {
    return;
  }


  if (
    Math.abs(event.deltaY) <
    40
  ) {
    return;
  }


  wheelLocked = true;


  if (
    event.deltaY > 0
  ) {

    nextEpisode();

  } else {

    previousEpisode();

  }


  setTimeout(
    () => {

      wheelLocked = false;

    },
    700
  );

}


/* =========================
   PLAYER STATUS
========================= */

function showLoading(show) {

  loading.classList.toggle(
    "hidden",
    !show
  );

}


function showVideoError(
  message
) {

  videoError.textContent =
    message;


  videoError.classList.remove(
    "hidden"
  );

}


function hideVideoError() {

  videoError.classList.add(
    "hidden"
  );

}


function updatePlayButton() {

  const paused =
    video.paused;


  playPauseButton.textContent =
    paused
      ? "▶"
      : "❚❚";


  centerPlay.classList.toggle(
    "hidden",
    !paused
  );

}


/* =========================
   SWIPE HINT
========================= */

function showSwipeHint() {

  swipeIndicator.classList.add(
    "show"
  );


  setTimeout(
    () => {

      swipeIndicator.classList.remove(
        "show"
      );

    },
    2200
  );

}


/* =========================
   BACK HOME
========================= */

function backHome() {

  closeEpisodeSheet();


  destroyHLS();


  video.pause();

  video.removeAttribute(
    "src"
  );

  video.load();


  playerPage.classList.add(
    "hidden"
  );


  homePage.classList.remove(
    "hidden"
  );


  document.body.style.overflow =
    "";


  currentStreamCandidates = [];

}


/* =========================
   HLS DESTROY
========================= */

function destroyHLS() {

  if (hls) {

    hls.destroy();

    hls = null;

  }

}


/* =========================
   ESCAPE
========================= */

function escapeHtml(text) {

  const div =
    document.createElement(
      "div"
    );


  div.textContent =
    String(text);


  return div.innerHTML;

}


function escapeAttr(text) {

  return String(text)

    .replaceAll(
      "&",
      "&amp;"
    )

    .replaceAll(
      '"',
      "&quot;"
    )

    .replaceAll(
      "<",
      "&lt;"
    )

    .replaceAll(
      ">",
      "&gt;"
    );

}


/* =========================
   EVENTS
========================= */

refreshForYouButton.addEventListener(
  "click",
  loadForYou
);


closeModeButton.addEventListener(
  "click",
  closeModePicker
);


modeOverlay.addEventListener(
  "click",
  closeModePicker
);


shortsModeButton.addEventListener(
  "click",
  startShortsMode
);


floatingModeButton.addEventListener(
  "click",
  startFloatingMode
);


searchButton.addEventListener(
  "click",
  searchDrama
);


searchInput.addEventListener(
  "keydown",
  event => {

    if (
      event.key === "Enter"
    ) {

      searchDrama();

    }

  }
);


backButton.addEventListener(
  "click",
  backHome
);


episodeBar.addEventListener(
  "click",
  openEpisodeSheet
);


closeSheetButton.addEventListener(
  "click",
  closeEpisodeSheet
);


episodeOverlay.addEventListener(
  "click",
  closeEpisodeSheet
);


fullscreenButton.addEventListener(
  "click",
  toggleFullscreen
);


playPauseButton.addEventListener(
  "click",
  togglePlay
);


centerPlay.addEventListener(
  "click",
  togglePlay
);


previousButton.addEventListener(
  "click",
  previousEpisode
);


nextButton.addEventListener(
  "click",
  nextEpisode
);


videoStage.addEventListener(
  "click",
  event => {

    if (
      event.target === video ||
      event.target === videoStage
    ) {

      togglePlay();

    }

  }
);


video.addEventListener(
  "play",
  updatePlayButton
);


video.addEventListener(
  "pause",
  updatePlayButton
);


video.addEventListener(
  "timeupdate",
  updateProgress
);


video.addEventListener(
  "durationchange",
  updateProgress
);


video.addEventListener(
  "waiting",
  () => showLoading(true)
);


video.addEventListener(
  "playing",
  () => showLoading(false)
);


video.addEventListener(
  "ended",
  () => {

    nextEpisode();

  }
);


/* PROGRESS */

progress.addEventListener(
  "pointerdown",
  () => {

    isSeeking = true;

  }
);


progress.addEventListener(
  "input",
  () => {

    if (
      Number.isFinite(
        video.duration
      )
    ) {

      const preview =
        (
          Number(
            progress.value
          ) /
          1000
        ) *
        video.duration;


      currentTime.textContent =
        formatTime(
          preview
        );

    }

  }
);


progress.addEventListener(
  "change",
  () => {

    seekVideo();

    isSeeking = false;

  }
);


/* SWIPE */

playerPage.addEventListener(
  "touchstart",
  handleTouchStart,
  {
    passive: true
  }
);


playerPage.addEventListener(
  "touchend",
  handleTouchEnd,
  {
    passive: true
  }
);


/* MOUSE WHEEL */

playerPage.addEventListener(
  "wheel",
  handleWheel,
  {
    passive: true
  }
);


/* KEYBOARD */

document.addEventListener(
  "keydown",
  event => {

    if (
      playerPage.classList.contains(
        "hidden"
      )
    ) {
      return;
    }


    if (
      event.key ===
      "ArrowDown"
    ) {

      nextEpisode();

    }


    if (
      event.key ===
      "ArrowUp"
    ) {

      previousEpisode();

    }


    if (
      event.key === " "
    ) {

      event.preventDefault();

      togglePlay();

    }


    if (
      event.key ===
      "Escape"
    ) {

      closeEpisodeSheet();

    }

  }
);

/* =========================
   STARTUP
========================= */

loadForYou();

