import { useState, useEffect, useRef } from 'react';
import { getMemories, createMemory, deleteMemory } from '../api';
import Toast from '../components/Toast';

const FALLBACK_MEMORIES = [
  {
    _id: 'f1',
    title: 'The First Sunday',
    dateStr: 'OCTOBER 12, 2023',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuC8fqgXY0ryTCBKFJbwefPLNGXF0pgDSuqGmu7bJbPZJg_ivE-itfIsOiDKQ_F5v_fr5rICz1w-rcwc-Q8n6uGN9RQ5y5r2SPPGHaES8LWVXjTMUJR7zp0PfFRhAQYkoYQDUgkkv99rr0coLvQdaZW5QVn66iYvIO4DsivIQaEJkgWCiut-7u1znfg-Sa9PrHQJItFDw5t-Db2IPGYxb9q4nYKZxuZe-EVn7yPLo6R-RMbA7NATKYDUqMC76EaStwKyR2jHKrYN5la6',
    icon: 'spa',
    rotation: 0,
    aspect: 'aspect-[4/5]',
    isDummy: true
  },
  {
    _id: 'f2',
    title: 'Garden Whispers',
    dateStr: 'OCTOBER 18, 2023',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuAXBnZ7O3VWtHKqGO2yPlkcxH8EoBfqzxB2NI9U8Aw-_9RyoXfH7UkZdyrH7DZCHNvHYNm12y3W4X4nKTeeNYJwpCSCocstYvgzTIkibcxE-AF4Mppz7GXCTQyX4oxUnAu58dNt865uFOsBqajoFkWFcWLx13KqBHQEwgCwBrN6I80SVCKTL9YqJPr0UqZia3FsDbpPEJb4ANeCeDNabSRL4hcxGymUVwSijVaMeyFblONvirfZ8IsquKlpcBn9oCihuyKf3JBFqICt',
    icon: 'favorite',
    rotation: -2,
    aspect: 'aspect-square',
    isDummy: true
  },
  {
    _id: 'f3',
    title: 'Quiet Mornings',
    dateStr: 'OCTOBER 25, 2023',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCbsys_63lAVNj1IZpezja01hnK7l36u3l3VurKeTPDIdnjQH7fpOIGE9jwOEh2OVOtwPJh5piwDW8WPbEA3EEOX9T18xDDJzxVgyQKxCWX6c9YQr4cGCEjAg7NIxPnBHrRuPOas-XWDcDRenuN9bD1s2MTYuXT7FLTIW3X2MJ3k1giuecZwK5vlmAN8IyegBZ1926QjTq2j7v--5O-N5Z3Lm0kSsGt3X0rodmLVpAtw__sP2Q4P_rwP-D5WMb2_yV4apyHE2IR97CM',
    icon: 'filter_vintage',
    rotation: 1,
    aspect: 'aspect-[3/4]',
    isDummy: true
  },
  {
    _id: 'f4',
    title: 'The Great Escape',
    dateStr: 'NOVEMBER 02, 2023',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBy_LU4O8nJXjqVt-zp7lFXH0rP7FLeRibTcSyWRO4ksRoNfGPo5ycqK_dAI3lH-CzlwN7ZnuKwkZkqVj3x47K7zKTjNeuERbIlmvOUFV3C_NHesa0DKsgC1RvJoZnOeJzxR1cmsqT6DezgvH_6UIPdbQc4-5slaOcXRVu3cVEE1IrMO6s_SdlIknMFSpW9VjzSkfYVkXNl_WqTwsopBPDxciE2TpR0Eh1e9Fg6iTb8Lq8rcBnjxpHfsUwbOK18hSe01eSISXlVbr7R',
    icon: 'forest',
    rotation: 0,
    aspect: 'aspect-square',
    isDummy: true
  },
  {
    _id: 'f5',
    title: 'Shared Laughter',
    dateStr: 'NOVEMBER 08, 2023',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuB_DABV-DsqO5tjDW6hlSn_z1lWd4twKimMnTq5FnzvsZYk5366Ctj7Xkf_T4_etT8lM0msQ1G1ZRqT3QKpXPuivUz9Tztf9bwZI1k3oKegjfCcj0Ej0_m3ikjy0IuzkbLgFxRP_x4XDTdqDfQCRvs0qHaCMoCCUP1kqG1kydCRt3uKFHNnkmkNL_zW1TeOG3PTIaU6EXYtyR1Trik7LMuUQlTgT9cOkMcBeLU0JV25Y7QaVTdthWtmon-Rp-H4YK6h89RNhCziJd_7',
    icon: 'sentiment_very_satisfied',
    rotation: -1,
    aspect: 'aspect-[4/5]',
    isDummy: true
  },
  {
    _id: 'f6',
    title: 'Petals of Time',
    dateStr: 'NOVEMBER 11, 2023',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCm3l0cuZ9G8DlJW6qT8MqcOXeK4wkrtBawtl2ZC4ZmFHTDgZL1gUay4_VgX-EGbWg0g7vabhmWaKoiQNO31leBXmvxKV08BuKeau5lhLschQitS6OfsDs2fMr5_N-StTYr4O9hiHz3yI2sKsX7b7De0Q4MpvECAK2ZGZFPLvOo-nOmgslMmmhoHCyNbumoorUIkhZOYZe7tuvdfqQTtsFJupADvdHyDa6exWBju5RpdWDahV67qMhuX0fsTLuj_r3L_3e9-PgANli_',
    icon: 'favorite',
    rotation: 2,
    aspect: 'aspect-video',
    isDummy: true
  }
];

