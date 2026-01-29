import React, { useState, useEffect, useMemo } from 'react';
import './App.css';
import Header from './components/Header/Header';
import Sidebar from './components/Sidebar/Sidebar';
import MapView from './components/Map/MapView';
import Stats from './components/Stats/Stats';

const API_BASE = '';

function App() {
  // Stan danych
  const [data, setData] = useState([]);
  const [initialData, setInitialData] = useState([]); // Przechowuj dane z pierwszego ładowania
  const [initialBtsData, setInitialBtsData] = useState([]); // Przechowuj BTS z pierwszego ładowania
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [btsData, setBtsData] = useState([]);
  const [speedtestData, setSpeedtestData] = useState([]);
  const [statsVisible, setStatsVisible] = useState(true); 
  // Stan filtrów
const [filters, setFilters] = useState({
  operators: [],
  speedtestOperators: [], 
  signalRange: [-170, -20],
  speedRange: [0, 500],  
  speedtestUploadRange: [0, 100],
  speedtestPingRange: [0, 200],
  networkTypes: [],
  shortCodeFilter: '',
  speedtestShortCodeFilter: '',
  dateRange: {
    start: null,
    end: null
  },
  displayLimit: 100000,
  showOnlyUnmatched: false,
  timeRange: null,
  visibleLayers: {
    telemetry: true,
    bts: true,
    speedtest: false
  },
  // Pola dla wizualizacji
  telemetryVisualization: 'points', // 'points' | 'heatmap' | 'hexagons' | 'bars'
  speedtestVisualization: 'points',  // 'points' | 'heatmap' | 'bars'
  speedtestBarType: 'download',       // 'download' | 'upload' | 'ping'
  is3DMode: false                     // tryb 3D
});
  
  // Stan UI
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [mapStyle, setMapStyle] = useState('DATAVIZ.DARK');
  const [themeMode, setThemeMode] = useState('auto');
  
  // Stan animacji
  const [isAnimationEnabled, setIsAnimationEnabled] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [animationTime, setAnimationTime] = useState(null);
  const [animationSpeed, setAnimationSpeed] = useState(1); // 1 = normal, 2 = 2x, 0.5 = half


useEffect(() => {
  // Lista jasnych stylów mapy
  const lightStyles = [
    'STREETS.LIGHT', 'STREETS.PASTEL',
    'BASIC.LIGHT', 'BRIGHT.LIGHT', 'BRIGHT.PASTEL',
    'DATAVIZ.LIGHT', 'BACKDROP.LIGHT',
    'TONER.LITE', 'AQUARELLE.VIVID',
    'LANDSCAPE', 'LANDSCAPE.VIVID',
    'SATELLITE', 'HYBRID', 'OPENSTREETMAP'
  ];
  
  let shouldBeLightMode = false;
  
  if (themeMode === 'auto') {
    // Automatycznie na podstawie stylu mapy
    shouldBeLightMode = lightStyles.includes(mapStyle);
  } else if (themeMode === 'light') {
    // Wymuś jasny
    shouldBeLightMode = true;
  } else {
    // themeMode === 'dark' - wymuś ciemny
    shouldBeLightMode = false;
  }
  
  // Dodaj/usuń klasę light-mode z body
  if (shouldBeLightMode) {
    document.body.classList.add('light-mode');
  } else {
    document.body.classList.remove('light-mode');
  }
}, [mapStyle, themeMode]);

useEffect(() => {
  // Nie rób nic przy pierwszym renderze (dane już pobrane w pierwszym useEffect)
  if (!data.length) return;
  
  // Jeśli daty zostały wyczyszczone - przywróć pierwsze dane
  if (!filters.dateRange.start && !filters.dateRange.end) {
    console.log('Przywracanie początkowych danych...');
    setData(initialData);
    setBtsData(initialBtsData);
    return;
  }
  
  // Zawsze pobiora nowe dane gdy zmieni się dateRange (ustawienie lub wyczyszczenie)
  const fetchData = async () => {
      try {
        setLoading(true);
        
        let url = `${API_BASE}/api/telemetry-with-bts?limit=100000`;
        
        if (filters.dateRange.start) {
          url += `&start_date=${filters.dateRange.start}`;
        }
        if (filters.dateRange.end) {
          url += `&end_date=${filters.dateRange.end}`;
        }
        
        console.log('Aktualizacja danych:', url);
        
        const response = await fetch(url);
        const json = await response.json();
        
        setData(json.items || []);
        
        // Filtruj dane według filtrów (np. showOnlyUnmatched)
        let filteredItems = json.items || [];
        if (filters.showOnlyUnmatched) {
          filteredItems = filteredItems.filter(p => !p.relatedBts);
        }
        
        // Aktualizuj BTS
        const btsMap = new Map();
        (filteredItems || []).forEach(point => {
          if (point.relatedBts && !btsMap.has(point.relatedBts.id)) {
            btsMap.set(point.relatedBts.id, {
              ...point.relatedBts,
              position: [point.relatedBts.lon, point.relatedBts.lat],
              measurementCount: json.items.filter(p => p.relatedBts?.id === point.relatedBts.id).length
            });
          }
        });
        
        setBtsData(Array.from(btsMap.values()));
        // Ustaw przefiltrowane dane telemetrii
        setData(filteredItems);
        // Pobierz dane speedtestów
console.log('Pobieranie danych speedtestów...');
let speedtestUrl = `${API_BASE}/api/speedtest?limit=5000`;

// Dodaj filtry daty jeśli ustawione
if (filters.dateRange.start) {
  speedtestUrl += `&start_date=${filters.dateRange.start}`;
}
if (filters.dateRange.end) {
  speedtestUrl += `&end_date=${filters.dateRange.end}`;
}
// Dodaj filtr short_code jeśli ustawiony
if (filters.speedtestShortCodeFilter && filters.speedtestShortCodeFilter.trim()) {
  speedtestUrl += `&short_code=${encodeURIComponent(filters.speedtestShortCodeFilter)}`;
}

try {
  const speedtestResponse = await fetch(speedtestUrl);
  if (speedtestResponse.ok) {
    const speedtestJson = await speedtestResponse.json();
    console.log('Pobrano:', speedtestJson.items?.length || 0, 'speedtestów');
    setSpeedtestData(speedtestJson.items || []);
  } else {
    console.warn('Brak endpointu speedtest lub błąd');
    setSpeedtestData([]);
  }
} catch (err) {
  console.warn('Speedtest endpoint niedostępny:', err.message);
  setSpeedtestData([]);
}
        setLoading(false);
        
      } catch (err) {
        console.error('Błąd aktualizacji:', err);
        setLoading(false);
      }
    };
    
    fetchData();
}, [filters.dateRange.start, filters.dateRange.end, initialData, initialBtsData]);

// Oddzielny useEffect do filtrowania danych gdy zmieni się showOnlyUnmatched
useEffect(() => {
  if (!initialData.length) return;
  
  let filteredItems = initialData;
  if (filters.showOnlyUnmatched) {
    filteredItems = filteredItems.filter(p => !p.relatedBts);
  }
  
  setData(filteredItems);
}, [filters.showOnlyUnmatched, initialData]);

useEffect(() => {
  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);
      
      // Buduj URL z parametrami
let url = `${API_BASE}/api/telemetry-with-bts?limit=100000&minutes=525600`;      
      // Dodaj filtr dat jeśli ustawiony
      if (filters.dateRange.start) {
        url += `&start_date=${filters.dateRange.start}`;
      }
      if (filters.dateRange.end) {
        url += `&end_date=${filters.dateRange.end}`;
      }
      // Dodaj filtr short_code jeśli ustawiony
      if (filters.shortCodeFilter && filters.shortCodeFilter.trim()) {
        url += `&short_code=${encodeURIComponent(filters.shortCodeFilter)}`;
      }
      
      console.log('Pobieranie danych:', url);
      
      const response = await fetch(url);
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const json = await response.json();
      
      console.log('Pobrano:', json.items?.length || 0, 'punktów');
      
      if (!json.items || json.items.length === 0) {
        console.warn('Brak danych dla wybranych filtrów');
        setData([]);
        setBtsData([]);
        setLoading(false);
        return;
      }
      
      setData(json.items);
      
      // ZAPISZ POCZĄTKOWE DANE (do przywrócenia gdy daty zostaną wyczyszczone)
      setInitialData(json.items);
      
      // Wyciągnij BTS
      const btsMap = new Map();
      json.items.forEach(point => {
        if (point.relatedBts && !btsMap.has(point.relatedBts.id)) {
          btsMap.set(point.relatedBts.id, {
            ...point.relatedBts,
            position: [point.relatedBts.lon, point.relatedBts.lat],
            measurementCount: json.items.filter(p => p.relatedBts?.id === point.relatedBts.id).length
          });
        }
      });

      const btsArray = Array.from(btsMap.values());
      setBtsData(btsArray);
      
      // ZAPISZ POCZĄTKOWE BTS
      setInitialBtsData(btsArray);
      
      // ========== POBIERANIE SPEEDTESTÓW ==========
      console.log('Pobieranie danych speedtestów...');
      let speedtestUrl = `${API_BASE}/api/speedtest?limit=5000`;
      
      // Dodaj filtry daty jeśli ustawione
      if (filters.dateRange.start) {
        speedtestUrl += `&start_date=${filters.dateRange.start}`;
      }
      if (filters.dateRange.end) {
        speedtestUrl += `&end_date=${filters.dateRange.end}`;
      }
      // Dodaj filtr short_code dla speedtestów jeśli ustawiony
      if (filters.speedtestShortCodeFilter && filters.speedtestShortCodeFilter.trim()) {
        speedtestUrl += `&short_code=${encodeURIComponent(filters.speedtestShortCodeFilter)}`;
      }
      
      try {
        const speedtestResponse = await fetch(speedtestUrl);
        if (speedtestResponse.ok) {
          const speedtestJson = await speedtestResponse.json();
          console.log('Pobrano:', speedtestJson.items?.length || 0, 'speedtestów');
          setSpeedtestData(speedtestJson.items || []);
        } else {
          console.warn('Błąd pobierania speedtestów:', speedtestResponse.status);
          setSpeedtestData([]);
        }
      } catch (err) {
        console.warn('Speedtest endpoint niedostępny:', err.message);
        setSpeedtestData([]);
      }
      // ========== KONIEC SPEEDTESTÓW ==========
      
      setLoading(false);
      
    } catch (err) {
      console.error('Błąd:', err);
      setError(`Błąd: ${err.message}`);
      setLoading(false);
    }
  };

  fetchData();
}, [filters.shortCodeFilter, filters.speedtestShortCodeFilter]);
  
  // Filtrowanie danych
