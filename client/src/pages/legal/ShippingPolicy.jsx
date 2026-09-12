import LegalContainer from './LegalContainer';

export default function ShippingPolicy() {
  return (
    <LegalContainer title="Shipping and Delivery Policy" lastUpdated="Last Updated: September 12, 2026">
      <p>
        This Shipping and Delivery Policy outlines fulfillment terms for <strong>Our Bloom</strong> (<a href="https://our-bloom-gamma.vercel.app" className="text-primary font-medium">our-bloom-gamma.vercel.app</a>).
      </p>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">1. 100% Digital Delivery</h2>
      <p>
        Our Bloom provides digital software application services for couples. We do not manufacture or ship physical goods. Consequently, <strong>no physical shipping is involved</strong> in any transaction.
      </p>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">2. Instant Delivery Timeline</h2>
      <ul className="list-disc pl-5 space-y-2">
        <li><strong>Instant Digital Fulfillment:</strong> All mobile app services, couple features, and savings vault deposits are provisioned and credited <strong>instantaneously</strong> upon payment gateway confirmation (PayU / UPI).</li>
        <li><strong>Receipts:</strong> Confirmation receipts and updated digital ledger balances are visible immediately within your account.</li>
        <li><strong>Delivery Fees:</strong> There are zero shipping, delivery, or handling charges on any Our Bloom transaction.</li>
      </ul>

      <div className="bg-primary/5 border-l-4 border-primary p-4 rounded-r-xl space-y-1">
        <p className="font-bold text-on-surface">Delivery Assistance:</p>
        <p>Email: <a href="mailto:support@ourbloom.app" className="text-primary font-semibold">support@ourbloom.app</a></p>
      </div>
    </LegalContainer>
  );
}
