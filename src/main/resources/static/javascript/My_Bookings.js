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

async function openSeatModal(originName, destName, flightNum, dep, arr, status, passengers) {
  document.getElementById('seatModalRoute').textContent = originName + ' → ' + destName;
  document.getElementById('seatModalFlight').textContent = flightNum;
  document.getElementById('seatModalDep').textContent = dep;
  document.getElementById('seatModalArr').textContent = arr;
  document.getElementById('seatModalStatus').textContent = status;

  const JSONpassengers = JSON.parse(passengers);

  // Rendering passengers
  // Used Claude AI to generate the inline HTML/Pebble, lines 81-108
  var passengerHtml = '';
  if (JSONpassengers && JSONpassengers.length > 0) {
    JSONpassengers.forEach(function(p) {
        passengerHtml +=
            '<div class="passenger-detail-card">' +
            '<div class="passenger-detail-header">' +
                '<span class="passenger-detail-icon">👤</span>' +
                '<span class="passenger-detail-name">' + (p.firstName || '') + ' ' + (p.lastName || '') + '</span>' +
                '<div class="seat-code mini-pill">' +
                  '<span class="passenger-detail-field-label">Seat</span>' +
                  '<span class="passenger-detail-field-value">' + (p.seatCode || '-') + '</span>' +
                '</div>' +
                '<span class="passenger-detail-checkin ' + (p.checkedIn == 1 ? 'checked-in' : '') + '">' +
                (p.checkedIn == 1 ? '✓ Checked In' : '○ Not Checked In') +
                '</span>' +
            '</div>' +
            '<div class="passenger-detail-grid">' +
                '<div class="passenger-detail-field">' +
                '<span class="passenger-detail-field-label">Date of Birth</span>' +
                '<span class="passenger-detail-field-value">' + (p.dob || '-') + '</span>' +
                '</div>' +
                '<div class="passenger-detail-field">' +
                '<span class="passenger-detail-field-label">Nationality</span>' +
                '<span class="passenger-detail-field-value">' + (p.nationality || '-') + '</span>' +
                '</div>' +
                '<div class="passenger-detail-field">' +
                '<span class="passenger-detail-field-label">Document Type</span>' +
                '<span class="passenger-detail-field-value">' + (p.docType || '-') + '</span>' +
                '</div>' +
                '<div class="passenger-detail-field">' +
                '<span class="passenger-detail-field-label">Document Number</span>' +
                '<span class="passenger-detail-field-value">' + (p.docNumber || '-') + '</span>' +
                '</div>' +
            '</div>' +
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

  // Used Claude AI to find dark theme flight map, line 125
  flightMap = L.map('flightMap', { zoomControl: true, attributionControl: false });
  L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png').addTo(flightMap);

  var originCoords = await geocodeCity(originName);
  var destCoords = await geocodeCity(destName);

  if (originCoords && destCoords) {
    // Draw line between 2 airports
    var latlngs = [originCoords, destCoords];
    L.polyline(latlngs, { color: '#ffffffe8', weight: 2, dashArray: '5 4' }).addTo(flightMap);

    // Used Claude AI to write the circle markers, lines 137-142
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