const filteredData = useMemo(() => {
  let filtered = data.filter(point => {
    // Filtr operatorów
    if (filters.operators.length > 0 && !filters.operators.includes(point.operator)) {
      return false;
    }
    
    // Filtr siły sygnału
    if (point.signal < filters.signalRange[0] || point.signal > filters.signalRange[1]) {
      return false;
    }
    
    // Filtr typu sieci
    if (filters.networkTypes.length > 0 && !filters.networkTypes.includes(point.networkType)) {
      return false;
    }
    
    // Filtr przedziału czasowego (lokalny)
    if (filters.timeRange !== null) {
      const pointDate = new Date(point.sendTime);
      const cutoffDate = new Date();
      cutoffDate.setDate(cutoffDate.getDate() - filters.timeRange);
      
      if (pointDate < cutoffDate) {
        return false;
      }
    }
    
    // Filtr zakresu dat (jeśli ustawiony)
    if (filters.dateRange?.start) {
      const start = new Date(filters.dateRange.start);
      if (new Date(point.sendTime) < start) return false;
    }
    if (filters.dateRange?.end) {
      const end = new Date(filters.dateRange.end);
      if (new Date(point.sendTime) > end) return false;
    }
    
    return true;
  });
  
  // Ogranicz liczbę wyświetlanych punktów (bierz najnowsze)
  if (filters.displayLimit && filtered.length > filters.displayLimit) {
    filtered = filtered
      .sort((a, b) => new Date(b.sendTime) - new Date(a.sendTime))
      .slice(0, filters.displayLimit);
  }
  
  return filtered;
}, [data, filters]);

