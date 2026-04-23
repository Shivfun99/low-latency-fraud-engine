#!/usr/bin/env node
const GATEWAY_URL = process.env.GATEWAY_URL || 'http:
const WS_GATEWAY_URL = process.env.WS_GATEWAY_URL || 'http:
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
const CITIES = ['Chennai', 'Mumbai', 'Delhi', 'Bangalore', 'Kolkata', 'Hyderabad', 'Pune', 'Jaipur', 'Ahmedabad', 'Lucknow'];
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
    location: randomPick(CITIES.filter(c => c !== user.city)), 
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
async function sendTransaction(txn, useTestEndpoint = false) {
  const url = useTestEndpoint ? WS_GATEWAY_URL : GATEWAY_URL;
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(txn),
    });
    if (response.ok) {
      const data = await response.json();
      const decision = data.decision || '—';
      const marker = decision === 'BLOCK' ? '🚫' : decision === 'REVIEW' ? '⚠️' : '✅';
      console.log(`${marker} [${txn.user_id}] ₹${txn.amount.toLocaleString()} @ ${txn.location} → ${data.transaction_id?.substring(0, 8) || 'sent'}`);
      return data;
    } else {
      console.error(`❌ HTTP ${response.status}: ${response.statusText}`);
    }
  } catch (e) {
    console.error(`❌ Failed: ${e.message}`);
  }
  return null;
}
async function runNormalMode() {
  console.log('═══════════════════════════════════════════');
  console.log('  Transaction Simulator — Normal Mode');
  console.log('  Sending mixed traffic every 1-3 seconds');
  console.log('═══════════════════════════════════════════\n');
  let count = 0;
  const useTestEndpoint = true; 
  while (true) {
    const user = randomPick(USERS);
    const rand = Math.random();
    let txn;
    if (rand < 0.7) {
      txn = normalTransaction(user);
    } else if (rand < 0.85) {
      txn = velocityAttack(user);
      console.log(`\n🔴 SUSPICIOUS: velocity attack on ${user.name}`);
    } else if (rand < 0.95) {
      txn = highAmountAnomaly(user);
      console.log(`\n🟡 SUSPICIOUS: high amount for ${user.name}`);
    } else {
      txn = cardTestingPattern(user);
      console.log(`\n🟠 SUSPICIOUS: card testing pattern for ${user.name}`);
    }
    await sendTransaction(txn, useTestEndpoint);
    count++;
    if (count % 10 === 0) {
      console.log(`\n📊 Sent ${count} transactions\n`);
    }
    await sleep(randomAmount(1000, 3000));
  }
}
async function runBurstMode() {
  console.log('═══════════════════════════════════════════');
  console.log('  Transaction Simulator — BURST MODE');
  console.log('  Simulating velocity attack on Rahul');
  console.log('═══════════════════════════════════════════\n');
  const victim = USERS[0]; 
  console.log(`🎯 Target: ${victim.name} (${victim.id})`);
  console.log(`📍 Home city: ${victim.city}\n`);
  console.log('─── Phase 1: Normal activity ─────────────\n');
  for (let i = 0; i < 3; i++) {
    await sendTransaction(normalTransaction(victim), true);
    await sleep(500);
  }
  await sleep(2000);
  console.log('\n─── Phase 2: ATTACK — Rapid transactions ──\n');
  const amounts = [5000, 10000, 25000, 40000, 15000, 8000];
  for (const amount of amounts) {
    const attack = velocityAttack(victim);
    attack.amount = amount;
    await sendTransaction(attack, true);
    await sleep(200);
  }
  await sleep(2000);
  console.log('\n─── Phase 3: Card testing pattern ─────────\n');
  for (let i = 0; i < 5; i++) {
    await sendTransaction(cardTestingPattern(victim), true);
    await sleep(150);
  }
  console.log('\n═══════════════════════════════════════════');
  console.log('  Burst simulation complete!');
  console.log('═══════════════════════════════════════════');
}
async function runFraudMode() {
  console.log('═══════════════════════════════════════════');
  console.log('  Transaction Simulator — FRAUD MODE');
  console.log('  80% fraudulent, 20% legitimate');
  console.log('═══════════════════════════════════════════\n');
  let count = 0;
  while (count < 50) {
    const user = randomPick(USERS);
    const rand = Math.random();
    let txn;
    if (rand < 0.2) {
      txn = normalTransaction(user);
    } else if (rand < 0.5) {
      txn = velocityAttack(user);
    } else if (rand < 0.8) {
      txn = highAmountAnomaly(user);
    } else {
      txn = cardTestingPattern(user);
    }
    await sendTransaction(txn, true);
    count++;
    await sleep(500);
  }
  console.log(`\n📊 Sent ${count} fraud-heavy transactions`);
}
async function runDemoMode() {
  console.log('═══════════════════════════════════════════');
  console.log('  🎬 LIVE DEMO — Fraud Detection System');
  console.log('═══════════════════════════════════════════\n');
  const rahul = USERS[0];
  const useTestEndpoint = false; 
  console.log('📖 Story: Rahul\'s card has been cloned.');
  console.log('   The attacker is in Delhi, Rahul is in Chennai.\n');
  await sleep(3000);
  console.log('Step 1: Rahul makes a normal purchase in Chennai...\n');
  await sendTransaction(normalTransaction(rahul), useTestEndpoint);
  await sleep(3000);
  console.log('\nStep 2: 30 seconds later, card used in DELHI!\n');
  const attack1 = velocityAttack(rahul);
  attack1.amount = 5000;
  attack1.location = 'Delhi';
  await sendTransaction(attack1, useTestEndpoint);
  await sleep(2000);
  console.log('\nStep 3: Rapid transactions follow...\n');
  for (const amount of [10000, 25000, 40000]) {
    const atk = velocityAttack(rahul);
    atk.amount = amount;
    atk.location = 'Delhi';
    await sendTransaction(atk, useTestEndpoint);
    await sleep(500);
  }
  await sleep(2000);
  console.log('\n✅ System should have detected and BLOCKED these transactions!');
  console.log('Total attempted fraud: ₹80,000');
  console.log('\n═══════════════════════════════════════════');
}
function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}
const mode = process.argv.find(a => a.startsWith('--mode'))?.split('=')[1] ||
             process.argv[process.argv.indexOf('--mode') + 1] ||
             'normal';
console.log(`\n🚀 Starting simulator in "${mode}" mode...\n`);
switch (mode) {
  case 'burst':
    runBurstMode();
    break;
  case 'fraud':
    runFraudMode();
    break;
  case 'demo':
    runDemoMode();
    break;
  default:
    runNormalMode();
}
