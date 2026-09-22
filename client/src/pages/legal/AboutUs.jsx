import LegalContainer from './LegalContainer';

export default function AboutUs() {
  return (
    <LegalContainer title="About Our Bloom" lastUpdated="A private, distraction-free relationship platform for couples.">
      <p className="text-[#0F2744] font-semibold text-lg">
        Empowering couples with secure, distraction-free digital connection and shared financial harmony.
      </p>

      <p>
        <strong>Our Bloom</strong> (<a href="https://our-bloom-gamma.vercel.app" className="text-blue-600 font-medium hover:underline">our-bloom-gamma.vercel.app</a>) provides paired couples with an exclusive, ad-free sanctuary dedicated solely to their relationship. We combine deep romantic memory preservation with authoritative fintech goal tracking to help couples cultivate a thriving shared future.
      </p>

      <div className="bg-slate-50 border border-slate-200 rounded-2xl p-6 space-y-4 my-6">
        <h3 className="font-bold text-[#0F2744] text-base">Key Pillars of Our Platform</h3>
        <ul className="list-disc pl-5 space-y-2.5 text-sm text-slate-700">
          <li>
            <strong>The Couple's Savings Vault:</strong> A transparent mutual goal-tracking ledger. Couples can collaboratively fund anniversaries, honeymoons, and mutual dreams using instant PayU UPI deposits with complete ledger transparency and a clear 2% platform fee.
          </li>
          <li>
            <strong>Sacred Journey Timeline:</strong> An immutable, chronological diary of your relationship's most treasured moments, from first glances to lifetime anniversaries.
          </li>
          <li>
            <strong>Real-Time Connection Arcade:</strong> Interactive games (Truth or Dare, Couples Tic-Tac-Toe, and custom prompts) designed to spark heartfelt laughter and deeper understanding.
          </li>
          <li>
            <strong>Enterprise-Grade Security:</strong> Strict multi-tenant isolation, 256-bit SSL encryption, zero advertising trackers, and absolute zero selling or monetization of couple data.
          </li>
        </ul>
      </div>

      <div className="border-t border-slate-200 pt-6 space-y-2 text-sm text-slate-600">
        <p><strong>Entity Name:</strong> Our Bloom</p>
        <p><strong>Founder &amp; Lead Architect:</strong> Narayan Phukan</p>
        <p><strong>Headquarters:</strong> Assam, India</p>
      </div>
    </LegalContainer>
  );
}
