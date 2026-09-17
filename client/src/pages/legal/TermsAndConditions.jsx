import LegalContainer from './LegalContainer';

export default function TermsAndConditions() {
  return (
    <LegalContainer title="Terms and Conditions" lastUpdated="Last Updated: September 12, 2026">
      <p>
        These Terms and Conditions govern your access to and use of the <strong>Our Bloom</strong> mobile application and website (<a href="https://our-bloom-gamma.vercel.app" className="text-primary font-medium">our-bloom-gamma.vercel.app</a>). By accessing our services, you agree to be bound by these terms.
      </p>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">1. Purpose &amp; Scope</h2>
      <p>
        Our Bloom provides a dedicated digital relationship sanctuary designed exclusively for romantic couples to document milestones, exchange private love notes, play connection games, and save towards joint relationship goals.
      </p>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">2. Couple's Savings Vault &amp; Transparent 2% Platform Fee</h2>
      <ul className="list-disc pl-5 space-y-2">
        <li><strong>Non-Banking Platform Disclosure:</strong> Our Bloom is a technology software platform designed to facilitate romantic couple relationship bonding and goal tracking. Our Bloom is <strong>NOT a bank, Non-Banking Financial Company (NBFC), or deposit-taking institution</strong>. Vault balances represent digitized accounting units allocated towards mutual couple goals and do not earn interest.</li>
        <li><strong>Voluntary Contributions:</strong> Deposits into the Couple's Savings Vault are made voluntarily by paired partners to track mutual relationship goals (₹1.00 min to ₹500,000.00 max).</li>
        <li><strong>Transparent 2% Platform Fee (Fee-on-Top):</strong> Our Bloom charges an authoritative 2.00% platform service fee (200 basis points) on top of the requested deposit amount. The exact amount entered by the user is credited in full to the Vault, and the 2% fee is charged on top via PayU (e.g. ₹100.00 Vault deposit incurs a ₹2.00 fee, charging ₹102.00 in total with ₹100.00 credited to the Vault). On approved full deposit refunds, Our Bloom reverses the 2% fee, refunding ₹102.00 in full to the contributor.</li>
        <li><strong>Verified Gateway Transactions:</strong> Payments are processed via authorized gateway partners (including PayU). Vault balances are authoritatively credited only upon backend cryptographic signature verification.</li>
        <li><strong>Withdrawals:</strong> Either partner may initiate a withdrawal to transfer saved funds back to their registered joint or individual bank account subject to mutual partner approval.</li>
      </ul>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">3. User Conduct</h2>
      <p>
        Users agree not to upload malicious software, submit fake or duplicate UTR numbers, or use the service for unlawful purposes. We reserve the right to suspend accounts that violate platform integrity.
      </p>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">4. Governing Law</h2>
      <p>
        These Terms shall be governed by and construed in accordance with the laws of India.
      </p>

      <div className="bg-primary/5 border-l-4 border-primary p-4 rounded-r-xl space-y-1">
        <p className="font-bold text-on-surface">Legal &amp; Support Contact:</p>
        <p>Entity: Our Bloom</p>
        <p>Email: <a href="mailto:support@ourbloom.app" className="text-primary font-semibold">support@ourbloom.app</a></p>
      </div>
    </LegalContainer>
  );
}
