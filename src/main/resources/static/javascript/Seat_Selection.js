var seatSelections = {};
var passengerList = [];
var totalPassengers = 0;

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
      if (values[j] === seatCode) {
        isSelected = true;
        break;
      }
    }
    
    btn.classList.remove('selected');
    if (isSelected) {
      btn.classList.add('selected');
    }
  }

  var seatListHtml = '';
  var keys = Object.keys(seatSelections);
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
  var keys = Object.keys(seatSelections);
  for (var i = 0; i < keys.length; i++) {
    if (seatSelections[keys[i]] === null) {
      allAssigned = false;
      break;
    }
  }
  document.getElementById('continue-btn').disabled = !allAssigned;

  if (allAssigned) {
    document.getElementById('hint-text').textContent = '✓ All passengers assigned! Click continue.';
  } else {
    var nextPassenger = null;
    for (var i = 0; i < passengerList.length; i++) {
      if (!seatSelections[passengerList[i].id]) {
        nextPassenger = passengerList[i];
        break;
      }
    }
    if (nextPassenger) {
      document.getElementById('hint-text').textContent = 'Select a seat for ' + nextPassenger.firstName + ' ' + nextPassenger.lastName;
    }
  }
}

document.addEventListener('DOMContentLoaded', function() {
  initPassengers();

  var seatButtons = document.querySelectorAll('.seat:not(.occupied)');
  for (var i = 0; i < seatButtons.length; i++) {
    seatButtons[i].addEventListener('click', function(e) {
      e.preventDefault();
      handleSeatClick(this);
    });
  }

  document.getElementById('continue-btn').addEventListener('click', function(e) {
    e.preventDefault();
    var seatInput = document.getElementById('selected-seats-input');
    seatInput.value = JSON.stringify(seatSelections);
    document.getElementById('seat-form').submit();
  });

  document.getElementById('reset-btn').addEventListener('click', function(e) {
    e.preventDefault();
    for (var i = 0; i < passengerList.length; i++) {
      seatSelections[passengerList[i].id] = null;
    }
    updateUI();
  });
});