// Filtrowane dane speedtestów
const filteredSpeedtestData = useMemo(() => {
  let filtered = speedtestData.filter(test => {
    // Filtr operatorów speedtest
    if (filters.speedtestOperators && filters.speedtestOperators.length > 0 &&
        !filters.speedtestOperators.includes(test.operator)) {
      return false;
    }

    // Filtr prędkości download
    if (filters.speedRange) {
      const download = test.downloadSpeed || 0;
      if (download < filters.speedRange[0] || download > filters.speedRange[1]) {
        return false;
      }
    }

    // Filtr prędkości upload
    if (filters.speedtestUploadRange) {
      const upload = test.uploadSpeed || 0;
      if (upload < filters.speedtestUploadRange[0] || upload > filters.speedtestUploadRange[1]) {
        return false;
      }
    }

    // Filtr ping (im mniejszy tym lepszy)
    if (filters.speedtestPingRange) {
      const ping = test.ping || 0;
      if (ping < filters.speedtestPingRange[0] || ping > filters.speedtestPingRange[1]) {
        return false;
      }
    }

    // Filtr przedziału czasowego (dni wstecz)
    if (filters.timeRange !== null) {
      const testDate = new Date(test.timestamp);
      const cutoffDate = new Date();
      cutoffDate.setDate(cutoffDate.getDate() - filters.timeRange);
      if (testDate < cutoffDate) {
        return false;
      }
    }

    // Filtr zakresu dat (jeśli ustawiony)
    if (filters.dateRange?.start) {
      const start = new Date(filters.dateRange.start);
      if (new Date(test.timestamp) < start) return false;
    }
    if (filters.dateRange?.end) {
      const end = new Date(filters.dateRange.end);
      if (new Date(test.timestamp) > end) return false;
    }

    return true;
  });

  // Ogranicz liczbę wyświetlanych testów tak jak punkty (najnowsze)
  if (filters.displayLimit && filtered.length > filters.displayLimit) {
    filtered = filtered
      .sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp))
      .slice(0, filters.displayLimit);
  }

  return filtered;
}, [speedtestData, filters]);

