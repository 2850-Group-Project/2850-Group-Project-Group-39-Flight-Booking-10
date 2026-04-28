function openCancel(bookingId) {
    document.getElementById("cancelBookingId").value = bookingId;
    document.getElementById("cancelModal").classList.add("open");
}

function closeCancel() {
    document.getElementById("cancelModal").classList.remove("open");
}

document.getElementById("cancelModal").addEventListener("click", function(e) {
    if (e.target === this) closeCancel();
});

function openDelete(bookingId) {
    document.getElementById("deleteBookingId").value = bookingId;
    document.getElementById("deleteModal").classList.add("open");
}

function closeDelete() {
    document.getElementById("deleteModal").classList.remove("open");
}

document.getElementById("cancelDelete").addEventListener("click", function(e) {
    if (e.target === this) closeDelete();
});