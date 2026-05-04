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

function openDelete(requestId) {
    document.getElementById("deleteRequestId").value = requestId;
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
