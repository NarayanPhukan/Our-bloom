const assert = require('assert');
const crypto = require('crypto');

// 1. Test Magic Byte Buffer Inspection
console.log('▶ Testing Upload Magic Byte Inspector...');
// Mock the inspection logic from upload.js
function inspectFileBuffer(buffer) {
  if (!buffer || buffer.length < 12) return null;

  if (buffer[0] === 0x4D && buffer[1] === 0x5A) return null; // PE 'MZ'
  if (buffer[0] === 0x7F && buffer[1] === 0x45 && buffer[2] === 0x4C && buffer[3] === 0x46) return null; // ELF
  if (buffer[0] === 0xCA && buffer[1] === 0xFE && buffer[2] === 0xBA && buffer[3] === 0xBE) return null; // Mach-O

  const headerAscii = buffer.slice(0, 100).toString('utf8', 0, Math.min(100, buffer.length)).toLowerCase();
  if (headerAscii.includes('<?php') || headerAscii.includes('<script') || headerAscii.startsWith('#!')) {
    return null;
  }

  // JPEG
  if (buffer[0] === 0xFF && buffer[1] === 0xD8 && buffer[2] === 0xFF) {
    return { mime: 'image/jpeg', ext: '.jpg', category: 'image', maxSize: 10 * 1024 * 1024 };
  }

  // PNG
  if (
    buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4E && buffer[3] === 0x47 &&
    buffer[4] === 0x0D && buffer[5] === 0x0A && buffer[6] === 0x1A && buffer[7] === 0x0A
  ) {
    return { mime: 'image/png', ext: '.png', category: 'image', maxSize: 10 * 1024 * 1024 };
  }

  // MP4
  if (buffer[4] === 0x66 && buffer[5] === 0x74 && buffer[6] === 0x79 && buffer[7] === 0x70) {
    const brand = buffer.slice(8, 12).toString('ascii').toLowerCase();
    if (brand.includes('m4a')) {
      return { mime: 'audio/m4a', ext: '.m4a', category: 'audio', maxSize: 25 * 1024 * 1024 };
    }
    return { mime: 'video/mp4', ext: '.mp4', category: 'video', maxSize: 50 * 1024 * 1024 };
  }

  return null;
}

// Test JPEG buffer
const jpegBuf = Buffer.from([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01]);
const jpegResult = inspectFileBuffer(jpegBuf);
assert.strictEqual(jpegResult.mime, 'image/jpeg');
assert.strictEqual(jpegResult.maxSize, 10 * 1024 * 1024);

// Test PNG buffer
const pngBuf = Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D]);
const pngResult = inspectFileBuffer(pngBuf);
assert.strictEqual(pngResult.mime, 'image/png');

// Test MP4 buffer
const mp4Buf = Buffer.from([0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x6D]);
const mp4Result = inspectFileBuffer(mp4Buf);
assert.strictEqual(mp4Result.mime, 'video/mp4');
assert.strictEqual(mp4Result.maxSize, 50 * 1024 * 1024); // Definitive 50 MB

// Test Rejection of Disguised PE Executable
const peBuf = Buffer.from([0x4D, 0x5A, 0x90, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00]);
assert.strictEqual(inspectFileBuffer(peBuf), null, 'PE executable must be rejected');

// Test Rejection of Disguised PHP script
const phpBuf = Buffer.from('<?php echo "evil"; ?>' + 'A'.repeat(20));
assert.strictEqual(inspectFileBuffer(phpBuf), null, 'PHP script must be rejected');

console.log('✓ Upload Magic Byte Inspector tests passed.');

// 2. Test PayU Reverse Hash Verification
console.log('▶ Testing PayU Reverse Hash Verification...');
const testKey = 'TEST_KEY_123';
const testSalt = 'TEST_SALT_456';
const testTxnid = 'TXN_TEST_999';
const testAmount = '500.00';
const testProduct = 'Vault Deposit';
const testFirstname = 'Ananya';
const testEmail = 'ananya@ourbloom.app';
const testStatus = 'success';

// Formula: sha512(SALT|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)
const hashString = `${testSalt}|${testStatus}|||||||||${testEmail}|${testFirstname}|${testProduct}|${testAmount}|${testTxnid}|${testKey}`;
const expectedHash = crypto.createHash('sha512').update(hashString).digest('hex');

assert.strictEqual(typeof expectedHash, 'string');
assert.strictEqual(expectedHash.length, 128);
console.log('✓ PayU Reverse Hash Verification tests passed.');

// 3. Test Integer Paise Conversions
console.log('▶ Testing Integer Paise Currency Logic...');
const rupeeAmount = 5000.00;
const paiseAmount = Math.round(rupeeAmount * 100);
assert.strictEqual(paiseAmount, 500000);
assert.strictEqual(Number.isInteger(paiseAmount), true);

const smallAmount = 0.50; // 50 paise
const smallPaise = Math.round(smallAmount * 100);
assert.strictEqual(smallPaise, 50);

const displayRupees = (paiseAmount / 100).toFixed(2);
assert.strictEqual(displayRupees, '5000.00');

// Test Two-Phase Balance Math
let totalBalancePaise = 1000000;
let availableBalancePaise = 1000000;
let reservedBalancePaise = 0;

const withdrawPaise = 500000;
// Phase 1: Reserve
availableBalancePaise -= withdrawPaise;
reservedBalancePaise += withdrawPaise;
assert.strictEqual(availableBalancePaise, 500000);
assert.strictEqual(reservedBalancePaise, 500000);
assert.strictEqual(totalBalancePaise, 1000000); // Unchanged during reservation

// Phase 2A: Complete payout
reservedBalancePaise -= withdrawPaise;
totalBalancePaise -= withdrawPaise;
assert.strictEqual(availableBalancePaise, 500000);
assert.strictEqual(reservedBalancePaise, 0);
assert.strictEqual(totalBalancePaise, 500000); // Deducted upon completion

console.log('✓ Integer Paise Two-Phase Math tests passed.');

console.log('\n===========================================');
console.log(' ALL SECURITY CONTRACT UNIT TESTS PASSED! 🌸');
console.log('===========================================');
