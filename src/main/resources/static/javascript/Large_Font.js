(function () {
  function applyLargeFont(enabled) {
    var root = document.documentElement;
    var elements = document.body ? document.body.querySelectorAll("*") : [];
    var toggles = document.querySelectorAll("[data-large-font-toggle]");

    if (enabled) {
      root.classList.add("large-font");
      elements.forEach(function (el) {
        if (!el.dataset.largeFontOriginalInline) {
          el.dataset.largeFontOriginalInline = el.style.fontSize || "";
        }
        var computed = window.getComputedStyle(el).fontSize;
        var size = parseFloat(computed);
        if (!isNaN(size) && size > 0) {
          el.style.fontSize = (size * 1.15).toFixed(2) + "px";
        }
      });
    } else {
      root.classList.remove("large-font");
      elements.forEach(function (el) {
        if (el.dataset.largeFontOriginalInline !== undefined) {
          el.style.fontSize = el.dataset.largeFontOriginalInline;
          delete el.dataset.largeFontOriginalInline;
        }
      });
    }

    toggles.forEach(function (toggle) {
      toggle.textContent = enabled ? "Normal Font" : "Large Font";
      toggle.setAttribute("aria-pressed", enabled ? "true" : "false");
    });
  }

  function initLargeFont() {
    var toggles = document.querySelectorAll("[data-large-font-toggle]");
    if (!toggles.length) {
      return;
    }

    var enabled = window.localStorage.getItem("skyflow_large_font") === "1";
    applyLargeFont(enabled);

    toggles.forEach(function (toggle) {
      toggle.addEventListener("click", function (event) {
        event.preventDefault();
        enabled = !enabled;
        window.localStorage.setItem("skyflow_large_font", enabled ? "1" : "0");
        applyLargeFont(enabled);
      });
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initLargeFont);
  } else {
    initLargeFont();
  }
})();
