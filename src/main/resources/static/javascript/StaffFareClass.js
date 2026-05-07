function openDeleteModal(id) {
    document.getElementById('deleteId').value = id;
    var modal = document.getElementById('deleteModal');
    modal.classList.add('open');
    modal.setAttribute('aria-hidden', 'false');
}

function closeDeleteModal() {
    var modal = document.getElementById('deleteModal');
    modal.classList.remove('open');
    modal.setAttribute('aria-hidden', 'true');
}

document.getElementById('deleteModal').addEventListener('click', function(e) {
    if (e.target === this) closeDeleteModal();
});