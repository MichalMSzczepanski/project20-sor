'use strict';

const map = L.map('map', {zoomControl: false}).setView([52.07, 19.48], 6);
L.control.zoom({position: 'bottomright'}).addTo(map);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
	maxZoom: 18, attribution: '&copy; OpenStreetMap'
}).addTo(map);
const layer = L.layerGroup().addTo(map);
const routeLayer = L.layerGroup().addTo(map);

const statusEl = document.getElementById('status');
const listEl = document.getElementById('list');
const priorityEl = document.getElementById('priority');
const searchForm = document.getElementById('search');
const addressEl = document.getElementById('address');
const suggestionsEl = document.getElementById('suggestions');
let origin = null;
let markers = [];

listEl.replaceChildren(document.getElementById('intro').content.cloneNode(true));

// --- formatowanie -----------------------------------------------------------

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[c]);

// Źródło pisze nazwy raz WIELKIMI LITERAMI, raz normalnie. Poprawiamy tylko te w całości wielkimi.
const LOWERCASE_WORDS = new Set(['w', 'we', 'i', 'im.', 'z', 'ze', 'nad', 'dla', 'na', 'o', 'o.o.', 'sp.', 'z.o.o.']);
const ACRONYMS = {nzoz: 'NZOZ', spzoz: 'SPZOZ', zoz: 'ZOZ', sp: 'SP', mswia: 'MSWiA', mon: 'MON', uck: 'UCK', usk: 'USK', csk: 'CSK'};
function tidy(text) {
	if (!text || text !== text.toUpperCase() || !/\p{Lu}/u.test(text)) return text ?? '';
	return text.toLowerCase().split(/(\s+|-)/)
		.map((word, i) => ACRONYMS[word.replace(/\P{L}/gu, '')]
			? word.replace(/\p{L}+/u, ACRONYMS[word.replace(/\P{L}/gu, '')])
			: i > 0 && LOWERCASE_WORDS.has(word) ? word : word.replace(/\p{L}/u, (c) => c.toUpperCase()))
		.join('');
}

// Urzędowe dodatki, które nie pomagają rozpoznać szpitala; pełna nazwa zostaje w podpowiedzi.
const SPZOZ = /samodzielny\s+publiczny\s+(zespół\s+zakładów|zakład|zespół)\s+opieki\s+zdrowotnej\s*(-\s*)?/i;
const COMPANY_SUFFIX = /\s*(sp\.\s*z\s*o\.\s*o\.|spółka\s+z\s+ograniczoną\s+odpowiedzialnością|spółka\s+akcyjna|s\.a\.)\s*$/i;
const squash = (text) => text.replace(/\s{2,}/g, ' ').trim();
function shortName(name) {
	const full = tidy(name).trim();
	let short = squash(full.replace(COMPANY_SUFFIX, ''));
	if (SPZOZ.test(short)) {
		const rest = squash(short.replace(SPZOZ, ' '));
		// „SPZOZ w Augustowie” / „SPZOZ MSWiA” zamiast samego „w Augustowie” / „MSWiA”.
		short = !rest ? short : /^(w|we)\s/i.test(rest) || /^\S+$/.test(rest) ? 'SPZOZ ' + rest : rest;
	}
	return short.replace(/^["„]+|["”]+$/g, '').trim() || full;
}

/** Minuty jako HTML: liczby duże, jednostki małe. */
function duration(m, atLeast) {
	if (m == null) return '?';
	const prefix = atLeast ? '<small>≥ </small>' : '';
	if (m < 60) return `${prefix}${m}<small> min</small>`;
	const rest = m % 60;
	return `${prefix}${Math.floor(m / 60)}<small> h</small>${rest ? ` ${rest}<small> min</small>` : ''}`;
}
const plainDuration = (m, atLeast) => duration(m, atLeast).replace(/<[^>]+>/g, '');

// Czas do lekarza: 0 przy znanym czasie znaczy, że w kolejce nikt nie czeka — nie obiecujemy „0 min”.
const waitText = (s) => s.waitMinutes === 0 && !s.waitAtLeast ? 'brak kolejki' : plainDuration(s.waitMinutes, s.waitAtLeast);
// Bez wybranego priorytetu porównujemy według średniego, a rozrzut (średni–najniższy) pokazujemy obok.
const hasSpread = (s) => s.waitRange != null && s.waitRange.fromMinutes !== s.waitRange.toMinutes;
const spreadText = (s) => `${plainDuration(s.waitRange.fromMinutes)} – ${plainDuration(s.waitRange.toMinutes, s.waitRange.toAtLeast)} zależnie od priorytetu`;
const RANK_LABEL = {ranked: (i) => i + 1, farther: () => '+', withoutData: () => '?'};
const km = (d) => d.toLocaleString('pl-PL', {maximumFractionDigits: d < 10 ? 1 : 0}) + ' km';
const ago = (iso) => {
	const min = Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 60000));
	return min < 1 ? 'przed chwilą' : `${min} min temu`;
};

