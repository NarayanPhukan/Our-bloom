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

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">2. Couple's Savings Vault &amp; Payments</h2>
      <ul className="list-disc pl-5 space-y-2">
        <li><strong>Voluntary Contributions:</strong> Deposits into the Couple's Savings Vault are made voluntarily by paired partners to track mutual relationship goals.</li>
        <li><strong>Verified UTR Required:</strong> Payments are processed via authorized payment gateway partners (such as PayU and UPI). A verified 12-digit UPI UTR / Reference number is strictly required to credit any transaction.</li>
        <li><strong>Withdrawals:</strong> Either partner may initiate a withdrawal to transfer their saved funds back to their registered joint or individual bank account.</li>
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
