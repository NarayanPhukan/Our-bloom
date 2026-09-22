import LegalContainer from './LegalContainer';

export default function ShippingPolicy() {
  return (
    <LegalContainer title="Shipping and Delivery Policy" lastUpdated="Last Updated: September 22, 2026">
      <p>
        This Shipping and Digital Delivery Policy outlines fulfillment terms for <strong>Our Bloom</strong> (<a href="https://our-bloom-gamma.vercel.app" className="text-blue-600 font-medium hover:underline">our-bloom-gamma.vercel.app</a>).
      </p>

      <h2 className="text-lg font-bold text-[#0F2744] pt-2">1. 100% Digital Delivery Model</h2>
      <p className="text-sm text-slate-700">
        Our Bloom provides digital relationship software applications, timeline features, connection tools, and mutual savings ledger technology. We do not manufacture or ship physical tangible goods. Consequently, <strong>no physical shipping or courier dispatch is involved</strong> in any transaction on our platform.
      </p>

      <h2 className="text-lg font-bold text-[#0F2744] pt-2">2. Instant Fulfillment Timeline</h2>
      <ul className="list-disc pl-5 space-y-2 text-sm text-slate-700">
        <li><strong>Instant Digital Fulfillment:</strong> All mobile application features, couple synchronizations, and savings vault deposits are provisioned and credited <strong>instantaneously</strong> upon payment gateway confirmation (PayU / UPI).</li>
        <li><strong>Receipts &amp; Audit Logs:</strong> Instant confirmation receipts, cryptographic signature verification, and updated digital ledger balances are immediately visible within the user dashboard.</li>
        <li><strong>Zero Shipping Fees:</strong> There are zero shipping, delivery, or handling charges applied to any transaction on Our Bloom.</li>
      </ul>

      <div className="bg-blue-50/60 border-l-4 border-[#2563EB] p-4 rounded-r-xl space-y-1 text-xs">
        <p className="font-bold text-[#0F2744]">Delivery &amp; Fulfillment Support:</p>
        <p>Entity: Our Bloom</p>
        <p>Email: <a href="mailto:support@ourbloom.app" className="text-blue-600 font-semibold hover:underline">support@ourbloom.app</a></p>
      </div>
    </LegalContainer>
  );
}
