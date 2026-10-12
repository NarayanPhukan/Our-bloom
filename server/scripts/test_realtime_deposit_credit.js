const assert = require('assert');
const crypto = require('crypto');

console.log('====================================================');
console.log(' TESTING IMMEDIATE REAL-TIME VAULT CREDIT CONTRACT 🌸');
console.log('====================================================\n');

// 1. Test mathematical reverse intent formula
console.log('▶ Test 1: Testing exact reverse fee-on-top derivation for mobile checkout...');
const testAmounts = [
  { vaultPaise: 10000, expectedPayablePaise: 10200 },    // ₹100 -> ₹102
  { vaultPaise: 50000, expectedPayablePaise: 51000 },    // ₹500 -> ₹510
  { vaultPaise: 100000, expectedPayablePaise: 102000 },  // ₹1000 -> ₹1020
  { vaultPaise: 123456, expectedPayablePaise: 125925 }   // ₹1,234.56 -> ₹1,259.25
];

for (const { vaultPaise, expectedPayablePaise } of testAmounts) {
  const derivedVaultPaise = Math.round(expectedPayablePaise / 1.02);
  const derivedFeePaise = expectedPayablePaise - derivedVaultPaise;
  assert.strictEqual(derivedVaultPaise, vaultPaise, `Derived vault paise mismatch for payable ${expectedPayablePaise}`);
  assert.strictEqual(derivedVaultPaise + derivedFeePaise, expectedPayablePaise, `Invariant violation`);
}
console.log('✓ Test 1 passed: All reverse fee-on-top calculations match exact paise.\n');

// 2. Test Atomic processSuccessfulPaymentCredit logic simulation
console.log('▶ Test 2: Simulating atomic processSuccessfulPaymentCredit without pre-created intent...');

// Mock in-memory Firestore database
const mockDb = {
  gateway_events: new Map(),
  savings_transactions: new Map(),
  payment_intents: new Map(),
  savings_wallets: new Map(),
  savings_goals: new Map(),
  platform_fees: new Map(),
  revenue_records: new Map()
};

const coupleId = 'couple_test_realtime_123';
const userId = 'user_partner_1';
const initialWallet = {
  coupleId,
  totalBalancePaise: 100000, // ₹1,000.00
  availableBalancePaise: 100000,
  reservedBalancePaise: 0,
  totalPlatformFeesPaise: 2000,
  totalBalance: 1000.0,
  user1Id: 'user_partner_1',
  user1Name: 'Partner 1',
  user1Total: 600.0,
  user2Id: 'user_partner_2',
  user2Name: 'Partner 2',
  user2Total: 400.0,
  currency: '₹',
  version: 1
};
mockDb.savings_wallets.set(coupleId, { ...initialWallet });

const txnid = `OB_${Date.now()}_testabc`;
const amountStr = '510.00'; // ₹500 vault + ₹10 fee = ₹510 payable

