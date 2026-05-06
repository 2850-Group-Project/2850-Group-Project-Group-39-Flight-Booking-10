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

function openDelete(flightId) {
    document.getElementById("deleteFlightId").value = flightId;
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
        if (document.getElementById("deleteModal").classList.contains("open")) closeDelete();
    }
});

function clearSearch() {
    const input = document.getElementById("flightSearch");
    
    if (input.value.trim() !== "" && window.location.href !== "/staff/flights") {
        window.location.href = "/staff/flights";
        input.value = "";
    } else if (input.value.trim() !== "" && window.location.href === "/staff/flights") {
        input.value = "";
    }
}

function toggleFareInputs(fareClassId, checked) {
    var inputs = document.getElementById("fare-inputs-" + fareClassId);
    if (inputs) {
        inputs.style.display = checked ? "grid" : "none";
    }
}