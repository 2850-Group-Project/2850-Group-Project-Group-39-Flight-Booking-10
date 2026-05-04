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

function openDeleteComplaint(id) {
    document.getElementById("deleteComplaintId").value = id;
    openModal(document.getElementById("deleteComplaintModal"));
}

function closeDeleteComplaint() {
    closeModal(document.getElementById("deleteComplaintModal"));
}

function openWriteResponse(id) {
    document.getElementById("writeResponseId").value = id;
    openModal(document.getElementById("writeResponseModal"));
}

function closeWriteResponse() {
    closeModal(document.getElementById("writeResponseModal"));
}

function openDeleteResponse(id) {
    document.getElementById("deleteResponseId").value = id;
    openModal(document.getElementById("deleteResponseModal"));
}

function closeDeleteResponse() {
    closeModal(document.getElementById("deleteResponseModal"));
}

document.getElementById("deleteComplaintModal").addEventListener("click", function(e) {
    if (e.target === this) closeDeleteComplaint();
});

document.getElementById("deleteResponseModal").addEventListener("click", function(e) {
    if (e.target === this) closeDeleteResponse();
});

document.getElementById("writeResponseModal").addEventListener("click", function(e) {
    if (e.target === this) closeWriteResponse();
});


document.addEventListener("keydown", function(e) {
    if (e.key === "Escape") {
        if (document.getElementById("deleteComplaintModal").classList.contains("open")) closeDeleteComplaint();
        if (document.getElementById("deleteResponseModal").classList.contains("open")) closeDeleteResponse();
        if (document.getElementById("writeResponseModal").classList.contains("open")) closeWriteResponse();
    }
});

function clearSearch() {
    const input = document.getElementById("inquirySearch");
    
    if (input.value.trim() !== "" && window.location.href !== "/staff/inquiries") {
        window.location.href = "/staff/inquiries";
        input.value = "";
    } else if (input.value.trim() !== "" && window.location.href === "/staff/inquiries") {
        input.value = "";
    }
}