// Źródło potrafi podać kilka numerów w jednym polu: "22 473 53 33, 22 473 53 32".
const phones = (raw) => (raw || '').split(/[,;]/).map((p) => p.trim()).filter(Boolean);

// --- karty ------------------------------------------------------------------

function card(s, index, kind) {
	const known = kind !== 'withoutData';
	const classes = ['card', known ? '' : 'unknown', kind === 'farther' ? 'farther' : '',
		kind === 'ranked' && index === 0 ? 'best' : ''].join(' ');
	const tags = [];
	if (s.sorType === 'PEDIATRIC') tags.push('<span class="tag kids">SOR dla dzieci</span>');
	if (s.freshness === 'STALE') tags.push(`<span class="tag stale">dane nieaktualne · ${ago(s.dataGeneratedAt)}</span>`);
	if (s.freshness === 'NO_DATA') tags.push('<span class="tag stale">brak danych o kolejce</span>');
	if (s.travelMethod === 'STRAIGHT_LINE') tags.push('<span class="tag">dojazd szacowany zgrubnie</span>');

	const breakdown = known
		? [`dojazd <b>${plainDuration(s.travelMinutes)}</b> · ${km(s.distanceKm)}`,
			`do lekarza <b>${waitText(s)}</b>${hasSpread(s) ? ` <span class="muted">(${spreadText(s)})</span>` : ''}`,
			s.patientsPresent != null ? `na SOR <b>${s.patientsPresent}</b> os.` : '',
			`<span class="muted">dane ${ago(s.dataGeneratedAt)}</span>`]
		: [`${km(s.distanceKm)}`, s.patientsPresent != null ? `na SOR <b>${s.patientsPresent}</b> os.` : ''];

	const stages = s.stages.map((st) => `<span>${esc(st.name)}</span><span class="dim">${st.patients ?? '?'} os.</span><span>${esc(st.waitText)}</span>`).join('');
	const [firstPhone, ...otherPhones] = phones(s.phone);
	const tel = (p) => `tel:${esc(p.replace(/[^\d+]/g, ''))}`;
	const call = firstPhone ? `<a class="action" href="${tel(firstPhone)}">Zadzwoń ${esc(firstPhone)}</a>` : '';
	const more = otherPhones.length
		? `<p class="more-phones">Inne numery: ${otherPhones.map((p) => `<a href="${tel(p)}">${esc(p)}</a>`).join(', ')}</p>` : '';
	const details = stages || more
		? `<details class="stages"><summary>${stages ? 'Etapy kolejki' : 'Szczegóły'}</summary>${stages ? `<div class="stage-grid">${stages}</div>` : ''}${more}</details>` : '';

	return `<article class="${classes}" data-index="${index}" data-kind="${kind}" tabindex="0">
		<div class="rank">${RANK_LABEL[kind](index)}</div>
		<div class="place">
			<div class="name" title="${esc(tidy(s.hospitalName))}">${esc(shortName(s.hospitalName))}</div>
			<div class="address">${esc(tidy(s.address))}</div>
		</div>
		<div class="time">
			<div class="time-total">${duration(known ? s.totalMinutes : s.travelMinutes, known && s.waitAtLeast)}</div>
			<div class="time-label">${known ? 'łącznie' : 'sam dojazd'}</div>
		</div>
		<div class="breakdown">${breakdown.filter(Boolean).map((b) => `<span>${b}</span>`).join('')}</div>
		${tags.length ? `<div class="tags">${tags.join('')}</div>` : ''}
		${details}
		<div class="route-summary" hidden></div>
		<div class="actions">
			<button type="button" class="action go" data-route="CAR">Trasa autem</button>
			<button type="button" class="action" data-route="TRANSIT">Komunikacją</button>
			<details class="nav-menu">
				<summary class="action">Nawiguj ▾</summary>
				<div class="nav-links">${navLinks(s)}</div>
			</details>
			${call}
		</div>
	</article>`;
}