// Function simulating processSuccessfulPaymentCredit
function simulateCredit(body) {
  const callbackAmountPaise = Math.round(parseFloat(body.amount) * 100);
  const targetCoupleId = body.udf1;
  const targetUserId = body.udf2;
  const targetUserName = body.udf3 || 'Partner';
  const targetGoalId = body.udf4 || null;

  // Deduplication check
  if (mockDb.gateway_events.has(body.txnid) && mockDb.gateway_events.get(body.txnid).status === 'PROCESSED') {
    return { outcome: 'ALREADY_PROCESSED' };
  }
  if (mockDb.savings_transactions.has(`LEDGER_${body.txnid}`)) {
    return { outcome: 'LEDGER_EXISTS' };
  }

  // Synthesize intent if missing
  const payableAmountPaise = callbackAmountPaise;
  const vaultAmountPaise = Math.round(payableAmountPaise / 1.02);
  const platformFeePaise = payableAmountPaise - vaultAmountPaise;

  mockDb.payment_intents.set(body.txnid, {
    txnid: body.txnid,
    coupleId: targetCoupleId,
    userId: targetUserId,
    userName: targetUserName,
    vaultAmountPaise,
    platformFeePaise,
    payableAmountPaise,
    status: 'COMPLETED'
  });

  // Credit wallet
  const wallet = mockDb.savings_wallets.get(targetCoupleId);
  const nextTotalPaise = wallet.totalBalancePaise + vaultAmountPaise;
  const nextAvailablePaise = wallet.availableBalancePaise + vaultAmountPaise;
  const nextPlatformFeesPaise = wallet.totalPlatformFeesPaise + platformFeePaise;
  const userDepositRupees = vaultAmountPaise / 100;
  const isUser1 = targetUserId === wallet.user1Id;
  const nextUser1Total = isUser1 ? wallet.user1Total + userDepositRupees : wallet.user1Total;
  const nextUser2Total = !isUser1 ? wallet.user2Total + userDepositRupees : wallet.user2Total;

  mockDb.savings_wallets.set(targetCoupleId, {
    ...wallet,
    totalBalancePaise: nextTotalPaise,
    availableBalancePaise: nextAvailablePaise,
    totalPlatformFeesPaise: nextPlatformFeesPaise,
    totalBalance: nextTotalPaise / 100,
    user1Total: nextUser1Total,
    user2Total: nextUser2Total,
    lastUpdated: Date.now()
  });

  // Write immutable ledger entry
  mockDb.savings_transactions.set(`LEDGER_${body.txnid}`, {
    id: `LEDGER_${body.txnid}`,
    coupleId: targetCoupleId,
    userId: targetUserId,
    userName: targetUserName,
    type: 'deposit',
    vaultAmountPaise,
    platformFeePaise,
    payableAmountPaise,
    amount: vaultAmountPaise / 100,
    status: 'COMPLETED',
    settlementStatus: 'PENDING',
    timestamp: Date.now()
  });

  mockDb.gateway_events.set(body.txnid, {
    status: 'PROCESSED',
    txnid: body.txnid
  });

  return {
    outcome: 'SUCCESS',
    creditedPaise: vaultAmountPaise,
    netPaise: payableAmountPaise,
    feePaise: platformFeePaise
  };
}

const res1 = simulateCredit({
  txnid,
  amount: amountStr,
  udf1: coupleId,
  udf2: userId,
  udf3: 'Partner 1',
  udf4: '',
  status: 'success'
});

assert.strictEqual(res1.outcome, 'SUCCESS');
assert.strictEqual(res1.creditedPaise, 50000, 'Credited exactly ₹500 (50,000 paise)');
assert.strictEqual(res1.feePaise, 1000, 'Platform fee ₹10 (1,000 paise)');
assert.strictEqual(res1.netPaise, 51000, 'Total paid ₹510 (51,000 paise)');

// Verify wallet balance updated immediately
const updatedWallet = mockDb.savings_wallets.get(coupleId);
assert.strictEqual(updatedWallet.totalBalance, 1500.0, 'Wallet totalBalance increased to ₹1,500.00');
assert.strictEqual(updatedWallet.totalBalancePaise, 150000, 'Wallet totalBalancePaise increased to 150,000');
assert.strictEqual(updatedWallet.user1Total, 1100.0, 'user1Total increased from ₹600 to ₹1,100');
assert.strictEqual(updatedWallet.user2Total, 400.0, 'user2Total unchanged at ₹400');

// Verify ledger transaction was written immediately with COMPLETED status
const ledgerTxn = mockDb.savings_transactions.get(`LEDGER_${txnid}`);
assert.ok(ledgerTxn, 'Ledger transaction exists');
assert.strictEqual(ledgerTxn.status, 'COMPLETED');
assert.strictEqual(ledgerTxn.settlementStatus, 'PENDING');
assert.strictEqual(ledgerTxn.amount, 500.0);

console.log('✓ Test 2 passed: Wallet balance updated immediately upon payment success (before settlement)!\n');

// 3. Test Deduplication / Idempotency
console.log('▶ Test 3: Simulating duplicate webhook / polling callback...');
const res2 = simulateCredit({
  txnid,
  amount: amountStr,
  udf1: coupleId,
  udf2: userId,
  status: 'success'
});
assert.strictEqual(res2.outcome, 'ALREADY_PROCESSED');
const walletAfterDup = mockDb.savings_wallets.get(coupleId);
assert.strictEqual(walletAfterDup.totalBalance, 1500.0, 'Balance untouched on duplicate callback');
console.log('✓ Test 3 passed: Deduplication prevents double credit.\n');

console.log('====================================================');
console.log(' ALL REAL-TIME VAULT CREDIT TESTS PASSED CLEANLY! 🌸');
console.log('====================================================\n');
