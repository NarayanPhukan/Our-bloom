import { useState, useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMapEvents, Polyline } from 'react-leaflet';
import { io } from 'socket.io-client';
import { useParams } from 'react-router-dom';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { GeoSearchControl, OpenStreetMapProvider } from 'leaflet-geosearch';
import 'leaflet-geosearch/dist/geosearch.css';
import { useMap } from 'react-leaflet';
import { getDreamLocations, createDreamLocation, updateDreamLocation } from '../api';
import { useAuth } from '../context/AuthContext';
import Toast from '../components/Toast';

// Fix for default marker icon in leaflet with webpack
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

// Custom cute marker icon based on status
const getHeartIcon = (status) => {
  let iconName = 'favorite';
  let colorClass = 'text-error';
  
  if (status === 'Planning') { iconName = 'edit_calendar'; colorClass = 'text-tertiary'; }
  else if (status === 'Booked') { iconName = 'flight_takeoff'; colorClass = 'text-primary'; }
  else if (status === 'Visited') { iconName = 'verified'; colorClass = 'text-secondary'; }
  
  return new L.DivIcon({
    className: 'custom-heart-icon',
    html: `<span class="material-symbols-outlined ${colorClass} text-3xl drop-shadow-md bg-white/80 rounded-full p-1 backdrop-blur-sm border border-white/50">${iconName}</span>`,
    iconSize: [40, 40],
    iconAnchor: [20, 40],
    popupAnchor: [0, -40],
  });
};

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
  const { token } = useAuth();
  const { slug } = useParams();
  const [locations, setLocations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [draftLocation, setDraftLocation] = useState(null);
  const [formData, setFormData] = useState({ title: '', description: '', status: 'Dreaming' });
  const [imageFile, setImageFile] = useState(null);
  const [toast, setToast] = useState(null);
  const [mapInstance, setMapInstance] = useState(null);
  const [editId, setEditId] = useState(null);

  useEffect(() => {
    if (!slug) return;
    fetchLocations();

    const socket = io(import.meta.env.VITE_API_URL || 'http://localhost:5000', {
      auth: { token, coupleSlug: slug }
    });
    
    socket.on('newLocation', (location) => {
      setLocations((prev) => [location, ...prev.filter(l => l._id !== location._id)]);
    });

    socket.on('deleteLocation', (id) => {
      setLocations((prev) => prev.filter(l => l._id !== id));
    });

    socket.on('updateLocation', (updatedLoc) => {
      setLocations((prev) => prev.map(l => l._id === updatedLoc._id ? updatedLoc : l));
    });

    return () => socket.disconnect();
  }, [slug, token]);

  const fetchLocations = async () => {
    try {
      const { data } = await getDreamLocations(slug);
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
      const data = new FormData();
      data.append('title', formData.title);
      data.append('description', formData.description);
      if (!editId) {
        data.append('lat', draftLocation.lat);
        data.append('lng', draftLocation.lng);
      }
      data.append('status', formData.status);
      if (imageFile) {
        data.append('image', imageFile);
      }

      if (editId) {
        await updateDreamLocation(slug, editId, data);
        setToast({ message: 'Dream location updated!', type: 'success' });
      } else {
        await createDreamLocation(slug, data);
        setToast({ message: 'Dream location added!', type: 'success' });
      }

      setModalOpen(false);
      setFormData({ title: '', description: '', status: 'Dreaming' });
      setImageFile(null);
      setDraftLocation(null);
      setEditId(null);
    } catch (err) {
      setToast({ message: 'Failed to save location', type: 'error' });
    }
  };

  const handleEditClick = (loc, e) => {
    e.stopPropagation();
    setEditId(loc._id);
    setFormData({ title: loc.title, description: loc.description, status: loc.status || 'Dreaming' });
    setDraftLocation({ lat: loc.lat, lng: loc.lng }); // dummy to pass validation
    setModalOpen(true);
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
          <>
            <div className="absolute left-4 md:left-8 top-48 bottom-8 w-[calc(100%-2rem)] md:w-80 bg-surface/80 backdrop-blur-xl rounded-[32px] shadow-2xl border border-white/60 overflow-hidden flex flex-col z-[1000] pointer-events-auto">
              <div className="p-5 bg-gradient-to-r from-primary/10 to-transparent border-b border-white/30">
                <h2 className="font-headline-sm text-xl text-primary flex items-center gap-2">
                  <span className="material-symbols-outlined">list_alt</span>
                  Pinned Dreams
                </h2>
              </div>
              <div className="flex-1 overflow-y-auto p-4 space-y-3 custom-scrollbar">
                {locations.length === 0 ? (
                  <div className="text-center mt-12 space-y-3 opacity-70">
                    <span className="material-symbols-outlined text-5xl text-primary">location_off</span>
                    <p className="text-on-surface-variant text-sm italic">No dreams pinned yet.<br/>Click the map to add one!</p>
                  </div>
                ) : (
                  locations.map((loc) => (
                    <div 
                      key={loc._id} 
                      onClick={() => mapInstance && mapInstance.flyTo([loc.lat, loc.lng], 6, { duration: 1.5 })}
                      className="bg-white/60 p-4 rounded-2xl hover:bg-white cursor-pointer transition-all duration-300 border border-white/50 shadow-sm hover:shadow-md transform hover:-translate-y-1 group"
                    >
                      <div className="flex items-start justify-between">
                        <h3 className="font-label-lg text-primary group-hover:text-secondary transition-colors">{loc.title}</h3>
                        <div className="flex items-center gap-2">
                           <button onClick={(e) => handleEditClick(loc, e)} className="opacity-0 group-hover:opacity-100 p-1 text-primary hover:bg-primary/10 rounded-full transition-all">
                             <span className="material-symbols-outlined text-[18px]">edit</span>
                           </button>
                           <span className="material-symbols-outlined text-error text-sm opacity-70 group-hover:opacity-100 group-hover:scale-110 transition-all">
                             {loc.status === 'Planning' ? 'edit_calendar' : loc.status === 'Booked' ? 'flight_takeoff' : loc.status === 'Visited' ? 'verified' : 'favorite'}
                           </span>
                        </div>
                      </div>
                      <p className="text-body-sm text-on-surface-variant mt-2 line-clamp-2 leading-relaxed">{loc.description}</p>
                      {loc.status && (
                        <span className="inline-block mt-2 text-[10px] uppercase font-bold tracking-wider px-2 py-1 rounded-full bg-primary/10 text-primary">
                          {loc.status}
                        </span>
                      )}
                    </div>
                  ))
                )}
              </div>
            </div>

            <MapContainer 
              center={[15.0, 100.0]} // Center around Southeast Asia (Thailand/Bali area)
              zoom={4} 
              ref={setMapInstance}
              className="h-full w-full z-0"
              style={{ backgroundColor: '#fbf9f8' }}
            >
            {/* Esri World Street Map (free, no API key, English labels globally) */}
            <TileLayer
              url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}"
              attribution='Tiles &copy; Esri'
            />
            <SearchField />
            <MapClickHandler setDraftLocation={setDraftLocation} setModalOpen={setModalOpen} />
            
            {/* Connect visited locations with a line */}
            {locations.filter(l => l.status === 'Visited').length > 1 && (
              <Polyline 
                positions={locations.filter(l => l.status === 'Visited').sort((a,b) => new Date(a.createdAt) - new Date(b.createdAt)).map(l => [l.lat, l.lng])} 
                pathOptions={{ color: '#F18C8E', weight: 3, dashArray: '5, 10', opacity: 0.8 }} 
              />
            )}

            {locations.map((loc) => (
              <Marker key={loc._id} position={[loc.lat, loc.lng]} icon={getHeartIcon(loc.status)}>
                <Popup className="custom-popup">
                  <div className="text-center p-2 max-w-[200px]">
                    {loc.photoUrl && (
                      <img src={loc.photoUrl} alt={loc.title} className="w-full h-24 object-cover rounded-xl mb-2 shadow-sm" />
                    )}
                    <h3 className="font-headline-md text-lg text-primary">{loc.title}</h3>
                    <p className="font-body-sm text-on-surface-variant mt-2">{loc.description}</p>
                  </div>
                </Popup>
              </Marker>
            ))}
          </MapContainer>
          </>
        )}
      </section>

      {/* Add Location Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center px-4 bg-surface/40 backdrop-blur-sm animate-fade-in">
          <div className="bg-surface-container-lowest p-8 rounded-[32px] shadow-2xl w-full max-w-md relative border border-primary/10">
            <button
              onClick={() => {
                setModalOpen(false);
                setEditId(null);
                setFormData({ title: '', description: '', status: 'Dreaming' });
              }}
              className="absolute top-6 right-6 text-on-surface-variant hover:text-on-surface transition-colors"
            >
              <span className="material-symbols-outlined">close</span>
            </button>
            <h2 className="font-headline-md text-2xl text-on-surface mb-6">
              {editId ? 'Update Dream' : 'Pin a Dream'}
            </h2>
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
              <div>
                <label className="block font-label-md text-on-surface mb-2">Status</label>
                <select
                  value={formData.status}
                  onChange={(e) => setFormData({ ...formData, status: e.target.value })}
                  className="w-full bg-surface border border-outline-variant rounded-2xl px-4 py-3 focus:outline-none focus:border-primary transition-colors appearance-none"
                >
                  <option value="Dreaming">Dreaming 💭</option>
                  <option value="Planning">Planning 📝</option>
                  <option value="Booked">Booked! ✈️</option>
                  <option value="Visited">Visited ❤️</option>
                </select>
              </div>
              <div>
                <label className="block font-label-md text-on-surface mb-2">Photo (Optional)</label>
                <input
                  type="file"
                  accept="image/*"
                  onChange={(e) => setImageFile(e.target.files[0])}
                  className="w-full bg-surface border border-outline-variant rounded-2xl px-4 py-2 focus:outline-none focus:border-primary transition-colors text-sm file:mr-4 file:py-2 file:px-4 file:rounded-full file:border-0 file:text-sm file:font-semibold file:bg-primary/10 file:text-primary hover:file:bg-primary/20"
                />
              </div>
              <button
                type="submit"
                className="w-full bg-primary text-on-primary font-label-lg py-4 rounded-full shadow-glow-primary hover:bg-secondary transition-colors"
              >
                {editId ? 'Update Dream 🪄' : 'Drop Pin 📍'}
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
