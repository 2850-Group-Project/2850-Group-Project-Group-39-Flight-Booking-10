var seatSelections = {};
var passengerList = [];
var totalPassengers = 0;
var runningTotal = 0;

function initPassengers() {
  var passengerDataEl = document.getElementById('passenger-data');
  passengerList = JSON.parse(passengerDataEl.textContent);
  totalPassengers = passengerList.length;
  for (var i = 0; i < passengerList.length; i++) {
    seatSelections[passengerList[i].id] = null;
  }
  updateUI();
}

function handleSeatClick(btn) {
  var seatCode = btn.getAttribute('data-seat-code');
  if (btn.classList.contains('occupied') || btn.disabled) return;

  var nextPassenger = null;
  for (var i = 0; i < passengerList.length; i++) {
    if (!seatSelections[passengerList[i].id]) {
      nextPassenger = passengerList[i];
      break;
    }
  }
  if (!nextPassenger) return;

  var keys = Object.keys(seatSelections);
  for (var i = 0; i < keys.length; i++) {
    if (seatSelections[keys[i]] === seatCode) {
      seatSelections[keys[i]] = null;
    }
  }

  seatSelections[nextPassenger.id] = seatCode;
  updateUI();
}

function updateUI() {
  var assigned = 0;
  var keys = Object.keys(seatSelections);
  for (var i = 0; i < keys.length; i++) {
    if (seatSelections[keys[i]] !== null) assigned++;
  }
  document.getElementById('seat-count').textContent = assigned + ' / ' + totalPassengers;

  var seatButtons = document.querySelectorAll('.seat:not(.occupied)');
  for (var i = 0; i < seatButtons.length; i++) {
    var btn = seatButtons[i];
    var seatCode = btn.getAttribute('data-seat-code');
    var isSelected = false;
    var values = Object.values(seatSelections);
    for (var j = 0; j < values.length; j++) {
      if (values[j] === seatCode) { isSelected = true; break; }
    }
    btn.classList.remove('selected');
    btn.setAttribute('aria-pressed', 'false');
    if (isSelected) {
      btn.classList.add('selected');
      btn.setAttribute('aria-pressed', 'true');
    }
    var position = btn.getAttribute('data-seat-position') || 'seat';
    btn.setAttribute('aria-label', 'Seat ' + seatCode + ', ' + position + ', ' + (isSelected ? 'selected' : 'available'));
  }

  var seatListHtml = '';
  for (var i = 0; i < keys.length; i++) {
    var pId = keys[i];
    var seatCode = seatSelections[pId];
    if (seatCode) {
      var p = null;
      for (var j = 0; j < passengerList.length; j++) {
        if (passengerList[j].id == pId) {
          p = passengerList[j];
          break;
        }
      }
      if (p) {
        seatListHtml += '<span class="seat-item"><strong>' + p.firstName + ' ' + p.lastName + '</strong> &rarr; ' + seatCode + '</span>';
      }
    }
  }
  document.getElementById('seat-list').innerHTML = seatListHtml;

  var allAssigned = true;
  for (var i = 0; i < keys.length; i++) {
    if (seatSelections[keys[i]] === null) { allAssigned = false; break; }
  }

  var resetBtn = document.getElementById('reset-btn');
  var continueBtn = document.getElementById('continue-btn');
  var hintText = document.getElementById('hint-text');
  var hasReturn = resetBtn.getAttribute('data-has-return') === 'true';
  var leg = resetBtn.getAttribute('data-leg');

  if (allAssigned) {
    if (leg === 'outbound' && hasReturn) {
      resetBtn.textContent = 'Next flight →';
      resetBtn.classList.add('btn-next-flight');
      continueBtn.style.display = 'none';
    } else {
      resetBtn.textContent = 'Reset all seats';
      resetBtn.classList.remove('btn-next-flight');
      continueBtn.style.display = 'inline-flex';
    }
    continueBtn.disabled = false;
    continueBtn.setAttribute('aria-disabled', 'false');
    hintText.textContent = '✓ All passengers assigned! Click continue.';
    document.getElementById('current-passenger-name').textContent = 'All passengers assigned';
  } else {
    resetBtn.textContent = 'Reset all seats';
    resetBtn.classList.remove('btn-next-flight');
    continueBtn.disabled = true;
    continueBtn.setAttribute('aria-disabled', 'true');

    var nextPassenger = null;
    for (var i = 0; i < passengerList.length; i++) {
      if (!seatSelections[passengerList[i].id]) { nextPassenger = passengerList[i]; break; }
    }
    if (nextPassenger) {
      var name = nextPassenger.firstName + ' ' + nextPassenger.lastName;
      hintText.textContent = 'Select a seat for ' + name;
      document.getElementById('current-passenger-name').textContent = name;
    }
  }

  var summaryEl = document.getElementById('fare-summary');
  var totalEl = document.getElementById('fare-running-total');
  var assignedCountEl = document.getElementById('fare-assigned-count');

  if (summaryEl && totalEl) {
    var currency = summaryEl.getAttribute('data-currency');
    var baseFare = parseFloat(summaryEl.getAttribute('data-price'));
    runningTotal = 0;

    var keys = Object.keys(seatSelections);
    for (var i = 0; i < keys.length; i++) {
      var seatCode = seatSelections[keys[i]];
      if (!seatCode) continue;

      var seatBtn = document.querySelector('[data-seat-code="' + seatCode + '"]');
      var seatPrice = seatBtn ? parseFloat(seatBtn.getAttribute('data-price')?.replace('£', '')) : NaN;
      runningTotal += isNaN(seatPrice) ? baseFare : seatPrice;
    }

    totalEl.textContent = currency + ' ' + runningTotal.toFixed(2);
    assignedCountEl.textContent = assigned + ' / ' + totalPassengers;
  }
}

