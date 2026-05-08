var lastFocusedElement = null;

function openModal(modal) {
    lastFocusedElement = document.activeElement;
    modal.classList.add("open");
    modal.setAttribute("aria-hidden", "false");
    modal.focus();
}

function closeModal(modal) {
    modal.classList.remove("open");
    modal.setAttribute("aria-hidden", "true");
    if (lastFocusedElement && typeof lastFocusedElement.focus === "function") {
        lastFocusedElement.focus();
    }
}

function openCancel(bookingId) {
    document.getElementById("cancelBookingId").value = bookingId;
    openModal(document.getElementById("cancelModal"));
}

function closeCancel() {
    closeModal(document.getElementById("cancelModal"));
}

document.getElementById("cancelModal").addEventListener("click", function(e) {
    if (e.target === this) closeCancel();
});

function openDelete(bookingId) {
    document.getElementById("deleteBookingId").value = bookingId;
    openModal(document.getElementById("deleteModal"));
}

function closeDelete() {
    closeModal(document.getElementById("deleteModal"));
}

document.getElementById("deleteModal").addEventListener("click", function(e) {
    if (e.target === this) closeDelete();
});

document.addEventListener("keydown", function(e) {
    if (e.key === "Escape") {
        if (document.getElementById("cancelModal").classList.contains("open")) closeCancel();
        if (document.getElementById("deleteModal").classList.contains("open")) closeDelete();
    }
});

var flightMap = null;

async function geocodeCity(name) {
  var url = 'https://nominatim.openstreetmap.org/search?format=json&limit=1&q=' + encodeURIComponent(name);
  var res = await fetch(url, { headers: { 'Accept-Language': 'en' } });
  var data = await res.json();
  if (data.length > 0) {
    return [parseFloat(data[0].lat), parseFloat(data[0].lon)];
  }
  return null;
}

