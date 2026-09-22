import LegalContainer from './LegalContainer';

export default function PrivacyPolicy() {
  return (
    <LegalContainer title="Privacy Policy" lastUpdated="Last Updated: September 22, 2026">
      <p>
        Welcome to <strong>Our Bloom</strong> ("we", "our", or "us"). We are deeply committed to safeguarding the intimate confidentiality and data privacy of couples using our mobile applications and web services (<a href="https://our-bloom-gamma.vercel.app" className="text-blue-600 font-medium hover:underline">our-bloom-gamma.vercel.app</a>).
      </p>

      <h2 className="text-lg font-bold text-[#0F2744] pt-2">1. Information We Collect</h2>
      <ul className="list-disc pl-5 space-y-2 text-sm text-slate-700">
        <li><strong>Account Information:</strong> Names, verified email addresses, relationship anniversary timestamps, and paired couple identifiers.</li>
        <li><strong>Couple Content:</strong> Private love notes, timeline memories, milestone journals, photos, and connection arcade records.</li>
        <li><strong>Payment &amp; Savings Data:</strong> When utilizing the Couple's Savings Vault, we record transaction amounts, timestamps, and gateway transaction IDs / UPI reference numbers. We <em>never</em> store your bank passwords, debit/credit card numbers, or UPI PINs.</li>
        <li><strong>Device Tokens:</strong> Push notification tokens (Firebase Cloud Messaging) utilized strictly to deliver partner notes, reminders, and call alerts.</li>
      </ul>

      <h2 className="text-lg font-bold text-[#0F2744] pt-2">2. How We Use Information</h2>
      <p className="text-sm text-slate-700 leading-relaxed">
        Your data is used solely to provide a private, synchronized space for you and your partner. We synchronize milestones, facilitate peer communication, and maintain the mutual savings ledger. We do <strong>NOT</strong> sell, rent, trade, or monetize your personal memories, photos, or relationship data to advertisers, analytics brokers, or third-party syndicates.
      </p>

      <h2 className="text-lg font-bold text-[#0F2744] pt-2">3. Data Security &amp; Encryption</h2>
      <p className="text-sm text-slate-700 leading-relaxed">
        All communications are encrypted in transit using industry-standard TLS 1.3 / SSL (HTTPS). Stored database records are fortified with strict multi-tenant authorization controls, ensuring that only verified paired partners possess decryption and reading access to their shared vault and journey.
      </p>

      <h2 className="text-lg font-bold text-[#0F2744] pt-2">4. Grievance Officer &amp; Privacy Redressal</h2>
      <div className="bg-blue-50/60 border-l-4 border-[#2563EB] p-4 rounded-r-xl space-y-1 text-xs">
        <p className="font-bold text-[#0F2744]">Grievance &amp; Privacy Officer: Narayan Phukan</p>
        <p>Entity: Our Bloom</p>
        <p>Email: <a href="mailto:support@ourbloom.app" className="text-blue-600 font-semibold hover:underline">support@ourbloom.app</a> / <a href="mailto:narayanphukan@gmail.com" className="text-blue-600 hover:underline">narayanphukan@gmail.com</a></p>
        <p className="text-slate-500">Official Turnaround: Inquiries acknowledged within 24 hours.</p>
      </div>
    </LegalContainer>
  );
}