document.addEventListener('DOMContentLoaded', function() {
  initPassengers();

  // Set the correct form action based on which leg this page is for
  var form = document.getElementById('seat-form');
  var leg = form.getAttribute('data-leg');
  if (leg === 'return') {
    form.action = '/flights/seats/return';
  }

  var seatButtons = document.querySelectorAll('.seat:not(.occupied)');
  for (var i = 0; i < seatButtons.length; i++) {
    seatButtons[i].addEventListener('click', function(e) {
      e.preventDefault();
      handleSeatClick(this);
    });
  }

  document.getElementById('continue-btn').addEventListener('click', function(e) {
    e.preventDefault();
    document.getElementById('selected-seats-input').value = JSON.stringify(seatSelections);
    document.getElementById('booking-total-input').value = runningTotal.toFixed(2);
    form.submit();
  });

  document.getElementById('reset-btn').addEventListener('click', function(e) {
    e.preventDefault();
    var resetBtn = document.getElementById('reset-btn');

    if (resetBtn.classList.contains('btn-next-flight')) {
      document.getElementById('selected-seats-input').value = JSON.stringify(seatSelections);
      document.getElementById('booking-total-input').value = runningTotal.toFixed(2);
      form.action = '/flights/seats/outbound';
      form.submit();
      return;
    }

    for (var i = 0; i < passengerList.length; i++) {
      seatSelections[passengerList[i].id] = null;
    }
    updateUI();
  });

  document.getElementById('seat-form').addEventListener('keydown', function(e) {
    if ((e.key === 'Enter' || e.key === ' ') && e.target.classList.contains('seat')) {
      e.preventDefault();
      handleSeatClick(e.target);
    }
  });
  
  // SEAT TOOLTIP
  var tooltip = document.getElementById('seat-tooltip');
  
  document.querySelectorAll('.seat').forEach(function(btn) {
    btn.addEventListener('mouseenter', function(e) {
      var code = btn.getAttribute('data-seat-code');
      var position = btn.getAttribute('data-seat-position') || '—';
      var status = btn.getAttribute('data-seat-status') || '—';
      var price = btn.getAttribute('data-price') || 'Not available';
  
      document.getElementById('tooltip-code').textContent = 'Seat ' + code;
      document.getElementById('tooltip-position').textContent = position;
      document.getElementById('tooltip-status').textContent = status;
      document.getElementById('tooltip-price').textContent = price;
  
      tooltip.classList.add('visible');
    });
  
    btn.addEventListener('mouseleave', function() {
      tooltip.classList.remove('visible');
    });
  });
});
