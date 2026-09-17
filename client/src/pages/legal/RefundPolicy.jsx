import LegalContainer from './LegalContainer';

export default function RefundPolicy() {
  return (
    <LegalContainer title="Refund and Cancellation Policy" lastUpdated="Last Updated: September 12, 2026">
      <p>
        At <strong>Our Bloom</strong> (<a href="https://our-bloom-gamma.vercel.app" className="text-primary font-medium">our-bloom-gamma.vercel.app</a>), we maintain full transparency and fairness regarding all digital features and savings vault deposits.
      </p>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">1. Couple's Savings Vault Funds</h2>
      <ul className="list-disc pl-5 space-y-2">
        <li><strong>Full Ownership:</strong> Vault savings remain the mutual property of the verified couple. Funds can be withdrawn back to your bank account at any time via the Withdrawal screen in the app.</li>
        <li><strong>Withdrawal Settlement:</strong> Verified withdrawal requests are processed and settled directly to your designated bank account within <strong>24 to 48 business hours</strong>.</li>
      </ul>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">2. Accidental, Erroneous, or Duplicate Transactions &amp; 100% Contributor Refund</h2>
      <p>
        If you experience an accidental, duplicate, or erroneous deposit:
      </p>
      <ul className="list-disc pl-5 space-y-2">
        <li><strong>100% Gross Refund:</strong> You may request a refund within <strong>7 days</strong> of the transaction date. When approved, <strong>100% of your total payment (including the 2% platform service fee)</strong> is refunded back to the originating payment source (e.g. ₹102.00 full refund for a ₹100.00 Vault deposit).</li>
        <li><strong>2% Platform Fee Reversal:</strong> Our Bloom completely reverses its 2% platform fee on approved refunds (Total Refund of ₹102.00 = ₹100.00 Vault deduction + ₹2.00 platform fee reversal).</li>
        <li><strong>Vault Balance Safety (Option A):</strong> If vault funds have already been partially withdrawn such that available balance is less than the vault amount to be reversed (₹100.00), the transaction is held for administrative review and couple notification to avoid negative balances.</li>
        <li><strong>Gateway Terms:</strong> Third-party payment gateway transaction processing charges (if levied directly by PayU or banking networks) remain subject to provider settlement guidelines.</li>
        <li>Email our support team at <a href="mailto:support@ourbloom.app" className="text-primary font-semibold">support@ourbloom.app</a> with your account email, txnid/PayU reference ID, and UPI UTR number from your payment receipt.</li>
      </ul>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">3. Multi-Phase Refund Processing Timeline</h2>
      <p>
        Approved refunds are dispatched to PayU within 24 business hours. Funds are credited back to your original source payment method (bank account / UPI VPA) within <strong>5 to 7 working days</strong>, adhering to standard NPCI and banking turnaround cycles.
      </p>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">4. Digital Feature Cancellations</h2>
      <p>
        Digital subscriptions or feature passes can be cancelled at any time without penalty. If dissatisfied within 48 hours of purchase, contact support for a prompt refund.
      </p>

      <div className="bg-primary/5 border-l-4 border-primary p-4 rounded-r-xl space-y-1">
        <p className="font-bold text-on-surface">Refund &amp; Payment Support:</p>
        <p>Email: <a href="mailto:support@ourbloom.app" className="text-primary font-semibold">support@ourbloom.app</a> / <a href="mailto:narayanphukan@gmail.com" className="text-primary">narayanphukan@gmail.com</a></p>
        <p className="text-xs text-on-surface-variant">Hours: Monday – Saturday, 9:00 AM – 7:00 PM IST</p>
      </div>
    </LegalContainer>
  );
}
