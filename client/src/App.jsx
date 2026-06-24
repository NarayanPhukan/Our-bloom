import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import Header from './components/Header';
import Footer from './components/Footer';
import PetalEffect from './components/PetalEffect';
import JourneyPage from './pages/JourneyPage';
import MemoriesPage from './pages/MemoriesPage';
import LoveNotesPage from './pages/LoveNotesPage';

function App() {
  return (
    <Router>
      <div className="bg-lily-pattern text-on-background min-h-screen flex flex-col">
        <Header />
        <PetalEffect />

        <main className="pt-32 pb-20 flex-1">
          <Routes>
            <Route path="/" element={<JourneyPage />} />
            <Route path="/memories" element={<MemoriesPage />} />
            <Route path="/love-notes" element={<LoveNotesPage />} />
          </Routes>
        </main>

        <Footer />
      </div>
    </Router>
  );
}

export default App;