/** Zewnętrzna nawigacja: działa bez kluczy i prowadzi głosowo, czego nasza mapa nie robi. */
function navLinks(s) {
	const dest = `${s.lat},${s.lng}`;
	const links = [
		['Google Maps — autem', `https://www.google.com/maps/dir/?api=1&destination=${dest}&travelmode=driving`],
		['Google Maps — komunikacją', `https://www.google.com/maps/dir/?api=1&destination=${dest}&travelmode=transit`],
		['Apple Maps — autem', `https://maps.apple.com/directions?destination=${dest}&mode=driving`],
		['Apple Maps — komunikacją', `https://maps.apple.com/directions?destination=${dest}&mode=transit`]
	];
	return links.map(([label, href]) => `<a href="${href}" target="_blank" rel="noopener">${label}</a>`).join('');
}

// --- trasa na mapie ---------------------------------------------------------

const LEG_STYLE = {
	CAR: {color: '#1565c0', weight: 6, opacity: .85},
	WALK: {color: '#5f6b7a', weight: 4, dashArray: '2 8', opacity: .9},
	BUS: {color: '#2e7d32', weight: 6},
	TRAM: {color: '#ef6c00', weight: 6},
	SUBWAY: {color: '#283593', weight: 6},
	RAIL: {color: '#4e342e', weight: 6},
	REGIONAL_RAIL: {color: '#4e342e', weight: 6}
};
const LEG_NAME = {WALK: 'pieszo', BUS: 'autobus', TRAM: 'tramwaj', SUBWAY: 'metro', RAIL: 'pociąg',
	REGIONAL_RAIL: 'pociąg', FERRY: 'prom', CAR: 'autem'};
const clock = (iso) => new Date(iso).toLocaleTimeString('pl-PL', {hour: '2-digit', minute: '2-digit'});
const routeCache = new Map();

function routeSummary(route) {
	if (route.mode === 'CAR') {
		return `<b>Autem ${plainDuration(route.minutes)}</b> · ${km(route.distanceKm)} · bez korków`;
	}
	const steps = route.legs
		.filter((leg) => leg.mode !== 'WALK' || leg.minutes >= 3)
		.map((leg) => `${LEG_NAME[leg.mode] ?? leg.mode.toLowerCase()}${leg.line ? ' ' + esc(leg.line) : ''} ${leg.minutes} min`)
		.join(' → ');
	return `<b>Wyjście ${clock(route.departure)}, na miejscu ${clock(route.arrival)}</b> · ${plainDuration(route.minutes)}`
		+ ` · przesiadki: ${route.transfers}<br><span class="muted">${steps}</span>`;
}

async function showRoute(entry, mode) {
	const summaryEl = entry.card.querySelector('.route-summary');
	entry.card.querySelectorAll('[data-route]').forEach((b) => b.classList.toggle('selected', b.dataset.route === mode));
	summaryEl.hidden = false;
	summaryEl.textContent = mode === 'TRANSIT' ? 'Szukam połączenia…' : 'Wyznaczam trasę…';
	const key = `${origin.lat},${origin.lng}>${entry.sor.lat},${entry.sor.lng}:${mode}`;
	try {
		let route = routeCache.get(key);
		if (!route) {
			const params = new URLSearchParams({fromLat: origin.lat, fromLng: origin.lng, toLat: entry.sor.lat,
				toLng: entry.sor.lng, mode});
			const res = await fetch('/api/route?' + params);
			if (!res.ok) throw new Error((await res.json().catch(() => ({}))).detail || res.statusText);
			route = await res.json();
			routeCache.set(key, route);
		}
		routeLayer.clearLayers();
		const lines = route.legs.filter((leg) => leg.path.length > 1)
			.map((leg) => L.polyline(leg.path, LEG_STYLE[leg.mode] ?? {color: '#00838f', weight: 6}).addTo(routeLayer));
		summaryEl.innerHTML = routeSummary(route);
		if (lines.length) map.fitBounds(L.featureGroup(lines).getBounds(), {paddingTopLeft: [40, 60], paddingBottomRight: [40, 30]});
	} catch (e) {
		summaryEl.textContent = e.message;
	}
}

// --- mapa -------------------------------------------------------------------

