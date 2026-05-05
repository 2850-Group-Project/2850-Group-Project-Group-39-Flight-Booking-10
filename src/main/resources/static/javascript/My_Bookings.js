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

async function openSeatModal(originName, destName, flightNum, dep, arr, status, seat, passengers) {
  document.getElementById('seatModalRoute').textContent = originName + ' -> ' + destName;
  document.getElementById('seatModalFlight').textContent = flightNum;
  document.getElementById('seatModalDep').textContent = dep;
  document.getElementById('seatModalArr').textContent = arr;
  document.getElementById('seatModalStatus').textContent = status;
  document.getElementById('seatModalSeat').textContent = seat;

  // Rendering passengers
  // Used Claude AI to generate the inline HTML/Pebble, lines 77-83
  var passengerHtml = '';
  if (passengers && passengers.length > 0) {
    passengers.forEach(function(p) {
      passengerHtml +=
        '<div style="padding:10px 12px; border-radius:8px; background:rgba(59,130,246,.08); border:1px solid rgba(59,130,246,.2);">' +
          '<div style="font-weight:700; margin-bottom:6px;">👤 ' + (p.firstName || '') + ' ' + (p.lastName || '') + '</div>' +
          '<div class="flight-details-modal-row"><span class="flight-details-modal-label">Date of Birth</span><span class="flight-details-modal-value">' + (p.dob || '—') + '</span></div>' +
          '<div class="flight-details-modal-row"><span class="flight-details-modal-label">Nationality</span><span class="flight-details-modal-value">' + (p.nationality || '—') + '</span></div>' +
          '<div class="flight-details-modal-row"><span class="flight-details-modal-label">Document</span><span class="flight-details-modal-value">' + (p.docType || '—') + ' ' + (p.docNumber || '') + '</span></div>' +
          '<div class="flight-details-modal-row"><span class="flight-details-modal-label">Checked In</span><span class="flight-details-modal-value">' + (p.checkedIn == 1 ? '✓ Yes' : '✗ No') + '</span></div>' +
        '</div>';
    });
  } else {
    passengerHtml = '<span class="flight-details-modal-label">No passenger data available.</span>';
  }
  document.getElementById('seatModalPassengers').innerHTML = passengerHtml;

  var modal = document.getElementById('seatModal');
  modal.setAttribute('aria-hidden', 'false');
  modal.style.display = 'flex';

  if (flightMap) {
    flightMap.remove();
    flightMap = null;
  }

  // Used Claude AI to find dark theme flight map, line 82
  flightMap = L.map('flightMap', { zoomControl: true, attributionControl: false });
  L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png').addTo(flightMap);
//   L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(flightMap);

  var originCoords = await geocodeCity(originName);
  var destCoords = await geocodeCity(destName);

  if (originCoords && destCoords) {
    // Draw line between 2 airports
    var latlngs = [originCoords, destCoords];
    L.polyline(latlngs, { color: '#ffffffe8', weight: 2, dashArray: '5 4' }).addTo(flightMap);

    // Used Claude AI to write the circle markers, lines 94-99
    // Airport markers
    L.circleMarker(originCoords, { radius: 6, color: '#0062ffc5', fillColor: '#3b82f6', fillOpacity: 1 })
      .bindTooltip(originName, { permanent: false })
      .addTo(flightMap);
    L.circleMarker(destCoords, { radius: 6, color: '#22c55ebf', fillColor: '#22c55e', fillOpacity: 1 })
      .bindTooltip(destName, { permanent: false })
      .addTo(flightMap);

    // fit map to show both points
    flightMap.fitBounds(L.latLngBounds([originCoords, destCoords]), { padding: [30, 30] });
  } else {
    // fallback to world view if geocoding fails
    flightMap.setView([20, 0], 2);
  }
}

function closeSeatModal() {
  var modal = document.getElementById('seatModal');
  modal.setAttribute('aria-hidden', 'true');
  modal.style.display = 'none';
  if (flightMap) {
    flightMap.remove();
    flightMap = null;
  }
}

// Used Claude AI to generate geocodeAirportFunction to search airports more accurately
async function geocodeAirport(query) {
  var url = 'https://nominatim.openstreetmap.org/search?format=json&limit=1&q=' 
    + encodeURIComponent(query + ' airport');
  var res = await fetch(url, { headers: { 'Accept-Language': 'en' } });
  var data = await res.json();
  if (data.length > 0) {
    return [parseFloat(data[0].lat), parseFloat(data[0].lon)];
  }
  return null;
}
