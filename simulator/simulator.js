#!/usr/bin/env node
// Sandbox ingestion still runs the real Kafka -> Redis -> C++ scoring pipeline.
const GATEWAY_URL = process.env.GATEWAY_URL || 'http://localhost:8080/api/v1/sandbox/ingest';
const USERS = [
  { id: 'user_rahul_001', name: 'Rahul Sharma', city: 'Chennai', avgSpend: 2500 },
  { id: 'user_priya_002', name: 'Priya Patel', city: 'Mumbai', avgSpend: 5000 },
  { id: 'user_amit_003', name: 'Amit Kumar', city: 'Delhi', avgSpend: 3000 },
  { id: 'user_sneha_004', name: 'Sneha Reddy', city: 'Bangalore', avgSpend: 8000 },
  { id: 'user_vikram_005', name: 'Vikram Singh', city: 'Kolkata', avgSpend: 1500 },
  { id: 'user_deepa_006', name: 'Deepa Nair', city: 'Hyderabad', avgSpend: 4000 },
  { id: 'user_arjun_007', name: 'Arjun Menon', city: 'Pune', avgSpend: 6000 },
  { id: 'user_kavya_008', name: 'Kavya Iyer', city: 'Jaipur', avgSpend: 2000 },
];
const MERCHANTS = [
  { id: 'merchant_amazon', name: 'Amazon India', category: 'ECOMMERCE' },
  { id: 'merchant_flipkart', name: 'Flipkart', category: 'ECOMMERCE' },
  { id: 'merchant_swiggy', name: 'Swiggy', category: 'FOOD_DELIVERY' },
  { id: 'merchant_bigbasket', name: 'BigBasket', category: 'GROCERY' },
  { id: 'merchant_uber', name: 'Uber India', category: 'TRANSPORT' },
  { id: 'merchant_petrol', name: 'Indian Oil', category: 'FUEL' },
  { id: 'merchant_reliance', name: 'Reliance Digital', category: 'ELECTRONICS' },
  { id: 'merchant_apollo', name: 'Apollo Pharmacy', category: 'HEALTHCARE' },
  { id: 'merchant_pvr', name: 'PVR Cinemas', category: 'ENTERTAINMENT' },
  { id: 'merchant_zara', name: 'Zara India', category: 'FASHION' },
];
const CITIES = [
  'Chennai',
  'Mumbai',
  'Delhi',
  'Bangalore',
  'Kolkata',
  'Hyderabad',
  'Pune',
  'Jaipur',
  'Ahmedabad',
  'Lucknow',
];
const CHANNELS = ['ONLINE', 'POS', 'UPI', 'ATM', 'MOBILE'];
const CARD_TYPES = ['VISA', 'MASTERCARD', 'RUPAY', 'AMEX'];
function randomPick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}
function randomAmount(min, max) {
  return Math.round((Math.random() * (max - min) + min) * 100) / 100;
}
function generateDeviceId() {
  const prefixes = ['iPhone14', 'Samsung_S23', 'Pixel_8', 'OnePlus_12', 'Xiaomi_14'];
  return `${randomPick(prefixes)}_${Math.random().toString(36).substring(2, 8)}`;
}
function generateIP() {
  return `${Math.floor(Math.random() * 223) + 1}.${Math.floor(Math.random() * 256)}.${Math.floor(Math.random() * 256)}.${Math.floor(Math.random() * 256)}`;
}
function normalTransaction(user) {
  const merchant = randomPick(MERCHANTS);
  return {
    user_id: user.id,
    amount: randomAmount(user.avgSpend * 0.1, user.avgSpend * 2),
    currency: 'INR',
    merchant_id: merchant.id,
    merchant_name: merchant.name,
    category: merchant.category,
    card_type: randomPick(CARD_TYPES),
    location: user.city,
    device_id: `${user.id}_device_primary`,
    ip_address: generateIP(),
    channel: randomPick(CHANNELS),
  };
}
function velocityAttack(user) {
  const merchant = randomPick(MERCHANTS);
  return {
    user_id: user.id,
    amount: randomAmount(5000, 40000),
    currency: 'INR',
    merchant_id: merchant.id,
    merchant_name: merchant.name,
    category: merchant.category,
    card_type: randomPick(CARD_TYPES),
    location: randomPick(CITIES.filter((c) => c !== user.city)),
    device_id: generateDeviceId(),
    ip_address: generateIP(),
    channel: 'POS',
  };
}
function highAmountAnomaly(user) {
  return {
    user_id: user.id,
    amount: randomAmount(50000, 200000),
    currency: 'INR',
    merchant_id: randomPick(MERCHANTS).id,
    merchant_name: 'Luxury Electronics Store',
    category: 'ELECTRONICS',
    card_type: randomPick(CARD_TYPES),
    location: randomPick(CITIES),
    device_id: generateDeviceId(),
    ip_address: generateIP(),
    channel: 'POS',
  };
}
function cardTestingPattern(user) {
  return {
    user_id: user.id,
    amount: randomAmount(1, 50),
    currency: 'INR',
    merchant_id: randomPick(MERCHANTS).id,
    merchant_name: randomPick(MERCHANTS).name,
    category: randomPick(MERCHANTS).category,
    card_type: randomPick(CARD_TYPES),
    location: randomPick(CITIES),
    device_id: generateDeviceId(),
    ip_address: generateIP(),
    channel: 'ONLINE',
  };
}
function parseOptions(args) {
  const options = { mode: 'normal', count: null, interval: null };
  for (let index = 0; index < args.length; index++) {
    const [key, inlineValue] = args[index].split('=');
    if (!['--mode', '--count', '--interval'].includes(key))
      throw new Error(`Unknown option: ${key}`);
    const value = inlineValue ?? args[++index];
    if (value == null) throw new Error(`Missing value for ${key}`);
    options[key.slice(2)] = key === '--mode' ? value : Number(value);
  }
  if (!['normal', 'burst', 'fraud', 'demo'].includes(options.mode))
    throw new Error('Mode must be normal, burst, fraud or demo');
  if (options.count !== null && (!Number.isSafeInteger(options.count) || options.count < 1))
    throw new Error('Count must be a positive integer');
  if (
    options.interval !== null &&
    (!Number.isSafeInteger(options.interval) || options.interval < 0)
  )
    throw new Error('Interval must be a nonnegative integer in milliseconds');
  return options;
}

