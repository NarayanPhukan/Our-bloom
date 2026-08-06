import { useState, useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMapEvents } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { GeoSearchControl, OpenStreetMapProvider } from 'leaflet-geosearch';
import 'leaflet-geosearch/dist/geosearch.css';
import { useMap } from 'react-leaflet';
import { getDreamLocations, createDreamLocation } from '../api';
import Toast from '../components/Toast';

// Fix for default marker icon in leaflet with webpack
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

// Custom cute marker icon
const heartIcon = new L.DivIcon({
  className: 'custom-heart-icon',
  html: '<span class="material-symbols-outlined text-error text-3xl drop-shadow-md">favorite</span>',
  iconSize: [30, 30],
  iconAnchor: [15, 30],
  popupAnchor: [0, -30],
});

function MapClickHandler({ setDraftLocation, setModalOpen }) {
  useMapEvents({
    click(e) {
      setDraftLocation({ lat: e.latlng.lat, lng: e.latlng.lng });
      setModalOpen(true);
    },
  });
  return null;
}

function SearchField() {
  const map = useMap();

  useEffect(() => {
    const provider = new OpenStreetMapProvider();
    
    const searchControl = new GeoSearchControl({
      provider: provider,
      style: 'button',
      position: 'topright',
      showMarker: false,
      showPopup: false,
      autoClose: true,
      retainZoomLevel: false,
      animateZoom: true,
      keepResult: true,
      searchLabel: 'Search for a dream location...'
    });

    map.addControl(searchControl);
    return () => map.removeControl(searchControl);
  }, [map]);

  return null;
}

export default function MapPage() {
  const [locations, setLocations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [draftLocation, setDraftLocation] = useState(null);
  const [formData, setFormData] = useState({ title: '', description: '' });
  const [toast, setToast] = useState(null);

  useEffect(() => {
    fetchLocations();
  }, []);

  const fetchLocations = async () => {
    try {
      const { data } = await getDreamLocations();
      setLocations(data);
    } catch (err) {
      setToast({ message: 'Failed to load locations', type: 'error' });
    } finally {
      setLoading(false);
    }
  };

  const handleAddLocation = async (e) => {
    e.preventDefault();
    if (!formData.title || !formData.description || !draftLocation) return;
    
    try {
      await createDreamLocation({
        title: formData.title,
        description: formData.description,
        lat: draftLocation.lat,
        lng: draftLocation.lng,
      });
      setToast({ message: 'Dream location added!', type: 'success' });
      setModalOpen(false);
      setFormData({ title: '', description: '' });
      setDraftLocation(null);
      fetchLocations();
    } catch (err) {
      setToast({ message: 'Failed to add location', type: 'error' });
    }
  };

  return (
    <>
      <section className="relative h-screen w-full pt-20">
        <div className="absolute inset-x-0 top-24 z-10 text-center pointer-events-none">
          <h1 className="font-display-lg text-4xl md:text-5xl text-on-surface drop-shadow-md bg-white/40 backdrop-blur-md inline-block px-8 py-4 rounded-full border border-white/50">
            Our Dream Map ✨
          </h1>
          <p className="mt-2 text-on-surface-variant font-medium drop-shadow bg-white/40 backdrop-blur-md inline-block px-6 py-2 rounded-full border border-white/50">
            Click anywhere to pin a new dream
          </p>
        </div>
        
        {loading ? (
          <div className="flex h-full items-center justify-center">
            <span className="material-symbols-outlined animate-spin text-primary text-4xl">autorenew</span>
          </div>
        ) : (
          <MapContainer 
            center={[15.0, 100.0]} // Center around Southeast Asia (Thailand/Bali area)
            zoom={4} 
            className="h-full w-full z-0"
            style={{ backgroundColor: '#fbf9f8' }}
          >
            {/* Using a beautiful watercolor/stamen style map if available, fallback to default */}
            <TileLayer
              url="https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
            />
            <SearchField />
            <MapClickHandler setDraftLocation={setDraftLocation} setModalOpen={setModalOpen} />
            
            {locations.map((loc) => (
              <Marker key={loc._id} position={[loc.lat, loc.lng]} icon={heartIcon}>
                <Popup className="custom-popup">
                  <div className="text-center p-2">
                    <h3 className="font-headline-md text-lg text-primary">{loc.title}</h3>
                    <p className="font-body-sm text-on-surface-variant mt-2">{loc.description}</p>
                  </div>
                </Popup>
              </Marker>
            ))}
          </MapContainer>
        )}
      </section>

      {/* Add Location Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center px-4 bg-surface/40 backdrop-blur-sm animate-fade-in">
          <div className="bg-surface-container-lowest p-8 rounded-[32px] shadow-2xl w-full max-w-md relative border border-primary/10">
            <button
              onClick={() => setModalOpen(false)}
              className="absolute top-6 right-6 text-on-surface-variant hover:text-on-surface transition-colors"
            >
              <span className="material-symbols-outlined">close</span>
            </button>
            <h2 className="font-headline-md text-2xl text-on-surface mb-6">Pin a Dream</h2>
            <form onSubmit={handleAddLocation} className="space-y-4">
              <div>
                <label className="block font-label-md text-on-surface mb-2">Location Name</label>
                <input
                  type="text"
                  required
                  value={formData.title}
                  onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                  placeholder="e.g., Thailand"
                  className="w-full bg-surface border border-outline-variant rounded-2xl px-4 py-3 focus:outline-none focus:border-primary transition-colors"
                />
              </div>
              <div>
                <label className="block font-label-md text-on-surface mb-2">The Dream</label>
                <textarea
                  required
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  placeholder="e.g., Getting our matching tattoos!"
                  className="w-full bg-surface border border-outline-variant rounded-2xl px-4 py-3 focus:outline-none focus:border-primary transition-colors resize-none h-24 custom-scrollbar"
                />
              </div>
              <button
                type="submit"
                className="w-full bg-primary text-on-primary font-label-lg py-4 rounded-full shadow-glow-primary hover:bg-secondary transition-colors"
              >
                Drop Pin 📍
              </button>
            </form>
          </div>
        </div>
      )}

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
