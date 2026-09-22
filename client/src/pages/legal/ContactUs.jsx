import LegalContainer from './LegalContainer';

export default function ContactUs() {
  return (
    <LegalContainer title="Contact Us & Grievance Redressal" lastUpdated="Official Support Portal | Operating Hours: Mon – Sat, 9:00 AM – 7:00 PM IST">
      <p>
        Whether you have questions regarding couple account synchronization, savings vault deposits, billing reconciliation, or technical support, our dedicated team is here to assist you.
      </p>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 my-6">
        <div className="bg-slate-50 border border-slate-200 rounded-2xl p-6 space-y-2">
          <h3 className="font-bold text-[#0F2744] flex items-center gap-1.5 text-sm">
            <span className="material-symbols-outlined text-blue-600 text-[18px]">mail</span>
            Customer Support Email
          </h3>
          <p className="text-xs text-slate-500">For account help, billing, and general support inquiries:</p>
          <p className="font-bold text-sm text-[#2563EB]">
            <a href="mailto:support@ourbloom.app" className="hover:underline">support@ourbloom.app</a>
          </p>
          <p className="text-xs text-slate-400">
            Escalation: <a href="mailto:narayanphukan@gmail.com" className="hover:underline">narayanphukan@gmail.com</a>
          </p>
        </div>

        <div className="bg-slate-50 border border-slate-200 rounded-2xl p-6 space-y-2">
          <h3 className="font-bold text-[#0F2744] flex items-center gap-1.5 text-sm">
            <span className="material-symbols-outlined text-emerald-600 text-[18px]">chat</span>
            WhatsApp Direct Assistance
          </h3>
          <p className="text-xs text-slate-500">Quick payment verification and transaction assistance:</p>
          <p className="text-sm font-bold text-emerald-700">
            <a href="https://wa.me/917086884639" target="_blank" rel="noreferrer" className="hover:underline flex items-center gap-1">
              <span>+91 7086884639</span>
              <span className="text-[11px] bg-emerald-100 text-emerald-800 px-2 py-0.5 rounded-full font-normal">Active</span>
            </a>
          </p>
          <p className="text-xs text-slate-400">Standard response: Under 2 hours during operating window.</p>
        </div>

        <div className="bg-slate-50 border border-slate-200 rounded-2xl p-6 space-y-2">
          <h3 className="font-bold text-[#0F2744] flex items-center gap-1.5 text-sm">
            <span className="material-symbols-outlined text-indigo-600 text-[18px]">domain</span>
            Registered Entity Details
          </h3>
          <p className="text-xs text-slate-600"><strong>Entity:</strong> Our Bloom</p>
          <p className="text-xs text-slate-600"><strong>Founder:</strong> Narayan Phukan</p>
          <p className="text-xs text-slate-600"><strong>Classification:</strong> Relationship Software &amp; Digital Services</p>
          <p className="text-xs text-slate-500">Country of Origin: India</p>
        </div>

        <div className="bg-slate-50 border border-slate-200 rounded-2xl p-6 space-y-2">
          <h3 className="font-bold text-[#0F2744] flex items-center gap-1.5 text-sm">
            <span className="material-symbols-outlined text-amber-600 text-[18px]">schedule</span>
            Support Availability
          </h3>
          <p className="text-xs text-slate-600"><strong>Days:</strong> Monday – Saturday</p>
          <p className="text-xs text-slate-600"><strong>Hours:</strong> 9:00 AM – 7:00 PM IST</p>
          <p className="text-xs text-slate-500">Email turnaround: 24 to 48 business hours.</p>
        </div>
      </div>

      <div className="border-t border-slate-200 pt-6 space-y-3">
        <h2 className="text-lg font-bold text-[#0F2744]">Statutory Grievance Redressal</h2>
        <p className="text-xs text-slate-600 leading-relaxed">
          In compliance with the Information Technology (Intermediary Guidelines and Digital Media Ethics Code) Rules, 2021, the contact details for user grievances are designated below:
        </p>

        <div className="bg-blue-50/60 border-l-4 border-[#2563EB] p-4 rounded-r-xl space-y-1.5 text-xs text-slate-700">
          <p><strong>Designated Grievance Officer:</strong> Narayan Phukan</p>
          <p><strong>Entity Name:</strong> Our Bloom</p>
          <p><strong>Grievance Email:</strong> <a href="mailto:support@ourbloom.app" className="text-blue-700 font-bold hover:underline">support@ourbloom.app</a></p>
          <p><strong>Resolution SLA:</strong> Acknowledgment within 24 hours; resolution within 15 days as mandated by IT Rules.</p>
        </div>
      </div>
    </LegalContainer>
  );
}