async function sendTransaction(transaction, url = GATEWAY_URL) {
  const headers = { 'Content-Type': 'application/json' };
  if (process.env.API_TOKEN) headers.Authorization = `Bearer ${process.env.API_TOKEN}`;
  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(transaction),
    signal: AbortSignal.timeout(15000),
  });
  const body = await response.text();
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${body}`);
  const data = JSON.parse(body);
  if (!data.transaction_id) throw new Error('Ingestion did not return a transaction_id');
  console.log(
    `[${transaction.user_id}] ₹${transaction.amount.toLocaleString()} @ ${transaction.location} -> ${data.transaction_id} (${data.decision || data.status || 'ACCEPTED'})`
  );
  return data;
}

async function run(options) {
  const plannedCount =
    options.count ?? (options.mode === 'normal' ? Infinity : options.mode === 'fraud' ? 50 : 14);
  const interval =
    options.interval ?? (options.mode === 'normal' ? 1500 : options.mode === 'demo' ? 1000 : 200);
  console.log(`Fraud transaction simulator: ${options.mode} -> ${GATEWAY_URL}`);
  console.log(
    'Transactions are submitted for asynchronous scoring. Watch decisions in the dashboard.'
  );
  if (options.mode === 'demo' || options.mode === 'burst') {
    console.log(
      'Scenario: normal purchases in Chennai, then a new device in Delhi and rapid transactions.'
    );
  }
  let running = true;
  const stop = () => {
    running = false;
  };
  process.once('SIGINT', stop);
  process.once('SIGTERM', stop);
  let sent = 0;
  try {
    while (running && sent < plannedCount) {
      let transaction;
      if (options.mode === 'demo' || options.mode === 'burst') {
        const user = USERS[0];
        if (sent < 3) transaction = normalTransaction(user);
        else if (sent < 9) {
          transaction = velocityAttack(user);
          transaction.location = 'Delhi';
          transaction.amount = [5000, 10000, 25000, 40000, 15000, 8000][(sent - 3) % 6];
        } else transaction = cardTestingPattern(user);
      } else {
        const user = randomPick(USERS);
        const roll = Math.random();
        const normalShare = options.mode === 'fraud' ? 0.2 : 0.7;
        transaction =
          roll < normalShare
            ? normalTransaction(user)
            : randomPick([velocityAttack, highAmountAnomaly, cardTestingPattern])(user);
      }
      await sendTransaction(transaction);
      sent++;
      if (running && sent < plannedCount && interval)
        await new Promise((resolve) => setTimeout(resolve, interval));
    }
  } finally {
    process.removeListener('SIGINT', stop);
    process.removeListener('SIGTERM', stop);
  }
  console.log(
    `Submitted ${sent} transactions. Scoring outcomes are available asynchronously in the live feed.`
  );
  return sent;
}

if (require.main === module) {
  Promise.resolve()
    .then(() => run(parseOptions(process.argv.slice(2))))
    .catch((error) => {
      console.error(`Simulator failed: ${error.message}`);
      process.exitCode = 1;
    });
}
module.exports = { parseOptions, sendTransaction, run };
