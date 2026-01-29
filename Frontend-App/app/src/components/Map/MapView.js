import React, { useState, useMemo, useEffect } from 'react';
import Map from 'react-map-gl';
import DeckGL from '@deck.gl/react';
import { HeatmapLayer, HexagonLayer } from '@deck.gl/aggregation-layers';
import { ScatterplotLayer, IconLayer, LineLayer, TextLayer, ColumnLayer } from '@deck.gl/layers';
import './MapView.css';
import { getMapStyleUrl } from '../../utils/mapStyles';
import antennaIcon from '../../assets/antenna-svgrepo-com.svg';

const INITIAL_VIEW_STATE = {
  longitude: 16.9252, 
  latitude: 52.4064,  
  zoom: 11,
  pitch: 0,
  bearing: 0
};

function MapView({ 
  data, 
  mapStyle, 
  themeMode, 
  btsData, 
  speedtestData, 
  visibleLayers = { telemetry: true, bts: true, speedtest: true },
  telemetryVisualization = 'points',
  speedtestVisualization = 'points',
  speedtestBarType = 'download',
  is3DMode = false
}) {
  const [viewState, setViewState] = useState(INITIAL_VIEW_STATE);
  const [hoveredPoint, setHoveredPoint] = useState(null);
  const [clickedPoint, setClickedPoint] = useState(null);
  const [selectedBts, setSelectedBts] = useState(null);
  const [clickedSpeedtest, setClickedSpeedtest] = useState(null);

  // Auto-switch pitch dla trybu 3D
  useEffect(() => {
    if (is3DMode && viewState.pitch === 0) {
      setViewState(prev => ({ ...prev, pitch: 45, bearing: 0 }));
    } else if (!is3DMode && viewState.pitch !== 0) {
      setViewState(prev => ({ ...prev, pitch: 0, bearing: 0 }));
    }
  }, [is3DMode]);

  // Styl mapy
  const MAPTILER_KEY = 'BthCyHbl7nrJGIrqyfDs';
  const MAP_STYLE = getMapStyleUrl(mapStyle, MAPTILER_KEY);

  const lightMapStyles = ['STREETS.LIGHT', 'STREETS.PASTEL', 'BASIC.LIGHT', 
    'BRIGHT.LIGHT', 'BRIGHT.PASTEL', 'DATAVIZ.LIGHT', 'BACKDROP.LIGHT', 
    'TONER.LITE', 'AQUARELLE.VIVID', 'LANDSCAPE', 'LANDSCAPE.VIVID', 
    'SATELLITE', 'HYBRID', 'OPENSTREETMAP'];
  
  const isLightMap = themeMode === 'light' || 
    (themeMode === 'auto' && lightMapStyles.includes(mapStyle));

  // Kolory dla heatmapy 
  const colorRange = [
    [65, 105, 225, 200],   // najsłabszy sygnał
    [70, 130, 180, 210],   
    [100, 149, 237, 220],  
    [135, 206, 250, 230],  
    [173, 216, 230, 240],  
    [224, 255, 255, 250]   // najsilniejszy sygnał
  ];
  
const btsLayer = useMemo(() => {
  if (!visibleLayers.bts) return null;
  
  const ICON_MAPPING = {
    antenna: {
      x: 0,
      y: 0,
      width: 700,
      height: 512,
      mask: false
    }
  };

  return new IconLayer({
    id: 'bts-layer',
    data: btsData,
    getPosition: d => d.position,
    getIcon: d => 'antenna',
    getSize: 1,
    sizeScale: 25,
    sizeMinPixels: 25,
    sizeMaxPixels: 25,
    iconAtlas: antennaIcon,
    iconMapping: ICON_MAPPING,

    pickable: true,
    onClick: info => {
      if (info.object) {
        setSelectedBts(info.object);
      }
    },
  });
}, [btsData, viewState, visibleLayers.bts]);

// Linie między BTS a punktami
const connectionLines = useMemo(() => {
  if (!selectedBts || !visibleLayers.telemetry) return null;
  
  const relatedPoints = data.filter(p => p.relatedBts?.id === selectedBts.id);
  
  return new LineLayer({
    id: 'connection-lines',
    data: relatedPoints.map(point => ({
      source: [selectedBts.lon, selectedBts.lat],
      target: [point.longitude, point.latitude]
    })),
    getSourcePosition: d => d.source,
    getTargetPosition: d => d.target,
    getColor: [59, 130, 246, 100],
    getWidth: 2,
    widthMinPixels: 1
  });
}, [selectedBts, data, visibleLayers.telemetry]);

  // ===== ORYGINALNE WARSTWY PUNKTÓW (używane gdy telemetryVisualization === 'points') =====
const pointsGlowLayer = useMemo(() => {
  if (!visibleLayers.telemetry || telemetryVisualization !== 'points') return null;
  
  return new ScatterplotLayer({
    id: 'points-glow-layer',
    data,
    getPosition: d => d.position,
    getFillColor: d => {
      const signal = d.signal;
      const opacity = isLightMap ? 120 : 80;
      if (signal >= -70) return [34, 197, 94, opacity];
      if (signal >= -85) return [234, 179, 8, opacity];
      if (signal >= -100) return [249, 115, 22, opacity];
      return [239, 68, 68, opacity];
    },
    getRadius: 8,
    radiusMinPixels: 6,
    radiusMaxPixels: 12,
    pickable: false,
    updateTriggers: {
      getFillColor: [data, isLightMap]
    }
  });
}, [data, isLightMap, visibleLayers.telemetry, telemetryVisualization]);

const pointsCoreLayer = useMemo(() => {
  if (!visibleLayers.telemetry || telemetryVisualization !== 'points') return null;
  
  return new ScatterplotLayer({
    id: 'points-core-layer',
    data,
    getPosition: d => d.position,
    getFillColor: d => {
      const signal = d.signal;
      if (signal >= -70) return [34, 197, 94, 255];
      if (signal >= -85) return [234, 179, 8, 255];
      if (signal >= -100) return [249, 115, 22, 255];
      return [239, 68, 68, 255];
    },
    getRadius: 6,
    radiusMinPixels: 4,
    radiusMaxPixels: 8,
    filled: true,
    pickable: true,
    onHover: info => setHoveredPoint(info.object),
    onClick: info => {
      if (info.object) {
        setClickedPoint(info.object);
      }
    },
    updateTriggers: {
      getFillColor: [data]
    }
  });
}, [data, visibleLayers.telemetry, telemetryVisualization]);

// ===== TELEMETRIA HEATMAP =====
const telemetryHeatmapLayer = useMemo(() => {
  if (!visibleLayers.telemetry || telemetryVisualization !== 'heatmap') return null;
  
  return new HeatmapLayer({
    id: 'telemetry-heatmap-layer',
    data,
    getPosition: d => d.position,
    getWeight: d => 1,
    radiusPixels: 40,
    intensity: 1,
    threshold: 0.05,
    colorRange,
    aggregation: 'SUM'
  });
}, [data, visibleLayers.telemetry, telemetryVisualization]);

// ===== TELEMETRIA HEXAGONY =====
const telemetryHexagonLayer = useMemo(() => {
  if (!visibleLayers.telemetry || telemetryVisualization !== 'hexagons') return null;
  
  return new HexagonLayer({
    id: 'telemetry-hexagon-layer',
    data,
    getPosition: d => d.position,
    getElevationWeight: d => {
      const normalized = (d.signal + 120) / 80;
      return Math.max(0, Math.min(1, normalized));
    },
    getColorWeight: d => {
      // Kolor według mocy sygnału, nie ilości
      const normalized = (d.signal + 120) / 80;
      return Math.max(0, Math.min(1, normalized));
    },
    elevationScale: is3DMode ? 4 : 0,
    radius: 50,
    coverage: 0.9,
    extruded: is3DMode,
    pickable: true,
    // agregacja MEAN (średnia)
    colorAggregation: 'MEAN',
    elevationAggregation: 'MEAN',
    colorRange: [
      [239, 68, 68],      // Czerwony - słaby sygnał
      [249, 115, 22],     // Pomarańczowy
      [234, 179, 8],      // Żółty
      [34, 197, 94]       // Zielony - mocny sygnał
    ],
    onClick: info => {
      if (info.object && info.object.points && info.object.points.length > 0) {
        const firstPoint = info.object.points[0].source || info.object.points[0];
        setClickedPoint(firstPoint);
        setClickedSpeedtest(null);
      }
    }
  });
}, [data, visibleLayers.telemetry, telemetryVisualization, is3DMode]);

// ===== TELEMETRIA SŁUPKI =====
const telemetryBarsLayer = useMemo(() => {
  if (!visibleLayers.telemetry || telemetryVisualization !== 'bars') return null;
  
  return new ColumnLayer({
    id: 'telemetry-bars-layer',
    data,
    diskResolution: 12,
    radius: 6,
    extruded: true,
    pickable: true,
    elevationScale: 1,
    getPosition: d => d.position,
    getFillColor: d => {
      const signal = d.signal;
      if (signal >= -70) return [34, 197, 94, 255];
      if (signal >= -85) return [234, 179, 8, 255];
      if (signal >= -100) return [249, 115, 22, 255];
      return [239, 68, 68, 255];
    },
    getLineColor: [0, 0, 0, 80],
    getElevation: d => {
      const normalized = (d.signal + 120) / 80;
      return Math.max(10, normalized * 500);
    },
    onClick: info => {
      if (info.object) {
        setClickedPoint(info.object);
        setClickedSpeedtest(null);
      }
    },
    updateTriggers: {
      getFillColor: [data],
      getElevation: [data]
    }
  });
}, [data, visibleLayers.telemetry, telemetryVisualization]);

// ===== WARSTWY SPEEDTEST (używane gdy speedtestVisualization === 'points') =====
const speedtestGlowLayer = useMemo(() => {
  if (!visibleLayers.speedtest || !speedtestData || speedtestData.length === 0 || speedtestVisualization !== 'points') return null;
  
  return new ScatterplotLayer({
    id: 'speedtest-glow-layer',
    data: speedtestData,
    getPosition: d => d.position,
    getFillColor: d => {
      const download = d.downloadSpeed || 0;
      const opacity = isLightMap ? 100 : 70;
      
      if (download >= 100) return [138, 43, 226, opacity];
      if (download >= 50) return [59, 130, 246, opacity];
      if (download >= 20) return [34, 197, 94, opacity];
      if (download >= 5) return [234, 179, 8, opacity];
      return [239, 68, 68, opacity];
    },
    getRadius: 8,
    radiusMinPixels: 6,
    radiusMaxPixels: 12,
    pickable: false,
    updateTriggers: {
      getFillColor: [speedtestData, isLightMap]
    }
  });
}, [speedtestData, isLightMap, visibleLayers.speedtest, speedtestVisualization]);

const speedtestCoreLayer = useMemo(() => {
  if (!visibleLayers.speedtest || !speedtestData || speedtestData.length === 0 || speedtestVisualization !== 'points') return null;
  
  return new ScatterplotLayer({
    id: 'speedtest-core-layer',
    data: speedtestData,
    getPosition: d => d.position,
    getFillColor: d => {
      const download = d.downloadSpeed || 0;
      
      if (download >= 100) return [138, 43, 226, 255];
      if (download >= 50) return [59, 130, 246, 255];
      if (download >= 20) return [34, 197, 94, 255];
      if (download >= 5) return [234, 179, 8, 255];
      return [239, 68, 68, 255];
    },
    getRadius: 6,
    radiusMinPixels: 4,
    radiusMaxPixels: 8,
    stroked: true,
    lineWidthMinPixels: 2,
    getLineColor: [255, 255, 255, 180],
    filled: true,
    pickable: true,
    onClick: info => {
      if (info.object) {
        setClickedSpeedtest(info.object);
        setClickedPoint(null);
      }
    },
    updateTriggers: {
      getFillColor: [speedtestData]
    }
  });
}, [speedtestData, visibleLayers.speedtest, speedtestVisualization]);

// ===== SPEEDTEST HEATMAP =====
const speedtestHeatmapLayer = useMemo(() => {
  if (!visibleLayers.speedtest || !speedtestData || speedtestData.length === 0 || speedtestVisualization !== 'heatmap') return null;
  
  const speedColorRange = [
    [239, 68, 68, 200],
    [234, 179, 8, 210],
    [34, 197, 94, 220],
    [59, 130, 246, 230],
    [138, 43, 226, 240]
  ];
  
  return new HeatmapLayer({
    id: 'speedtest-heatmap-layer',
    data: speedtestData,
    getPosition: d => d.position,
    getWeight: d => 1,
    radiusPixels: 50,
    intensity: 1,
    threshold: 0.05,
    colorRange: speedColorRange,
    aggregation: 'SUM'
  });
}, [speedtestData, visibleLayers.speedtest, speedtestVisualization]);

// ===== SPEEDTEST SŁUPKI =====
const speedtestBarsLayer = useMemo(() => {
  if (!visibleLayers.speedtest || !speedtestData || speedtestData.length === 0 || speedtestVisualization !== 'bars') return null;
  
  return new ColumnLayer({
    id: 'speedtest-bars-layer',
    data: speedtestData,
    diskResolution: 12,
    radius: 7,
    extruded: true,
    pickable: true,
    elevationScale: 1.8,
    getPosition: d => d.position,
    getFillColor: d => {
      let value;
      if (speedtestBarType === 'download') {
        value = d.downloadSpeed || 0;
        if (value >= 100) return [138, 43, 226, 255];
        if (value >= 50) return [59, 130, 246, 255];
        if (value >= 20) return [34, 197, 94, 255];
        if (value >= 5) return [234, 179, 8, 255];
        return [239, 68, 68, 255];
      } else if (speedtestBarType === 'upload') {
        value = d.uploadSpeed || 0;
        if (value >= 50) return [147, 51, 234, 255];
        if (value >= 25) return [79, 70, 229, 255];
        if (value >= 10) return [59, 130, 246, 255];
        if (value >= 2) return [234, 179, 8, 255];
        return [239, 68, 68, 255];
      } else {
        value = d.ping || 0;
        if (value <= 20) return [34, 197, 94, 255];
        if (value <= 50) return [234, 179, 8, 255];
        if (value <= 100) return [249, 115, 22, 255];
        return [239, 68, 68, 255];
      }
    },
    getLineColor: [0, 0, 0, 80],
    getElevation: d => {
      let value;
      let maxValue;
      
      if (speedtestBarType === 'download') {
        value = d.downloadSpeed || 0;
        maxValue = 200;
      } else if (speedtestBarType === 'upload') {
        value = d.uploadSpeed || 0;
        maxValue = 100;
      } else {
        value = d.ping || 0;
        value = Math.max(0, 200 - value);
        maxValue = 200;
      }
      
      return Math.max(10, (value / maxValue) * 800);
    },
    onClick: info => {
      if (info.object) {
        setClickedSpeedtest(info.object);
        setClickedPoint(null);
      }
    },
    updateTriggers: {
      getFillColor: [speedtestData, speedtestBarType],
      getElevation: [speedtestData, speedtestBarType]
    }
  });
}, [speedtestData, visibleLayers.speedtest, speedtestVisualization, speedtestBarType]);

// ===== LISTA WARSTW =====
const layers = [
  btsLayer,
  connectionLines,
  // Telemetria - warunkowe warstwy
  telemetryVisualization === 'points' && pointsGlowLayer,
  telemetryVisualization === 'points' && pointsCoreLayer,
  telemetryVisualization === 'heatmap' && telemetryHeatmapLayer,
  telemetryVisualization === 'hexagons' && telemetryHexagonLayer,
  telemetryVisualization === 'bars' && telemetryBarsLayer,
  // Speedtest - warunkowe warstwy
  speedtestVisualization === 'points' && speedtestGlowLayer,
  speedtestVisualization === 'points' && speedtestCoreLayer,
  speedtestVisualization === 'heatmap' && speedtestHeatmapLayer,
  speedtestVisualization === 'bars' && speedtestBarsLayer
].filter(Boolean);

  // Funkcja do określenia koloru sygnału
  const getSignalColor = (signal) => {
    if (signal >= -70) return '#22c55e';
    if (signal >= -85) return '#eab308';
    if (signal >= -100) return '#f97316';
    return '#ef4444';
  };
  
  // Funkcja do określenia opisu sygnału
  const getSignalLabel = (signal) => {
    if (signal >= -70) return 'Świetny';
    if (signal >= -85) return 'Dobry';
    if (signal >= -100) return 'Średni';
    return 'Słaby';
  };

  return (
    <div className="map-view">
<DeckGL
  viewState={viewState}
  onViewStateChange={({ viewState }) => setViewState(viewState)}
  controller={true}
  layers={layers}
  getCursor={({ isDragging, isHovering }) => {
    if (isDragging) return 'grabbing';
    if (isHovering) return 'pointer';
    return 'grab';
  }}
  getTooltip={({ object, layer }) => {
  if (!object) return null;
  
  // DEFINICJE ZMIENNYCH TOOLTIPA
  const tooltipBg = isLightMap ? 'rgba(255, 255, 255, 0.98)' : 'rgba(30, 41, 59, 0.98)';
  const tooltipBorder = isLightMap ? '#e2e8f0' : '#334155';
  const tooltipTextPrimary = isLightMap ? '#0f172a' : '#f1f5f9';
  const tooltipTextSecondary = isLightMap ? '#64748b' : '#94a3b8';
  
  // Tooltip dla speedtestu
if (layer.id === 'speedtest-core-layer' || layer.id === 'speedtest-bars-layer') {
  const download = object.downloadSpeed || 0;
  const upload = object.uploadSpeed || 0;
  const ping = object.ping || 0;
  
  const getSpeedColor = (speed) => {
    if (speed >= 100) return '#8a2be2';
    if (speed >= 50) return '#3b82f6';
    if (speed >= 20) return '#22c55e';
    if (speed >= 5) return '#eab308';
    return '#ef4444';
  };
  
  return {
    html: `
      <div style="
        background: ${tooltipBg};
        border: 2px solid ${tooltipBorder};
        border-radius: 12px;
        padding: 12px;
        min-width: 200px;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
      ">
        <div style="font-weight: 700; color: ${tooltipTextPrimary}; font-size: 16px; margin-bottom: 8px;">
          ⚡ Speedtest
        </div>
        <div style="color: ${tooltipTextPrimary}; font-size: 13px; margin-bottom: 6px;">
          <strong>${object.operator || 'N/A'}</strong>
        </div>
        <div style="
          background: ${getSpeedColor(download)}20;
          padding: 8px;
          border-radius: 8px;
          border-left: 3px solid ${getSpeedColor(download)};
          margin-bottom: 6px;
        ">
          <div style="color: ${tooltipTextPrimary}; font-size: 12px;">
            Download: <strong>${download.toFixed(2)} Mbps</strong>
          </div>
          <div style="color: ${tooltipTextPrimary}; font-size: 12px;">
            Upload: <strong>${upload.toFixed(2)} Mbps</strong>
          </div>
          <div style="color: ${tooltipTextPrimary}; font-size: 12px;">
            Ping: <strong>${ping.toFixed(0)} ms</strong>
          </div>
        </div>
        <div style="color: ${tooltipTextSecondary}; font-size: 11px;">
          Kliknij aby zobaczyć szczegóły
        </div>
      </div>
    `,
    style: {
      backgroundColor: 'transparent',
      padding: '0',
      border: 'none'
    }
  };
}
  
  // Jeśli to antena BTS
  if (layer.id === 'bts-layer') {
    return {
      html: `
        <div style="
          background: ${tooltipBg};
          border: 2px solid ${tooltipBorder};
          border-radius: 12px;
          padding: 12px;
          min-width: 220px;
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
        ">
          <div style="font-weight: 700; color: ${tooltipTextPrimary}; font-size: 16px; margin-bottom: 8px;">
            Stacja BTS
          </div>
          <div style="color: ${tooltipTextPrimary}; font-size: 13px; margin-bottom: 4px;">
            <strong>${object.siecId}</strong> - ${object.standard}
          </div>
          <div style="color: ${tooltipTextSecondary}; font-size: 12px; margin-bottom: 4px;">
            ${object.lokalizacja}
          </div>
          <div style="color: ${tooltipTextSecondary}; font-size: 12px; margin-bottom: 8px;">
            ${object.miejscowosc}
          </div>
          <div style="
            background: rgba(59, 130, 246, 0.1);
            padding: 6px;
            border-radius: 6px;
            color: #3b82f6;
            font-size: 12px;
            font-weight: 600;
          ">
             ${object.measurementCount} pomiarów
          </div>
          <div style="color: ${tooltipTextSecondary}; font-size: 11px; margin-top: 6px;">
            Kliknij aby zobaczyć pomiary
          </div>
        </div>
      `,
      style: {
        backgroundColor: 'transparent',
        padding: '0',
        border: 'none'
      }
    };
  }
  
  // Tooltip dla hexagonów
if (layer.id === 'telemetry-hexagon-layer' && object.points) {
  const avgSignal = object.points.reduce((sum, p) => sum + (p.source?.signal || p.signal || 0), 0) / object.points.length;
  return {
    html: `
      <div style="
        background: ${tooltipBg};
        border: 2px solid ${tooltipBorder};
        border-radius: 12px;
        padding: 12px;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
      ">
        <div style="font-weight: 700; font-size: 14px; color: ${tooltipTextPrimary}; margin-bottom: 8px;">
          Rejon (${object.points.length} pomiarów)
        </div>
        <div style="font-size: 13px; color: ${tooltipTextSecondary};">
          <strong style="color: ${tooltipTextPrimary};">Średni sygnał:</strong> 
          <span style="color: ${getSignalColor(avgSignal)}; font-weight: 700;">
            ${avgSignal.toFixed(1)} dBm
          </span>
        </div>
      </div>
    `,
    style: {
      backgroundColor: 'transparent',
      padding: '0',
      border: 'none'
    }
  };
}
  
  // Tooltip dla punktów telemetrii (points-core-layer lub telemetry-bars-layer)
  if (layer.id === 'points-core-layer' || layer.id === 'telemetry-bars-layer') {
    return {
      html: `
        <div style="
          background: ${tooltipBg};
          border: 2px solid ${tooltipBorder};
          border-radius: 12px;
          padding: 12px;
          min-width: 180px;
          max-width: calc(100vw - 32px);
          box-shadow: 0 8px 24px rgba(0, 0, 0, ${isLightMap ? '0.15' : '0.6'});
          backdrop-filter: blur(12px);
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
        ">
          <div style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 8px;
            padding-bottom: 8px;
            border-bottom: 2px solid ${tooltipBorder};
          ">
            <span style="
              font-weight: 700;
              color: ${tooltipTextPrimary};
              font-size: 15px;
              text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
            ">${object.operator || 'N/A'}</span>
            <span style="
              background: #3b82f6;
              color: white;
              padding: 3px 10px;
              border-radius: 8px;
              font-size: 12px;
              font-weight: 700;
              box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
            ">${object.networkType || 'N/A'}</span>
          </div>
          
          <div style="
            display: flex;
            align-items: center;
            gap: 8px;
            margin: 16px 0;
            padding: 8px;
            background: ${isLightMap ? 'rgba(0, 0, 0, 0.05)' : 'rgba(0, 0, 0, 0.3)'};
            border-radius: 8px;
            color: ${getSignalColor(object.signal)};
          ">
            <strong style="
              font-size: 20px;
              font-weight: 800;
              text-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
            ">${object.signal} dBm</strong>
            <span style="
              background: ${getSignalColor(object.signal)};
              color: white;
              padding: 3px 10px;
              border-radius: 8px;
              font-size: 11px;
              font-weight: 700;
              box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
            ">${getSignalLabel(object.signal)}</span>
          </div>
          
          <div style="
            color: ${tooltipTextSecondary};
            font-size: 13px;
            font-family: 'Courier New', monospace;
            font-weight: 600;
            margin-top: 8px;
            background: ${isLightMap ? 'rgba(0, 0, 0, 0.05)' : 'rgba(0, 0, 0, 0.3)'};
            padding: 4px 8px;
            border-radius: 4px;
          ">
            ${object.latitude.toFixed(6)}, ${object.longitude.toFixed(6)}
          </div>
          
          <div style="
            color: ${tooltipTextSecondary};
            font-size: 12px;
            margin-top: 8px;
            font-weight: 500;
            display: flex;
            align-items: center;
            gap: 4px;
          ">
            ${new Date(object.sendTime).toLocaleString('pl-PL')}
          </div>
        </div>
      `,
      style: {
        backgroundColor: 'transparent',
        padding: '0',
        border: 'none'
      }
    };
  }
  
  return null;
}}
      >
        <Map
          mapStyle={MAP_STYLE}
          mapboxAccessToken="MAPTILER" // cokolwiek tutaj byleby dzialalo
        />
      </DeckGL>
      
{/* PANEL SPEEDTESTU */}
{clickedSpeedtest && (
  <div className="details-panel speedtest-details">
    <div className="mobile-handle"></div>
    <button className="close-details" onClick={() => setClickedSpeedtest(null)}>
      ✕
    </button>
    
    <h3> Speedtest</h3>
    
    <div className="detail-section">
      <h4>Podstawowe informacje</h4>
      
      <div className="detail-row">
        <span className="detail-label">Operator</span>
        <span className="detail-value">{clickedSpeedtest.operator || 'N/A'}</span>
      </div>
    </div>

    <div className="detail-section">
      <h4>Prędkości</h4>
      
      <div className="detail-row">
        <span className="detail-label">Download</span>
        <span className="detail-value signal-value">
          {(clickedSpeedtest.downloadSpeed || 0).toFixed(2)} Mbps
        </span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">Upload</span>
        <span className="detail-value">
          {(clickedSpeedtest.uploadSpeed || 0).toFixed(2)} Mbps
        </span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">Ping</span>
        <span className="detail-value">
          {(clickedSpeedtest.ping || 0).toFixed(0)} ms
        </span>
      </div>
      
      {clickedSpeedtest.jitter !== undefined && (
        <div className="detail-row">
          <span className="detail-label">Jitter</span>
          <span className="detail-value">
            {clickedSpeedtest.jitter.toFixed(1)} ms
          </span>
        </div>
      )}
    </div>

    <div className="detail-section">
      <h4>Lokalizacja i czas</h4>
      
      <div className="detail-row">
        <span className="detail-label">Współrzędne</span>
        <span className="detail-value coords">
          {clickedSpeedtest.latitude.toFixed(6)}, {clickedSpeedtest.longitude.toFixed(6)}
        </span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">Data testu</span>
        <span className="detail-value">
          {new Date(clickedSpeedtest.timestamp).toLocaleString('pl-PL')}
        </span>
      </div>
    </div>
  </div>
)}

      {/* Panel szczegółów BTS */}
{selectedBts && (
  <div className={`details-panel bts-details ${clickedPoint ? 'with-point' : ''}`}>
    <div className="mobile-handle"></div>
    <button 
      className="close-details"
      onClick={() => setSelectedBts(null)}
    >
      ✕
    </button>
    
    <h3> Stacja BTS</h3>
    
    <div className="detail-section">
      <h4>Podstawowe informacje</h4>
      
      <div className="detail-row">
        <span className="detail-label">Operator</span>
        <span className="detail-value">{selectedBts.siecId}</span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">ID Stacji</span>
        <span className="detail-value id">{selectedBts.stationId}</span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">BTS ID</span>
        <span className="detail-value id">{selectedBts.btsid}</span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">eNB ID</span>
        <span className="detail-value">{selectedBts.enbi}</span>
      </div>
    </div>
    
    <div className="detail-section">
      <h4>Lokalizacja</h4>
      
      <div className="detail-row">
        <span className="detail-label">Adres</span>
        <span className="detail-value">{selectedBts.lokalizacja}</span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">Miejscowość</span>
        <span className="detail-value">{selectedBts.miejscowosc}</span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">Województwo</span>
        <span className="detail-value">{selectedBts.wojewodztwoId}</span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">Współrzędne</span>
        <span className="detail-value coords">
          {selectedBts.lat.toFixed(6)}, {selectedBts.lon.toFixed(6)}
        </span>
      </div>
    </div>
    
    <div className="detail-section">
      <h4>Parametry techniczne</h4>
      
      <div className="detail-row">
        <span className="detail-label">Standard</span>
        <span className="detail-value">{selectedBts.standard}</span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">Pasmo</span>
        <span className="detail-value">{selectedBts.pasmo} MHz</span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">Duplex</span>
        <span className="detail-value">{selectedBts.duplex}</span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">Carrier</span>
        <span className="detail-value">{selectedBts.carrier}</span>
      </div>
    </div>
    
    <div className="detail-section">
      <h4>Statystyki</h4>
      
      <div className="detail-row">
        <span className="detail-label">Liczba pomiarów</span>
        <span className="detail-value" style={{color: '#3b82f6', fontWeight: 700}}>
          {selectedBts.measurementCount}
        </span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">Ostatnia aktualizacja</span>
        <span className="detail-value">
          {new Date(selectedBts.aktualizacja).toLocaleDateString('pl-PL')}
        </span>
      </div>
    </div>
    
    {selectedBts.uwagi && (
      <div className="detail-section">
        <h4>Uwagi</h4>
        <p style={{margin: 0, color: 'var(--text-secondary)'}}>{selectedBts.uwagi}</p>
      </div>
    )}
  </div>
)}
      
      {/* Panel szczegółów po kliknięciu */}
{clickedPoint && (
    <div className={`details-panel ${selectedBts ? 'with-bts' : ''}`}>
      <div className="mobile-handle"></div>
    <button 
      className="close-details"
      onClick={() => setClickedPoint(null)}
    >
      ✕
    </button>
    
    <h3>Szczegóły pomiaru</h3>
    
    {/* Podstawowe info */}
    <div className="detail-section">
      <h4>Podstawowe informacje</h4>
      
      <div className="detail-row">
        <span className="detail-label">Operator</span>
        <span className="detail-value">{clickedPoint.operator}</span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">Typ sieci</span>
        <span className="detail-value">
          {clickedPoint.networkType}
          {clickedPoint.nrMode && ` (${clickedPoint.nrMode})`}
        </span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">RAT</span>
        <span className="detail-value">{clickedPoint.rat || 'N/A'}</span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">Pasmo</span>
        <span className="detail-value">{clickedPoint.band || 'N/A'}</span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">ARFCN</span>
        <span className="detail-value">{clickedPoint.arfcn || 'N/A'}</span>
      </div>
    </div>
    
    {/* Parametry sygnału */}
    <div className="detail-section">
      <h4>Parametry sygnału</h4>
      
      <div className="detail-row">
        <span className="detail-label">Siła sygnału</span>
        <span 
          className="detail-value signal-value"
          style={{ color: getSignalColor(clickedPoint.signal) }}
        >
          <span>{clickedPoint.signal} dBm</span>
          <span className="signal-badge">({getSignalLabel(clickedPoint.signal)})</span>
        </span>
      </div>
      
      {clickedPoint.rsrp && (
        <div className="detail-row">
          <span className="detail-label">RSRP</span>
          <span className="detail-value">{clickedPoint.rsrp} dBm</span>
        </div>
      )}
      
      {clickedPoint.rsrq && (
        <div className="detail-row">
          <span className="detail-label">RSRQ</span>
          <span className="detail-value">{clickedPoint.rsrq} dB</span>
        </div>
      )}
      
      {clickedPoint.sinr !== undefined && (
        <div className="detail-row">
          <span className="detail-label">SINR</span>
          <span className="detail-value">{clickedPoint.sinr} dB</span>
        </div>
      )}
      
      {clickedPoint.rssi && (
        <div className="detail-row">
          <span className="detail-label">RSSI</span>
          <span className="detail-value">{clickedPoint.rssi} dBm</span>
        </div>
      )}
    </div>
    
    {/* Identyfikatory komórki */}
<div className="detail-section">
  <h4>Identyfikatory komórki</h4>
  
  {clickedPoint.pci !== undefined && clickedPoint.pci !== null && (
    <div className="detail-row">
      <span className="detail-label">PCI</span>
      <span className="detail-value">{clickedPoint.pci}</span>
    </div>
  )}
  
  {clickedPoint.eci && (
    <div className="detail-row">
      <span className="detail-label">ECI</span>
      <span className="detail-value id">{clickedPoint.eci}</span>
    </div>
  )}
  
  {clickedPoint.nci && (
    <div className="detail-row">
      <span className="detail-label">NCI</span>
      <span className="detail-value id">{clickedPoint.nci}</span>
    </div>
  )}
  
  {clickedPoint.enb && (
    <div className="detail-row">
      <span className="detail-label">eNB</span>
      <span className="detail-value">{clickedPoint.enb}</span>
    </div>
  )}
  
  {clickedPoint.cellId && (
    <div className="detail-row">
      <span className="detail-label">Cell ID</span>
      <span className="detail-value id">{clickedPoint.cellId}</span>
    </div>
  )}
  
  {clickedPoint.sectorId !== undefined && clickedPoint.sectorId !== null && (
    <div className="detail-row">
      <span className="detail-label">Sektor</span>
      <span className="detail-value">{clickedPoint.sectorId}</span>
    </div>
  )}
  
  {clickedPoint.tac && (
    <div className="detail-row">
      <span className="detail-label">TAC</span>
      <span className="detail-value">{clickedPoint.tac}</span>
    </div>
  )}
  
  {clickedPoint.lac && (
    <div className="detail-row">
      <span className="detail-label">LAC</span>
      <span className="detail-value">{clickedPoint.lac}</span>
    </div>
  )}
  
  {clickedPoint.timingAdvance !== undefined && clickedPoint.timingAdvance !== null && (
    <div className="detail-row">
      <span className="detail-label">Timing Advance</span>
      <span className="detail-value">{clickedPoint.timingAdvance}</span>
    </div>
  )}
</div>
    
    {/* Powiązana stacja BTS */}
{clickedPoint.relatedBts && (
  <div className="detail-section bts-section">
    <h4>Powiązana stacja BTS</h4>
    
    <div className="detail-row">
      <span className="detail-label">ID stacji</span>
      <span className="detail-value id">{clickedPoint.relatedBts.stationId}</span>
    </div>
    
    <div className="detail-row">
      <span className="detail-label">BTS ID</span>
      <span className="detail-value id">{clickedPoint.relatedBts.btsid}</span>
    </div>
    
    <div className="detail-row">
      <span className="detail-label">eNB ID</span>
      <span className="detail-value">{clickedPoint.relatedBts.enbi}</span>
    </div>
    
    <div className="detail-row">
      <span className="detail-label">Lokalizacja</span>
      <span className="detail-value">{clickedPoint.relatedBts.lokalizacja}</span>
    </div>
    
    <div className="detail-row">
      <span className="detail-label">Miejscowość</span>
      <span className="detail-value">{clickedPoint.relatedBts.miejscowosc}, {clickedPoint.relatedBts.wojewodztwoId}</span>
    </div>
    
    <div className="detail-row">
      <span className="detail-label">Standard</span>
      <span className="detail-value">{clickedPoint.relatedBts.standard}</span>
    </div>
    
    <div className="detail-row">
      <span className="detail-label">Pasmo</span>
      <span className="detail-value">{clickedPoint.relatedBts.pasmo} MHz ({clickedPoint.relatedBts.duplex})</span>
    </div>
    
    <div className="detail-row">
      <span className="detail-label">Carrier</span>
      <span className="detail-value">{clickedPoint.relatedBts.carrier}</span>
    </div>
    
    {clickedPoint.relatedBts.uwagi && (
      <div className="detail-row">
        <span className="detail-label">Uwagi</span>
        <span className="detail-value">{clickedPoint.relatedBts.uwagi}</span>
      </div>
    )}
    
    <div className="detail-row">
      <span className="detail-label">Współrzędne BTS</span>
      <span className="detail-value coords">
        {clickedPoint.relatedBts.lat.toFixed(6)}, {clickedPoint.relatedBts.lon.toFixed(6)}
      </span>
    </div>
    
    <div className="detail-row">
      <span className="detail-label">Aktualizacja</span>
      <span className="detail-value">
        {new Date(clickedPoint.relatedBts.aktualizacja).toLocaleString('pl-PL')}
      </span>
    </div>
    
    <button 
  className="show-bts-button"
  onClick={() => {
    setSelectedBts(clickedPoint.relatedBts);
  }}
>
  📍 Pokaż stację na mapie
</button>
  </div>
)}
    
    {/* Lokalizacja i czas */}
    <div className="detail-section">
      <h4>Lokalizacja i czas</h4>
      
      <div className="detail-row">
        <span className="detail-label">Współrzędne</span>
        <span className="detail-value coords">
          {clickedPoint.latitude.toFixed(6)}, {clickedPoint.longitude.toFixed(6)}
        </span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">Data pomiaru</span>
        <span className="detail-value">
          {new Date(clickedPoint.sendTime).toLocaleString('pl-PL')}
        </span>
      </div>
      
      <div className="detail-row">
        <span className="detail-label">ID pomiaru</span>
        <span className="detail-value id">{clickedPoint.id}</span>
      </div>
    </div>
  </div>
)}
    </div>
  );
}

export default MapView;