const selected = { outbound: false, return: false };

function scrollCarousel(trackId, direction) {
    const track = document.getElementById(trackId + '-track');
    track.scrollBy({ left: direction * 500, behavior: 'smooth' });
}

async function selectFlight(event, form) {
    event.preventDefault();

    const leg = form.querySelector('[name="leg"]').value;
    const tripType = form.querySelector('[name="tripType"]').value;

    // remove selected state from other cards in this carousel
    const track = form.closest('.carousel-track');
    track.querySelectorAll('.flight-card').forEach(card => {
        card.classList.remove('flight-card--selected');
        card.querySelector('.btn-select')?.setAttribute('aria-pressed', 'false');
    });

    // mark this card as selected
    form.closest('.flight-card').classList.add('flight-card--selected');
    form.querySelector('.btn-select')?.setAttribute('aria-pressed', 'true');

    // track which legs are selected
    selected[leg] = true;

    await fetch('/flights/select', { method: 'POST', body: new FormData(form) });

    // show the button when done
    const needsBoth = tripType === 'return';
    const isReady = needsBoth ? (selected.outbound && selected.return) : selected.outbound;

    document.getElementById('confirm-btn').classList.toggle('confirm-btn--visible', isReady);
}
