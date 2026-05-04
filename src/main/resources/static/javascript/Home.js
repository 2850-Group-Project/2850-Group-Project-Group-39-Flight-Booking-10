// Trip type (return/one-way)
function setTripType(type, btn) {
    document.querySelectorAll('.trip-tab').forEach(t => {
        t.classList.remove('active');
        t.setAttribute('aria-pressed', 'false');
    });
    btn.classList.add('active');
    btn.setAttribute('aria-pressed', 'true');
    document.getElementById('tripType').value = type;

    const returnField = document.getElementById('returnField');
    const returnDate = document.getElementById('returnDate');

    if (type === 'oneway') {
        returnField.style.display = 'none';
        returnDate.removeAttribute('required');
    } else {
        returnField.style.display = '';
        returnDate.setAttribute('required', '');
    }
}

// Swap button functionality
function swapAirports() {
    const originInput = document.getElementById('origin-input');
    const destinationInput = document.getElementById('destination-input');
    const originHidden = document.getElementById('origin-value');
    const destinationHidden = document.getElementById('destination-value');
    const tmpVisible = originInput.value;
    const tmpHidden = originHidden.value;

    originInput.value = destinationInput.value;
    destinationInput.value = tmpVisible;
    originHidden.value = destinationHidden.value;
    destinationHidden.value = tmpHidden;
}

// Passengers
const pax = { adults: 1, children: 0, infants: 0 };

function togglePax() {
    const dropdown = document.getElementById('paxDropdown');
    const toggle = document.getElementById('paxToggle');
    const isOpen = dropdown.classList.toggle('open');
    toggle.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
}

document.addEventListener('click', e => {
    if (!e.target.closest('.pax-wrapper')) {
        document.getElementById('paxDropdown').classList.remove('open');
        document.getElementById('paxToggle')?.setAttribute('aria-expanded', 'false');
    }
});

function changePax(type, delta) {
    const min = type === 'adults' ? 1 : 0;
    pax[type] = Math.max(min, Math.min(9, pax[type] + delta));

    document.getElementById(type + 'Count').textContent = pax[type];
    document.getElementById(type + 'Input').value = pax[type];

    updatePaxLabel();
}

function updatePaxLabel() {
    // const total = pax.adults + pax.children + pax.infants;
    const parts = [];
    if (pax.adults) parts.push(`${pax.adults} Adult${pax.adults > 1 ? 's' : ''}`);
    if (pax.children) parts.push(`${pax.children} Child${pax.children > 1 ? 'ren' : ''}`);
    if (pax.infants) parts.push(`${pax.infants} Infant${pax.infants > 1 ? 's' : ''}`);
    document.getElementById('paxLabel').textContent = parts.join(', ');
}

// Set min date to today
const today = new Date().toISOString().split('T')[0];
document.querySelectorAll('input[type="date"]').forEach(i => i.setAttribute('min', today));

// Search suggestion autocomplete
function initAirportAutocomplete(inputId, dropdownId, hiddenId) {
    const input = document.getElementById(inputId);
    const dropdown = document.getElementById(dropdownId);
    const hidden = document.getElementById(hiddenId);

    let intervalTimer;

    input.addEventListener("input", () =>{
        clearTimeout(intervalTimer);
        const q = input.value.trim();

        if (q.length < 2) {
            closeDropdown();
            return;
        }

        // wait for 200ms after the user stops typing to send the request to get suggestions
        intervalTimer = setTimeout(async () => {
            const res = await fetch(`/airports/search?q=${encodeURIComponent(q)}`);
            const airports = await res.json();
            renderDropdown(airports);
        }, 200);
    });

    function renderDropdown(airports) {
        dropdown.innerHTML = "";

        if (airports.length == 0) {
            closeDropdown();
            return;
        }

        airports.forEach((airport, index) => {
            const li = document.createElement("li");
            li.className = "autocomplete-item";
            li.id = `${dropdownId}-option-${index}`;
            li.setAttribute("role", "option");
            li.setAttribute("aria-selected", "false");
            li.textContent = `${airport.city} - ${airport.name} (${airport.iataCode})`;

            li.addEventListener("mousedown", (e) => {
                e.preventDefault();
                input.value = airport.city;
                hidden.value = airport.iataCode;
                closeDropdown();
            });

            dropdown.appendChild(li);
        });

        dropdown.classList.add("open");
        input.setAttribute("aria-expanded", "true");
    }

    function closeDropdown() {
        dropdown.innerHTML = "";
        dropdown.classList.remove("open");
        input.setAttribute("aria-expanded", "false");
        input.removeAttribute("aria-activedescendant");

        flightIndex = -1;
    }

    input.addEventListener("click", (e) => {
        if (!input.contains(e.target) && !dropdown.contains(e.target)) {
            closeDropdown();
        }
    })

    let flightIndex = -1;

    input.addEventListener("keydown", (e) => {
        const items = dropdown.querySelectorAll(".autocomplete-item")
        if (!items.length) return;

        if (e.key === "ArrowDown") {
            e.preventDefault();
            flightIndex = (flightIndex + 1) % items.length; // using mod to wrap around
            updateActive(items);
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            flightIndex = (flightIndex - 1 + items.length) % items.length;
            updateActive(items); 
        } else if (e.key === "Enter") {
            e.preventDefault();
            if (flightIndex >= 0 && items[flightIndex]) {
                items[flightIndex].dispatchEvent(new MouseEvent("mousedown")); // simulate click when entering
            }
        } else if (e.key == "Escape") {
            closeDropdown();
        }

    })

    function updateActive(items) {
        items.forEach((item, i) => {
            item.classList.toggle("autocomplete-item--active", i === flightIndex);
            item.setAttribute("aria-selected", i === flightIndex ? "true" : "false");
        });

        if (items[flightIndex]) {
            input.setAttribute("aria-activedescendant", items[flightIndex].id);
        }
        items[flightIndex]?.scrollIntoView({ block: "nearest" });
    }
}

document.querySelector("form").addEventListener("submit", (e) => {
    const originInput = document.getElementById("origin-input");
    const destinationInput = document.getElementById("destination-input");

    const originHidden = document.getElementById("origin-value");
    const destinationHidden = document.getElementById("destination-value");

    // If user does not select anything from dropdown, default to using entered text
    if (!originHidden.value) {
        originHidden.value = originInput.value;
    }

    if (!destinationHidden.value) {
        destinationHidden.value = destinationInput.value
    }
})

initAirportAutocomplete("origin-input", "origin-dropdown", "origin-value");
initAirportAutocomplete("destination-input", "destination-dropdown", "destination-value");
