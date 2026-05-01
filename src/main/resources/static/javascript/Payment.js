let pointsActive = false;

function togglePoints() {
    pointsActive = !pointsActive;
    document.getElementById('pointsToRedeem').value = pointsActive ? {{ pointsAvailable }} : 0;
    document.getElementById('usePointsBtn').textContent = pointsActive ? 'Points Applied' : 'Use Points';
}