// ===== ANIMACJA CZASOWA =====
// Inicjalizuj animationTime na najstarszą datę, jeśli nie ustawiony
useEffect(() => {
  if (isAnimationEnabled && animationTime === null && filteredData.length > 0) {
    const minTime = new Date(Math.min(...filteredData.map(p => new Date(p.sendTime))));
    setAnimationTime(minTime);
  }
}, [isAnimationEnabled, filteredData]);

// Animacja: advance time co klatkę (skok 10 min)
useEffect(() => {
  if (!isPlaying || animationTime === null || filteredData.length === 0) return;
  
  const maxTime = new Date(Math.max(...filteredData.map(p => new Date(p.sendTime))));
  const stepMs = 10 * 60 * 1000 * animationSpeed; // 10 minut * prędkość
  
  const frame = () => {
    setAnimationTime(prev => {
      if (!prev) return prev;
      const next = new Date(prev.getTime() + stepMs);
      if (next >= maxTime) {
        // Loop: reset to start
        return new Date(Math.min(...filteredData.map(p => new Date(p.sendTime))));
      }
      return next;
    });
  };
  
  const interval = setInterval(frame, 100); // update every 100ms
  return () => clearInterval(interval);
}, [isPlaying, animationTime, filteredData, animationSpeed]);

// Filtruj dane animacji - pokaż tylko do animationTime
const animatedData = useMemo(() => {
  if (!isAnimationEnabled || animationTime === null) return filteredData;
  return filteredData.filter(p => new Date(p.sendTime) <= animationTime);
}, [filteredData, isAnimationEnabled, animationTime]);
  
  // Oblicz statystyki
  const stats = {
    total: animatedData.length,
    avgSignal: animatedData.length > 0 
      ? (animatedData.reduce((sum, p) => sum + p.signal, 0) / animatedData.length).toFixed(1)
      : 0,
    operators: [...new Set(animatedData.map(p => p.operator))].length
  };

  // ===== HANDLERY ANIMACJI =====
  const handleAnimationToggle = (enabled) => {
    setIsAnimationEnabled(enabled);
  };

  const handlePlayPause = () => {
    setIsPlaying(!isPlaying);
  };

  const handleTimeChange = (newTime) => {
    setAnimationTime(newTime);
  };

  const handleSpeedChange = (speed) => {
    setAnimationSpeed(speed);
  };

  // Oblicz min/max czasu dla animacji
  const minMaxAnimationTime = useMemo(() => {
    if (filteredData.length === 0) return { min: null, max: null };
    const times = filteredData.map(p => new Date(p.sendTime));
    return {
      min: new Date(Math.min(...times.map(t => t.getTime()))),
      max: new Date(Math.max(...times.map(t => t.getTime())))
    };
  }, [filteredData]);

  // ========= EXPORT =========

  const serializeToCSV = (rows, headers, delimiter = ';') => {
    const escape = (val) => {
      if (val === null || val === undefined) return '';
      const s = String(val);
      if (s.includes(delimiter) || s.includes('\n') || s.includes('"')) {
        return '"' + s.replace(/"/g, '""') + '"';
      }
      return s;
    };
    const head = headers.join(delimiter);
    const body = rows.map(r => headers.map(h => escape(r[h])).join(delimiter)).join('\r\n');
    // Add UTF-8 BOM for Excel compatibility
    return '\uFEFF' + head + '\r\n' + body;
  };

  const downloadFile = (content, filename, mime = 'text/csv;charset=utf-8') => {
    const blob = new Blob([content], { type: mime });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const handleExport = (options) => {
    // options: { telemetry: boolean, speedtest: boolean, format: 'csv' | 'geojson' }
    const ts = new Date().toISOString().replace(/[:.]/g, '-');

    if (options.format === 'geojson') {
      const featureCollection = {
        type: 'FeatureCollection',
        features: []
      };
      if (options.telemetry) {
        featureCollection.features.push(
          ...filteredData.map(p => ({
            type: 'Feature',
            geometry: { type: 'Point', coordinates: [p.longitude, p.latitude] },
            properties: {
              id: p.id,
              operator: p.operator,
              networkType: p.networkType,
              nrMode: p.nrMode,
              signal: p.signal,
              rsrp: p.rsrp,
              rsrq: p.rsrq,
              sinr: p.sinr,
              rssi: p.rssi,
              rat: p.rat,
              band: p.band,
              arfcn: p.arfcn,
              pci: p.pci,
              eci: p.eci,
              nci: p.nci,
              enb: p.enb,
              cellId: p.cellId,
              sectorId: p.sectorId,
              timingAdvance: p.timingAdvance,
              tac: p.tac,
              lac: p.lac,
              sendTime: p.sendTime,
              bts_stationId: p.relatedBts?.stationId,
              bts_btsid: p.relatedBts?.btsid,
              bts_enbi: p.relatedBts?.enbi,
              bts_siecId: p.relatedBts?.siecId,
              bts_standard: p.relatedBts?.standard,
              bts_pasmo: p.relatedBts?.pasmo,
              bts_duplex: p.relatedBts?.duplex,
              bts_carrier: p.relatedBts?.carrier,
              bts_lokalizacja: p.relatedBts?.lokalizacja,
              bts_miejscowosc: p.relatedBts?.miejscowosc,
              bts_wojewodztwoId: p.relatedBts?.wojewodztwoId,
              bts_lat: p.relatedBts?.lat,
              bts_lon: p.relatedBts?.lon
            }
          }))
        );
      }
      if (options.speedtest) {
        featureCollection.features.push(
          ...filteredSpeedtestData.map(t => ({
            type: 'Feature',
            geometry: { type: 'Point', coordinates: [t.longitude, t.latitude] },
            properties: {
              id: t.id,
              operator: t.operator,
              downloadSpeed: t.downloadSpeed,
              uploadSpeed: t.uploadSpeed,
              ping: t.ping,
              jitter: t.jitter,
              timestamp: t.timestamp
            }
          }))
        );
      }
      const content = JSON.stringify(featureCollection);
      downloadFile(content, `export_${ts}.geojson`, 'application/geo+json');
      return;
    }

    // Default CSV
    if (options.telemetry) {
      const headers = [
        'id','operator','networkType','nrMode','signal','rsrp','rsrq','sinr','rssi','rat','band','arfcn','pci','eci','nci','enb','cellId','sectorId','timingAdvance','tac','lac','latitude','longitude','sendTime',
        'bts_stationId','bts_btsid','bts_enbi','bts_siecId','bts_standard','bts_pasmo','bts_duplex','bts_carrier','bts_lokalizacja','bts_miejscowosc','bts_wojewodztwoId','bts_lat','bts_lon'
      ];
      const rows = filteredData.map(p => ({
        id: p.id,
        operator: p.operator,
        networkType: p.networkType,
        nrMode: p.nrMode,
        signal: p.signal,
        rsrp: p.rsrp,
        rsrq: p.rsrq,
        sinr: p.sinr,
        rssi: p.rssi,
        rat: p.rat,
        band: p.band,
        arfcn: p.arfcn,
        pci: p.pci,
        eci: p.eci,
        nci: p.nci,
        enb: p.enb,
        cellId: p.cellId,
        sectorId: p.sectorId,
        timingAdvance: p.timingAdvance,
        tac: p.tac,
        lac: p.lac,
        latitude: p.latitude,
        longitude: p.longitude,
        sendTime: p.sendTime,
        bts_stationId: p.relatedBts?.stationId,
        bts_btsid: p.relatedBts?.btsid,
        bts_enbi: p.relatedBts?.enbi,
        bts_siecId: p.relatedBts?.siecId,
        bts_standard: p.relatedBts?.standard,
        bts_pasmo: p.relatedBts?.pasmo,
        bts_duplex: p.relatedBts?.duplex,
        bts_carrier: p.relatedBts?.carrier,
        bts_lokalizacja: p.relatedBts?.lokalizacja,
        bts_miejscowosc: p.relatedBts?.miejscowosc,
        bts_wojewodztwoId: p.relatedBts?.wojewodztwoId,
        bts_lat: p.relatedBts?.lat,
        bts_lon: p.relatedBts?.lon
      }));
      const csv = serializeToCSV(rows, headers);
      downloadFile(csv, `telemetry_${ts}.csv`);
    }
    if (options.speedtest) {
      const headers = [
        'id','operator','downloadSpeed','uploadSpeed','ping','jitter','latitude','longitude','timestamp'
      ];
      const rows = filteredSpeedtestData.map(t => ({
        id: t.id,
        operator: t.operator,
        downloadSpeed: t.downloadSpeed,
        uploadSpeed: t.uploadSpeed,
        ping: t.ping,
        jitter: t.jitter,
        latitude: t.latitude,
        longitude: t.longitude,
        timestamp: t.timestamp
      }));
      const csv = serializeToCSV(rows, headers);
      downloadFile(csv, `speedtests_${ts}.csv`);
    }
  };

  return (
    <div className="app">
<Header 
  onToggleSidebar={() => setSidebarOpen(!sidebarOpen)}
  sidebarOpen={sidebarOpen}
  mapStyle={mapStyle}
  onMapStyleChange={setMapStyle}
  themeMode={themeMode}
  onThemeModeChange={setThemeMode}
/>
      
      <div className="app-content">
        <Sidebar 
          isOpen={sidebarOpen}
          filters={filters}
          onFiltersChange={setFilters}
          data={data}
          loading={loading}
          speedtestData={speedtestData}
          onExport={handleExport}
          isAnimationEnabled={isAnimationEnabled}
          isPlaying={isPlaying}
          animationTime={animationTime}
          animationSpeed={animationSpeed}
          minAnimationTime={minMaxAnimationTime.min}
          maxAnimationTime={minMaxAnimationTime.max}
          onAnimationToggle={handleAnimationToggle}
          onPlayPause={handlePlayPause}
          onTimeChange={handleTimeChange}
          onSpeedChange={handleSpeedChange}
        />
        
        <div className="map-container">
          {loading && (
            <div className="loading-overlay">
              <div className="spinner"></div>
              <p>Ładowanie danych...</p>
            </div>
          )}
          
          {error && (
            <div className="error-overlay">
              <div className="error-message">
                <h3>Błąd ładowania danych</h3>
                <p>{error}</p>
                <button onClick={() => window.location.reload()}>
                  Spróbuj ponownie
                </button>
              </div>
            </div>
          )}
          
          {!loading && !error && (
            <>
              <MapView 
  data={isAnimationEnabled ? animatedData : filteredData} 
  speedtestData={filteredSpeedtestData}
  mapStyle={mapStyle} 
  themeMode={themeMode} 
  btsData={btsData}
  visibleLayers={filters.visibleLayers}
  telemetryVisualization={filters.telemetryVisualization || 'points'}
  speedtestVisualization={filters.speedtestVisualization || 'points'}
  speedtestBarType={filters.speedtestBarType || 'download'}
  is3DMode={filters.is3DMode || false}
/>
              <Stats 
  stats={stats} 
  sidebarOpen={sidebarOpen}
  visible={statsVisible}
  onToggle={() => setStatsVisible(!statsVisible)}
/>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export default App;