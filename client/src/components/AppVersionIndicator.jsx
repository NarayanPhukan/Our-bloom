import { useState, useEffect } from 'react';
import { isNativeApp } from '../utils/capacitorPlugins';

export default function AppVersionIndicator() {
  const [version, setVersion] = useState('');

  useEffect(() => {
    // Read OTA version from localStorage, fallback to package version or 'Web'
    if (isNativeApp()) {
      const otaVersion = localStorage.getItem('bloom_ota_version');
      setVersion(otaVersion ? `v${otaVersion}` : 'v1.0.0 (Native)');
    } else {
      setVersion('v1.0.0 (Web)');
    }
  }, []);

  return (
    <div className="fixed bottom-2 left-2 z-50 pointer-events-none opacity-30 font-label-sm text-[9px] text-on-background uppercase tracking-widest">
      Our Bloom {version}
    </div>
  );
}
