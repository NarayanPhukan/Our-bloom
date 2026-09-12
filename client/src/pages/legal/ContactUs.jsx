import LegalContainer from './LegalContainer';

export default function ContactUs() {
  return (
    <LegalContainer title="Contact Us" lastUpdated="We are here to help with any support or account questions.">
      <p>
        Whether you have questions about couple pairing, vault deposits, features, or billing, our support team is happy to assist you.
      </p>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 my-6">
        <div className="bg-surface border border-primary/10 rounded-2xl p-6 space-y-2">
          <h3 className="font-serif font-bold text-primary">📧 Customer Support Email</h3>
          <p className="text-sm">For account questions, feedback, or refund inquiries:</p>
          <p className="font-semibold text-primary"><a href="mailto:support@ourbloom.app">support@ourbloom.app</a></p>
          <p className="text-xs text-on-surface-variant"><a href="mailto:narayanphukan@gmail.com">narayanphukan@gmail.com</a></p>
        </div>

        <div className="bg-surface border border-primary/10 rounded-2xl p-6 space-y-2">
          <h3 className="font-serif font-bold text-primary">⏰ Operating Hours</h3>
          <p className="text-sm">Monday – Saturday: 9:00 AM – 7:00 PM IST</p>
          <p className="text-xs text-on-surface-variant">Turnaround time: 24 to 48 hours.</p>
        </div>

        <div className="bg-surface border border-primary/10 rounded-2xl p-6 space-y-2">
          <h3 className="font-serif font-bold text-primary">🏢 Registered Entity</h3>
          <p className="text-sm"><strong>Entity Name:</strong> Our Bloom</p>
          <p className="text-sm"><strong>Founder &amp; Developer:</strong> Narayan Phukan</p>
          <p className="text-xs text-on-surface-variant">Country: India</p>
        </div>

        <div className="bg-surface border border-primary/10 rounded-2xl p-6 space-y-2">
          <h3 className="font-serif font-bold text-primary">💬 WhatsApp Support</h3>
          <p className="text-sm">Quick payment verification or technical assistance:</p>
          <p className="text-sm font-semibold text-primary"><a href="https://wa.me/917086884639" target="_blank" rel="noreferrer">Message on WhatsApp 💬</a></p>
        </div>
      </div>

      <h2 className="font-serif text-xl font-bold text-on-surface pt-4">Grievance Redressal</h2>
      <p className="text-sm">
        In accordance with Information Technology Rules, 2021, the contact details of the Grievance Officer are:
      </p>
      <div className="bg-primary/5 border-l-4 border-primary p-4 rounded-r-xl space-y-1 text-sm">
        <p><strong>Grievance Officer:</strong> Narayan Phukan</p>
        <p><strong>Email:</strong> <a href="mailto:support@ourbloom.app" className="text-primary font-semibold">support@ourbloom.app</a></p>
        <p><strong>Location:</strong> Our Bloom Technologies, India</p>
      </div>
    </LegalContainer>
  );
}
