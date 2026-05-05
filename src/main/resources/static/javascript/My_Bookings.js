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

function openSeatModal(origin, dest, flightNum, dep, arr, seat) {
  document.getElementById('seatModalRoute').textContent = origin + ' → ' + dest;
  document.getElementById('seatModalFlight').textContent = flightNum;
  document.getElementById('seatModalDep').textContent = dep;
  document.getElementById('seatModalArr').textContent = arr;
  document.getElementById('seatModalSeat').textContent = seat;

  var modal = document.getElementById('seatModal');
  modal.setAttribute('aria-hidden', 'false');
  modal.style.display = 'flex';
}

function closeSeatModal() {
  var modal = document.getElementById('seatModal');
  modal.setAttribute('aria-hidden', 'true');
  modal.style.display = 'none';
}
