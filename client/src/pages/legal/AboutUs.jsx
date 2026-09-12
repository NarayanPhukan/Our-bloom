import LegalContainer from './LegalContainer';

export default function AboutUs() {
  return (
    <LegalContainer title="About Our Bloom" lastUpdated="A private, ad-free sanctuary crafted with love.">
      <p className="font-serif italic text-primary text-lg">
        With love, forever &amp; always — Connecting couples across every mile.
      </p>

      <p>
        <strong>Our Bloom</strong> (<a href="https://our-bloom-gamma.vercel.app" className="text-primary font-medium">our-bloom-gamma.vercel.app</a>) was created to give couples a private, distraction-free sanctuary to celebrate their connection.
      </p>

      <p>
        Unlike noisy social media feeds or ephemeral chat apps, Our Bloom brings couples together around their shared love story: preserving first dates, anniversaries, private love notes, connection arcade games, and joint savings towards shared life dreams.
      </p>

      <div className="bg-primary/5 border border-primary/10 rounded-2xl p-6 space-y-3 my-6">
        <h3 className="font-serif font-bold text-primary text-lg">Core Highlights</h3>
        <ul className="list-disc pl-5 space-y-2 text-sm">
          <li><strong>Romantic Journey Timeline:</strong> Chronicle milestones and memories with photos and journal entries.</li>
          <li><strong>Couple's Savings Vault:</strong> Plan vacations, anniversaries, and future goals with transparent, mutual savings.</li>
          <li><strong>Interactive Couple Games:</strong> Play online Truth or Dare, Love Tic-Tac-Toe, and Would You Rather in real-time.</li>
          <li><strong>Private &amp; Secure:</strong> End-to-end multi-tenant isolation. Zero ads, zero selling of personal data.</li>
        </ul>
      </div>

      <p>
        Created by Narayan Phukan and dedicated to love in all its forms.
      </p>
    </LegalContainer>
  );
}