export default function MemoriesPage() {
  const [memories, setMemories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [formData, setFormData] = useState({ title: '', dateStr: '' });
  const [imageFile, setImageFile] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const fileInputRef = useRef(null);

  useEffect(() => {
    fetchMemories();
  }, []);

  const fetchMemories = async () => {
    try {
      const { data } = await getMemories();
      setMemories(data);
    } catch (err) {
      console.error('Failed to fetch memories', err);
    } finally {
      setLoading(false);
    }
  };

  const handleAddMemory = async (e) => {
    e.preventDefault();
    if (!imageFile) {
      setToast({ message: 'Please select an image', type: 'error' });
      return;
    }

    setSubmitting(true);
    try {
      const data = new FormData();
      data.append('title', formData.title);
      data.append('dateStr', formData.dateStr);
      data.append('image', imageFile);

      const res = await createMemory(data);
      setMemories([res.data, ...memories]);
      setModalOpen(false);
      setFormData({ title: '', dateStr: '' });
      setImageFile(null);
      setToast({ message: 'Memory bloomed successfully ♡', type: 'success' });
    } catch (err) {
      console.error(err);
      setToast({ message: 'Failed to save memory', type: 'error' });
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id) => {
    try {
      await deleteMemory(id);
      setMemories(memories.filter((m) => m._id !== id));
      setToast({ message: 'Memory removed', type: 'success' });
    } catch (err) {
      console.error(err);
      setToast({ message: 'Failed to remove memory', type: 'error' });
    }
  };

  const displayMemories = memories.length > 0 ? memories : FALLBACK_MEMORIES;

  return (
    <>
      {/* Hero Section */}
      <section className="max-w-container-max mx-auto px-5 md:px-margin-desktop text-center mb-20 relative">
        <div className="absolute -top-10 -left-10 opacity-20 animate-float pointer-events-none hidden md:block">
          <span className="material-symbols-outlined text-[120px] text-primary">
            local_florist
          </span>
        </div>
        <h1 className="font-display-lg-mobile md:font-display-lg text-display-lg-mobile md:text-display-lg text-primary mb-4">
          Capturing Our Bloom
        </h1>
        <p className="font-body-lg text-body-lg text-on-surface-variant max-w-2xl mx-auto italic">
          "Like lilies in a quiet pond, our moments surface one by one, delicate
          and pure."
        </p>
      </section>

      {/* Loading state */}
      {loading && (
        <div className="flex justify-center py-20">
          <span className="material-symbols-outlined text-[48px] text-primary animate-spin">
            filter_vintage
          </span>
        </div>
      )}

      {/* Memories Grid (4 per row) */}
      {!loading && (
        <section className="max-w-[1200px] mx-auto px-5 md:px-margin-desktop">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-8">
            {displayMemories.map((memory) => {
              const rotation = memory.rotation || 0;
              const isLocal = memory.imageUrl.startsWith('/uploads');
              const baseUrl = import.meta.env.VITE_API_URL ? import.meta.env.VITE_API_URL.replace('/api', '') : 'http://localhost:5000';
              const imgSrc = isLocal ? `${baseUrl}${memory.imageUrl}` : memory.imageUrl;
              const aspect = memory.aspect || 'aspect-[4/5]';

              return (
                <div key={memory._id} className="break-inside-avoid">
                  <div
                    className={`polaroid-frame bg-white p-4 rounded-sm relative group hover:scale-[1.03] transition-transform duration-400 ease-out`}
                    style={{ transform: `rotate(${rotation}deg)` }}
                    onMouseEnter={(e) => { e.currentTarget.style.transform = `scale(1.03) rotate(0deg)`; }}
                    onMouseLeave={(e) => { e.currentTarget.style.transform = `rotate(${rotation}deg)`; }}
                  >
                    {!memory.isDummy && (
                      <button
                        onClick={() => handleDelete(memory._id)}
                        className="absolute -top-4 -right-4 bg-error text-white w-8 h-8 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity z-20 shadow-lg"
                      >
                        <span className="material-symbols-outlined text-sm">close</span>
                      </button>
                    )}

                    <div className={`absolute -top-6 -left-6 text-primary/30 group-hover:text-primary/50 transition-colors duration-500 z-10`}>
                      <span className="material-symbols-outlined text-6xl">{memory.icon}</span>
                    </div>

                    <div className={`${aspect} bg-surface-container-low mb-4 overflow-hidden rounded-[4px]`}>
                      <img
                        className="w-full h-full object-cover"
                        src={imgSrc}
                        alt={memory.title}
                        loading="lazy"
                      />
                    </div>
                    <div className="text-center">
                      <span className="font-headline-md text-xl text-primary">{memory.title}</span>
                      <p className="font-label-sm text-label-sm text-on-tertiary-container mt-1 uppercase">
                        {memory.dateStr}
                      </p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      )}

      {/* Quote Divider */}
      <section className="mt-32 mb-10 max-w-container-max mx-auto px-5 text-center">
        <div className="inline-block p-12 glass-panel rounded-[24px] max-w-3xl border border-primary/20">
          <span className="material-symbols-outlined text-primary text-4xl mb-4">
            format_quote
          </span>
          <p className="font-headline-md text-2xl text-on-primary-container italic leading-relaxed">
            "In the garden of my heart, every memory of you is a bloom that never
            fades."
          </p>
          <div className="mt-6 flex justify-center items-center space-x-2">
            <div className="h-[1px] w-12 bg-primary-fixed-dim"></div>
            <span className="font-label-sm text-primary tracking-widest">
              ONE MONTH TOGETHER
            </span>
            <div className="h-[1px] w-12 bg-primary-fixed-dim"></div>
          </div>
        </div>
      </section>

      {/* Floating Action Button */}
      <button
        onClick={() => setModalOpen(true)}
        className="fixed bottom-8 right-8 bg-primary text-on-primary w-16 h-16 rounded-full shadow-2xl flex items-center justify-center hover:bg-secondary hover:scale-110 transition-all z-40 group"
      >
        <span className="material-symbols-outlined text-3xl group-hover:rotate-90 transition-transform">
          add_photo_alternate
        </span>
      </button>

      {/* Add Memory Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center px-4 bg-surface/40 backdrop-blur-sm animate-fade-in">
          <div className="bg-surface-container-lowest p-8 rounded-[32px] shadow-2xl w-full max-w-md relative border border-primary/10">
            <button
              onClick={() => setModalOpen(false)}
              className="absolute top-6 right-6 text-on-surface-variant hover:text-error transition-colors"
            >
              <span className="material-symbols-outlined">close</span>
            </button>
            <h2 className="font-headline-md text-2xl text-primary mb-6">Plant a Memory</h2>
            
            <form onSubmit={handleAddMemory} className="space-y-6">
              
              {/* Image Upload Area */}
              <div 
                className="w-full aspect-[4/3] bg-surface-container border-2 border-dashed border-primary/30 rounded-2xl flex flex-col items-center justify-center cursor-pointer hover:bg-surface-variant transition-colors overflow-hidden relative"
                onClick={() => fileInputRef.current?.click()}
              >
                {imageFile ? (
                  <img 
                    src={URL.createObjectURL(imageFile)} 
                    alt="Preview" 
                    className="w-full h-full object-cover" 
                  />
                ) : (
                  <>
                    <span className="material-symbols-outlined text-primary text-5xl mb-2">add_photo_alternate</span>
                    <span className="font-label-sm text-primary uppercase">Select a photo</span>
                  </>
                )}
              </div>
              <input 
                type="file" 
                accept="image/*" 
                className="hidden" 
                ref={fileInputRef} 
                onChange={(e) => {
                  if (e.target.files && e.target.files[0]) setImageFile(e.target.files[0]);
                }} 
              />

              {/* Title */}
              <div>
                <label className="block font-label-sm text-primary uppercase tracking-wider mb-2">Memory Title</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. The First Sunday"
                  className="w-full bg-surface border border-outline-variant rounded-xl px-4 py-3 font-body-md focus:outline-none focus:ring-2 focus:ring-primary/50"
                  value={formData.title}
                  onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                />
              </div>

              {/* Date String */}
              <div>
                <label className="block font-label-sm text-primary uppercase tracking-wider mb-2">Date Label</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. OCTOBER 12, 2023"
                  className="w-full bg-surface border border-outline-variant rounded-xl px-4 py-3 font-body-md focus:outline-none focus:ring-2 focus:ring-primary/50"
                  value={formData.dateStr}
                  onChange={(e) => setFormData({ ...formData, dateStr: e.target.value })}
                />
              </div>

              <button
                type="submit"
                disabled={submitting}
                className="w-full bg-primary text-on-primary py-4 rounded-full font-label-sm uppercase tracking-widest hover:bg-secondary transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {submitting ? 'Uploading...' : 'Add to Gallery'}
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Toast */}
      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}
    </>
  );
}