// Used Claude AI to generate new open seat modal function for new ticket design
async function openSeatModal(originName, destName, originCity, destCity, originIata, destIata, flightNum, dep, arr, status, passengers) {
  const JSONpassengers = JSON.parse(passengers);

  if (flightMap) {
    flightMap.remove();
    flightMap = null;
  }

  var modal = document.getElementById('seatModal');
  modal.classList.add('open');
  modal.setAttribute('aria-hidden', 'false');

  document.getElementById('seatModalBody').innerHTML = `
    <div id="flightMap" style="width:100%; height:200px; min-height:200px;"></div>
    <div class="ticket-route">
      <div>
        <p class="ticket-iata">${originIata}</p>
        <p class="ticket-city">${originName}</p>
      </div>
      <div class="ticket-arrow">
        <div class="ticket-arrow-line"></div>
        <span>✈ ${flightNum}</span>
      </div>
      <div style="text-align:right;">
        <p class="ticket-iata">${destIata}</p>
        <p class="ticket-city">${destName}</p>
      </div>
    </div>
    <div class="ticket-info-grid">
      <div class="ticket-info-cell">
        <p class="ticket-info-label">Departure</p>
        <p class="ticket-info-value">${dep}</p>
      </div>
      <div class="ticket-info-cell">
        <p class="ticket-info-label">Arrival</p>
        <p class="ticket-info-value">${arr}</p>
      </div>
      <div class="ticket-info-cell">
        <p class="ticket-info-label">Status</p>
        <p class="ticket-info-value">${status}</p>
      </div>
    </div>
    <div class="ticket-tear">
      <div class="ticket-tear-line"></div>
    </div>
    <div class="ticket-passengers">
      <p class="ticket-passengers-label">Passengers</p>
      ${JSONpassengers && JSONpassengers.length > 0 ? JSONpassengers.map(function(p) {
        var initials = ((p.firstName || '')[0] || '') + ((p.lastName || '')[0] || '');
        var checkedIn = p.checkedIn == 1;
        return `
          <div class="ticket-passenger-row">
            <div style="display:flex;align-items:center;gap:10px;">
              <div class="ticket-passenger-avatar">${initials.toUpperCase()}</div>
              <div>
                <p style="margin:0;font-size:13px;font-weight:500;color:#f1f5f9;">${p.firstName || ''} ${p.lastName || ''}</p>
                <p style="margin:2px 0 0;font-size:11px;color:rgba(255,255,255,.45);">${p.dob || ''}</p>
              </div>
            </div>
            <div style="display:flex;align-items:center;gap:12px;">
              <div style="text-align:right;">
                <p class="ticket-seat-label">Seat</p>
                <p class="ticket-seat-number">${p.seatCode || '—'}</p>
              </div>
              <span style="font-size:11px;padding:3px 8px;border-radius:999px;background:${checkedIn ? 'rgba(34,197,94,.1)' : 'rgba(255,255,255,.05)'};color:${checkedIn ? '#86efac' : 'rgba(255,255,255,.4)'};border:1px solid ${checkedIn ? 'rgba(34,197,94,.3)' : 'rgba(255,255,255,.1)'};">
                ${checkedIn ? '✓ checked in' : '○ not checked in'}
              </span>
            </div>
          </div>`;
      }).join('') : '<span style="font-size:13px;color:rgba(255,255,255,.4);">No passenger data available.</span>'}
    </div>
  `;

  requestAnimationFrame(function() {
    requestAnimationFrame(async function() {
      var mapEl = document.getElementById('flightMap');
      if (!mapEl) return;

      flightMap = L.map(mapEl, { zoomControl: true, attributionControl: false });
      L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png').addTo(flightMap);

      var originCoords = await geocodeCity(originCity || originName);
      var destCoords = await geocodeCity(destCity || destName);

      flightMap.invalidateSize();

      if (originCoords && destCoords) {
        const latlngs = [];
        for (let i = 0; i <= 50; i++) {
          const t = i / 50;
          const lat = originCoords[0] + (destCoords[0] - originCoords[0]) * t;
          const lng = originCoords[1] + (destCoords[1] - originCoords[1]) * t;
          const arc = Math.sin(Math.PI * t) * (L.latLng(originCoords).distanceTo(L.latLng(destCoords)) / 800000);
          latlngs.push([lat + arc, lng]);
        }
        L.polyline(latlngs, { color: 'rgba(255,255,255,0.25)', weight: 1.5, dashArray: '4 6' }).addTo(flightMap);

        const makeIcon = (color) => L.divIcon({
          className: '',
          html: `<div style="position:relative;width:28px;height:28px;display:flex;align-items:center;justify-content:center;">
            <div style="position:absolute;width:28px;height:28px;border-radius:50%;background:${color};opacity:0.15;"></div>
            <div style="width:10px;height:10px;border-radius:50%;background:${color};box-shadow:0 0 0 2px rgba(0,0,0,0.5),0 0 8px ${color};"></div>
          </div>`,
          iconSize: [28, 28],
          iconAnchor: [14, 14],
        });

        L.marker(originCoords, { icon: makeIcon('#e0ecffa2') }).bindTooltip(`<b>${originIata}</b> ${originName}`, { className: 'map-tip' }).addTo(flightMap);
        L.marker(destCoords, { icon: makeIcon('#22c55e') }).bindTooltip(`<b>${destIata}</b> ${destName}`, { className: 'map-tip' }).addTo(flightMap);

        flightMap.fitBounds(L.latLngBounds([originCoords, destCoords]), { padding: [30, 30] });
      } else {
        flightMap.setView([20, 0], 2);
      }
    });
  });
}

function closeSeatModal() {
  var modal = document.getElementById('seatModal');
  modal.classList.remove('open');
  modal.setAttribute('aria-hidden', 'true');
  modal.style.display = '';
  if (flightMap) {
    flightMap.remove();
    flightMap = null;
  }
}