function pin(s, label, kind, index) {
	const cls = kind === 'withoutData' ? 'unknown' : kind === 'farther' ? 'farther'
		: s.sorType === 'PEDIATRIC' ? 'kids' : index === 0 ? 'best' : '';
	return L.marker([s.lat, s.lng], {
		icon: L.divIcon({className: '', html: `<div class="pin ${cls}"><span>${label}</span></div>`, iconSize: [30, 30], iconAnchor: [15, 30]}),
		keyboard: false,
		// Pozycja w rankingu ma zawsze wygrać z położeniem na ekranie (Leaflet dodaje do z-index współrzędną y),
		// bo SOR z jednego kampusu potrafią stać w tym samym miejscu.
		zIndexOffset: kind === 'ranked' ? 100000 - index * 1000 : 0
	}).bindTooltip(`${esc(shortName(s.hospitalName))} — ${kind === 'withoutData' ? 'brak danych o kolejce' : plainDuration(s.totalMinutes, s.waitAtLeast) + ' łącznie'}`,
		{direction: 'top', offset: [0, -28]});
}

function highlight(key, scroll) {
	document.querySelectorAll('.card.active').forEach((el) => el.classList.remove('active'));
	document.querySelectorAll('.pin.active').forEach((el) => el.classList.remove('active'));
	const entry = markers.find((m) => m.key === key);
	if (!entry) return;
	entry.card.classList.add('active');
	entry.marker.getElement()?.querySelector('.pin')?.classList.add('active');
	if (scroll) entry.card.scrollIntoView({block: 'nearest', behavior: 'smooth'});
}

// --- dane -------------------------------------------------------------------

const patient = () => document.querySelector('input[name="patient"]:checked').value;

function setStatus(text, isError = false) {
	statusEl.textContent = text;
	statusEl.classList.toggle('error', isError);
}

async function load() {
	if (!origin) return;
	setStatus('Szukam…');
	const params = new URLSearchParams({lat: origin.lat, lng: origin.lng, limit: 10, patient: patient()});
	if (priorityEl.value) params.set('priority', priorityEl.value);
	try {
		const res = await fetch('/api/sor/nearest?' + params);
		if (!res.ok) throw new Error((await res.json().catch(() => ({}))).detail || res.statusText);
		const data = await res.json();
		render(data);
		const all = [...data.ranked, ...data.fartherFaster, ...data.withoutData];
		setStatus(all.length === 0 ? '' : 'Kolejność według łącznego czasu: dojazd + czas do lekarza. '
			+ (priorityEl.value ? '' : 'Nie wybrano priorytetu — liczymy jak dla średniego; przy niższym czeka się dłużej. ')
			+ (all.some((s) => s.travelMethod === 'STRAIGHT_LINE')
				? 'Część dojazdów szacowana zgrubnie (serwis tras nie odpowiedział).'
				: 'Dojazd po drogach, bez korków.'));
	} catch (e) {
		setStatus('Nie udało się pobrać danych: ' + e.message, true);
	}
}

function render({ranked, withoutData, fartherFaster, sorTypesKnown}) {
	layer.clearLayers();
	routeLayer.clearLayers();
	L.marker([origin.lat, origin.lng], {
		icon: L.divIcon({className: '', html: '<div class="you"></div>', iconSize: [16, 16]}),
		interactive: false
	}).addTo(layer);

	let html = sorTypesKnown ? '' : '<p class="notice">Nie wczytaliśmy jeszcze rejestru SOR dla dzieci — '
		+ 'na liście mogą być SOR dziecięce, które nie przyjmują dorosłych.</p>';
	html += ranked.map((s, i) => card(s, i, 'ranked')).join('');
	if (fartherFaster.length) {
		html += '<h2 class="section-title">Dalej, ale krócej się czeka</h2>'
			+ '<p class="section-note">Ponad 30 minut jazdy dłużej niż do najbliższego SOR. Kolejka zdąży się zmienić, '
			+ 'zanim dojedziesz — przed wyjazdem warto zadzwonić.</p>'
			+ fartherFaster.map((s, i) => card(s, i, 'farther')).join('');
	}
	if (withoutData.length) {
		html += `<h2 class="section-title">${ranked.length ? 'W pobliżu, bez danych o kolejce' : 'Najbliższe SOR — brak danych o kolejkach'}</h2>`
			+ '<p class="section-note">Nie wiemy, ile się tam czeka. Przed wyjazdem warto zadzwonić.</p>'
			+ withoutData.map((s, i) => card(s, i, 'withoutData')).join('');
	}
	listEl.innerHTML = html || '<div class="intro"><p class="intro-title">Nie znaleziono SOR w pobliżu.</p></div>';
	listEl.scrollTop = 0;

	markers = [];
	const add = (items, kind) => items.forEach((s, i) => {
		const key = `${kind}:${i}`;
		const marker = pin(s, RANK_LABEL[kind](i), kind, i).addTo(layer);
		const cardEl = listEl.querySelector(`.card[data-kind="${kind}"][data-index="${i}"]`);
		const entry = {key, marker, card: cardEl, sor: s};
		markers.push(entry);
		marker.on('click', () => highlight(key, true));
		cardEl.addEventListener('mouseenter', () => highlight(key, false));
		cardEl.querySelectorAll('[data-route]').forEach((button) =>
			button.addEventListener('click', () => { highlight(key, false); showRoute(entry, button.dataset.route); }));
		// Kliknięcie karty pokazuje trasę autem — jedno zapytanie, a od razu widać, którędy jechać.
		cardEl.addEventListener('click', (e) => {
			if (e.target.closest('a, summary, button, details')) return;
			highlight(key, false);
			marker.openTooltip();
			showRoute(entry, 'CAR');
		});
	});
	add(withoutData, 'withoutData');
	add(fartherFaster, 'farther');
	add(ranked, 'ranked');

	const focus = [...ranked.slice(0, 5), ...withoutData.slice(0, 3)].map((s) => [s.lat, s.lng]);
	if (focus.length) map.fitBounds(L.latLngBounds([[origin.lat, origin.lng], ...focus]),
		// Pinezka wystaje 30 px ponad swój punkt — więcej miejsca u góry, żeby nie ucinało numeru.
		{paddingTopLeft: [40, 60], paddingBottomRight: [40, 30]});
}

