import LegalContainer from './LegalContainer';

export default function PrivacyPolicy() {
  return (
    <LegalContainer title="Privacy Policy" lastUpdated="Last Updated: September 12, 2026">
      <p>
        Welcome to <strong>Our Bloom</strong> ("we", "our", or "us"). We are deeply committed to protecting the intimacy and privacy of couples using our mobile application and web services (<a href="https://our-bloom-gamma.vercel.app" className="text-primary font-medium">our-bloom-gamma.vercel.app</a>). This Privacy Policy explains how information is collected, safeguarded, and used.
      </p>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">1. Information We Collect</h2>
      <ul className="list-disc pl-5 space-y-2">
        <li><strong>Account Information:</strong> Names, email addresses, relationship anniversary dates, and paired couple identifiers.</li>
        <li><strong>Couple Content:</strong> Private love notes, timeline memories, milestone journals, photos, and personalized game prompts.</li>
        <li><strong>Payment &amp; Savings Data:</strong> When utilizing the Couple's Savings Vault, we record transaction amounts, timestamps, and verified 12-digit UPI UTR reference numbers. We <em>never</em> store your bank passwords, card numbers, or UPI PINs.</li>
        <li><strong>Device Tokens:</strong> Push notification tokens (Firebase Cloud Messaging) used strictly to deliver partner notes, messages, and call alerts.</li>
      </ul>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">2. How We Use Information</h2>
      <p>
        Your data is used solely to provide a private, connected, and reliable space for you and your partner. We synchronize milestones, facilitate peer communication, and maintain the mutual savings ledger. We do <strong>NOT</strong> sell, trade, or monetize your personal memories or relationship data to advertisers or third-party data brokers.
      </p>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">3. Data Security &amp; Encryption</h2>
      <p>
        All communications are encrypted in transit using industry-standard TLS/SSL (HTTPS). Stored database records are isolated with strict multi-tenant authorization controls, ensuring that only paired partners have access to their shared vault.
      </p>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">4. Grievance Officer &amp; Inquiries</h2>
      <div className="bg-primary/5 border-l-4 border-primary p-4 rounded-r-xl space-y-1">
        <p className="font-bold text-on-surface">Grievance &amp; Privacy Officer: Narayan Phukan</p>
        <p>Entity: Our Bloom, India</p>
        <p>Email: <a href="mailto:support@ourbloom.app" className="text-primary font-semibold">support@ourbloom.app</a> / <a href="mailto:narayanphukan@gmail.com" className="text-primary">narayanphukan@gmail.com</a></p>
        <p className="text-xs text-on-surface-variant">Response turnaround: within 24–48 business hours.</p>
      </div>
    </LegalContainer>
  );
}
