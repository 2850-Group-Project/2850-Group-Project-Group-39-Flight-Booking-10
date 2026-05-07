import AxeBuilder from '@axe-core/playwright';
import { chromium } from 'playwright';

const baseUrl = process.env.APP_BASE_URL || 'http://127.0.0.1:8080';
const chromePath = process.env.CHROME_PATH || undefined;
const axeTags = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22a', 'wcag22aa'];

function uniqueEmail(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}@example.com`;
}

function url(path) {
  return new URL(path, baseUrl).href;
}

async function postForm(context, path, form) {
  const response = await context.request.post(url(path), { form });
  if (response.status() >= 500) {
    throw new Error(`${path} returned ${response.status()}`);
  }
  return response;
}

async function auditPage(page, label, path) {
  const response = await page.goto(url(path), { waitUntil: 'load' });
  await page.waitForTimeout(800);

  const status = response ? response.status() : 0;
  if (status >= 500) {
    throw new Error(`${label} ${path} returned ${status}`);
  }

  const results = await new AxeBuilder({ page })
    .withTags(axeTags)
    .analyze();

  return {
    label,
    path,
    status,
    violations: results.violations,
  };
}

async function registerAndLoginUser(context) {
  const email = uniqueEmail('axe-user');
  const password = 'Password123!';

  await postForm(context, '/register', {
    firstName: 'Axe',
    lastName: 'User',
    email,
    password,
    confirmPassword: password,
  });
  await postForm(context, '/login', { email, password });

  return email;
}

async function registerAndLoginStaff(context) {
  const email = uniqueEmail('axe-staff');
  const password = 'StrongPass123!';

  await postForm(context, '/staff/register', {
    firstName: 'Axe',
    lastName: 'Staff',
    email,
    password,
    confirmPassword: password,
    role: 'admin',
    inviteCode: 'STAFF-CHECK',
  });
  await postForm(context, '/staff/login', { email, password });

  return email;
}

async function seedBookingFlow(context, userEmail) {
  await postForm(context, '/flights/select', {
    flightId: '1',
    fareId: '1',
    leg: 'outbound',
    tripType: 'oneway',
    origin: 'LHR',
    destination: 'DXB',
    departureDate: '2026-08-13',
    returnDate: '',
    adults: '1',
    children: '0',
    infants: '0',
  });

  await postForm(context, '/flights/passengers/submit', {
    'passengers[0][type]': 'adult',
    'passengers[0][title]': 'Mr',
    'passengers[0][firstName]': 'Axe',
    'passengers[0][lastName]': 'User',
    'passengers[0][dateOfBirth]': '1990-01-01',
    'passengers[0][gender]': 'M',
    'passengers[0][email]': userEmail,
    'passengers[0][nationality]': 'GB',
    'passengers[0][documentType]': 'passport',
    'passengers[0][documentNumber]': 'A1234567',
    'passengers[0][documentCountry]': 'GB',
    'passengers[0][documentExpiry]': '2030-01-01',
  });
}

async function run() {
  const browser = await chromium.launch({
    headless: true,
    ...(chromePath ? { executablePath: chromePath } : {}),
  });

  const reports = [];

  try {
    const publicContext = await browser.newContext();
    const publicPage = await publicContext.newPage();
    for (const [label, path] of [
      ['login', '/login'],
      ['register', '/register'],
      ['staff-login', '/staff/login'],
      ['staff-register', '/staff/register'],
      ['not-found', '/404'],
    ]) {
      reports.push(await auditPage(publicPage, label, path));
    }
    await publicContext.close();

    const userContext = await browser.newContext();
    const userPage = await userContext.newPage();
    const userEmail = await registerAndLoginUser(userContext);
    for (const [label, path] of [
      ['home', '/home'],
      ['profile', '/profile'],
      ['my-bookings', '/profile/bookings'],
      ['complaints', '/complaints'],
      ['profile-complaints', '/profile/complaints'],
      ['flight-search', '/flights/search?trip_type=oneway&origin=LHR&destination=DXB&departure_date=2026-08-13&adults=1&children=0&infants=0'],
    ]) {
      reports.push(await auditPage(userPage, label, path));
    }
    await seedBookingFlow(userContext, userEmail);
    for (const [label, path] of [
      ['flight-passengers', '/flights/passengers'],
      ['seat-selection', '/flights/seats'],
      ['payment', '/payment'],
    ]) {
      reports.push(await auditPage(userPage, label, path));
    }
    await userContext.close();

    const staffContext = await browser.newContext();
    const staffPage = await staffContext.newPage();
    await registerAndLoginStaff(staffContext);
    for (const [label, path] of [
      ['staff-dashboard', '/staff/dashboard'],
      ['staff-flights', '/staff/flights'],
      ['staff-bookings', '/staff/bookings'],
      ['staff-fare-class', '/staff/fare-class'],
      ['staff-notifications', '/staff/notifications'],
      ['staff-inquiries', '/staff/inquiries'],
    ]) {
      reports.push(await auditPage(staffPage, label, path));
    }
    await staffContext.close();
  } finally {
    await browser.close();
  }

  const failing = reports.filter((report) => report.violations.length > 0);

  for (const report of reports) {
    console.log(`${report.violations.length === 0 ? 'PASS' : 'FAIL'} ${report.label} ${report.path} (${report.status})`);
    for (const violation of report.violations) {
      console.log(`  ${violation.impact || 'unknown'} ${violation.id}: ${violation.help}`);
      for (const node of violation.nodes.slice(0, 3)) {
        console.log(`    ${node.target.join(', ')}`);
        if (node.failureSummary) {
          console.log(`    ${node.failureSummary.replace(/\n/g, '\n    ')}`);
        }
      }
    }
  }

  if (failing.length > 0) {
    const count = failing.reduce((total, report) => total + report.violations.length, 0);
    console.error(`\naxe accessibility check failed: ${count} violations across ${failing.length} pages.`);
    process.exitCode = 1;
  } else {
    console.log(`\naxe accessibility check passed for ${reports.length} pages.`);
  }
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