// --- wyszukiwanie adresu ----------------------------------------------------

function closeSuggestions() {
	suggestionsEl.hidden = true;
	suggestionsEl.replaceChildren();
}

function choosePlace(place) {
	closeSuggestions();
	addressEl.value = place.label;
	origin = {lat: place.lat, lng: place.lng};
	load();
}

// Szukamy dopiero po zatwierdzeniu — publiczny Nominatim nie pozwala na podpowiedzi przy każdym klawiszu.
async function searchAddress(event) {
	event.preventDefault();
	const query = addressEl.value.trim();
	if (query.length < 3) {
		setStatus('Wpisz co najmniej 3 znaki adresu.', true);
		return;
	}
	const button = searchForm.querySelector('button');
	button.disabled = true;
	setStatus('Szukam adresu…');
	try {
		const res = await fetch('/api/geocode?' + new URLSearchParams({q: query}));
		if (!res.ok) throw new Error((await res.json().catch(() => ({}))).detail || res.statusText);
		const places = await res.json();
		if (places.length === 0) {
			setStatus('Nie znaleźliśmy takiego adresu w Polsce. Spróbuj dopisać miasto albo kliknij miejsce na mapie.', true);
		} else if (places.length === 1) {
			choosePlace(places[0]);
		} else {
			setStatus('Wybierz właściwy adres:');
			suggestionsEl.replaceChildren(...places.map((place) => {
				const item = document.createElement('li');
				const option = document.createElement('button');
				option.type = 'button';
				option.textContent = place.label;
				option.addEventListener('click', () => choosePlace(place));
				item.append(option);
				return item;
			}));
			suggestionsEl.hidden = false;
			suggestionsEl.querySelector('button').focus();
		}
	} catch (e) {
		setStatus(e.message, true);
	} finally {
		button.disabled = false;
	}
}

// --- zdarzenia --------------------------------------------------------------

searchForm.addEventListener('submit', searchAddress);
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeSuggestions(); });
document.addEventListener('click', (e) => { if (!searchForm.contains(e.target)) closeSuggestions(); });

document.getElementById('locate').addEventListener('click', () => {
	if (!navigator.geolocation) {
		setStatus('Przeglądarka nie udostępnia lokalizacji — wpisz adres albo kliknij miejsce na mapie.', true);
		addressEl.focus();
		return;
	}
	setStatus('Ustalam lokalizację…');
	navigator.geolocation.getCurrentPosition(
		(p) => { origin = {lat: p.coords.latitude, lng: p.coords.longitude}; addressEl.value = ''; load(); },
		() => {
			setStatus('Nie udało się ustalić lokalizacji — wpisz adres powyżej albo kliknij miejsce na mapie.', true);
			addressEl.focus();
		},
		{enableHighAccuracy: true, timeout: 10000});
});
map.on('click', (e) => { origin = {lat: e.latlng.lat, lng: e.latlng.lng}; addressEl.value = ''; load(); });
priorityEl.addEventListener('change', load);
document.querySelectorAll('input[name="patient"]').forEach((el) => el.addEventListener('change', load));
