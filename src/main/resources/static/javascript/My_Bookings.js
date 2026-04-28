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