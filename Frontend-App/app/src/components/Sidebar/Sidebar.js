import React, { useMemo, useState } from 'react';
import './Sidebar.css';

function Sidebar({ 
  isOpen, 
  filters, 
  onFiltersChange, 
  data, 
  loading, 
  speedtestData = [], 
  onExport,
  isAnimationEnabled = false,
  isPlaying = false,
  animationTime = null,
  animationSpeed = 1,
  minAnimationTime = null,
  maxAnimationTime = null,
  onAnimationToggle = () => {},
  onPlayPause = () => {},
  onTimeChange = () => {},
  onSpeedChange = () => {}
}) {
  /* =========================
     LOKALNY STAN UI (ACCORDION)
     ========================= */
  const [layersExpanded, setLayersExpanded] = useState(false);
  const [telemetryExpanded, setTelemetryExpanded] = useState(false);
  const [speedtestExpanded, setSpeedtestExpanded] = useState(false);
  const [animationExpanded, setAnimationExpanded] = useState(false);
  const [exportExpanded, setExportExpanded] = useState(false);

  /* =========================
     DANE PODSTAWOWE (telemetria)
     ========================= */
  const operators = useMemo(() => {
    return [...new Set(data.map(d => d.operator))].filter(Boolean);
  }, [data]);

  const networkTypes = useMemo(() => {
    return [...new Set(data.map(d => d.networkType))].filter(Boolean);
  }, [data]);

  /* =========================
     SPEEDTEST – DANE
     ========================= */
  const speedtestOperators = useMemo(() => {
    return [...new Set((speedtestData || []).map(d => d.operator))].filter(Boolean);
  }, [speedtestData]);

  // LOCAL STATE - Short code search
  const [shortCodeInput, setShortCodeInput] = useState(filters.shortCodeFilter || '');
  const [shortCodeInputSpeedtest, setShortCodeInputSpeedtest] = useState(filters.speedtestShortCodeFilter || '');

  /* =========================
     HANDLERY – TELEMETRIA
     ========================= */
  const toggleOperator = (operator) => {
    const current = filters.operators;
    const updated = current.includes(operator)
      ? current.filter(op => op !== operator)
      : [...current, operator];
    onFiltersChange({ ...filters, operators: updated });
  };

  const toggleNetworkType = (type) => {
    const current = filters.networkTypes;
    const updated = current.includes(type)
      ? current.filter(t => t !== type)
      : [...current, type];
    onFiltersChange({ ...filters, networkTypes: updated });
  };

  const handleSignalChange = (index, value) => {
    const newRange = [...filters.signalRange];
    newRange[index] = parseInt(value);
    onFiltersChange({ ...filters, signalRange: newRange });
  };

  /* =========================
     HANDLERY – WARSTWY
     ========================= */
  const toggleLayer = (layer) => {
    const current = filters.visibleLayers || { telemetry: true, bts: true, speedtest: true };
    onFiltersChange({
      ...filters,
      visibleLayers: {
        ...current,
        [layer]: !current[layer]
      }
    });
  };

  /* =========================
     HANDLERY – SPEEDTEST
     ========================= */
  const toggleSpeedtestOperator = (operator) => {
    const current = filters.speedtestOperators || [];
    const updated = current.includes(operator)
      ? current.filter(op => op !== operator)
      : [...current, operator];

    onFiltersChange({
      ...filters,
      speedtestOperators: updated
    });
  };

  const handleSpeedChange = (index, value) => {
    const range = filters.speedRange || [0, 500];
    const updated = [...range];
    updated[index] = parseInt(value) || 0;

    onFiltersChange({
      ...filters,
      speedRange: updated
    });
  };

  const handleUploadChange = (index, value) => {
    const range = filters.speedtestUploadRange || [0, 100];
    const updated = [...range];
    updated[index] = parseInt(value) || 0;

    onFiltersChange({
      ...filters,
      speedtestUploadRange: updated
    });
  };

  const handlePingChange = (index, value) => {
    const range = filters.speedtestPingRange || [0, 200];
    const updated = [...range];
    updated[index] = parseInt(value) || 0;

    onFiltersChange({
      ...filters,
      speedtestPingRange: updated
    });
  };

  /* =========================
     NOWE HANDLERY – WIZUALIZACJE
     ========================= */
  // Telemetria - typ wizualizacji
  const handleTelemetryVisualizationChange = (vizType) => {
    onFiltersChange({
      ...filters,
      telemetryVisualization: vizType
    });
  };

  // Speedtest - typ wizualizacji
  const handleSpeedtestVisualizationChange = (vizType) => {
    onFiltersChange({
      ...filters,
      speedtestVisualization: vizType
    });
  };

  // Speedtest - typ słupka (download/upload/ping)
  const handleSpeedtestBarTypeChange = (barType) => {
    onFiltersChange({
      ...filters,
      speedtestBarType: barType
    });
  };

  // Toggle 3D mode
  const toggle3DMode = () => {
    onFiltersChange({
      ...filters,
      is3DMode: !filters.is3DMode
    });
  };

  /* =========================
     RESET
     ========================= */
  const resetFilters = () => {
    setShortCodeInput('');
    setShortCodeInputSpeedtest('');
    onFiltersChange({
      operators: [],
      signalRange: [-170, -20],
      networkTypes: [],
      shortCodeFilter: '',
      speedtestShortCodeFilter: '',
      dateRange: { start: null, end: null },
      displayLimit: 100000,
      timeRange: null,
      speedtestOperators: [],
      speedRange: [0, 500],
      speedtestUploadRange: [0, 100],
      speedtestPingRange: [0, 200],
      visibleLayers: {
        telemetry: true,
        bts: true,
        speedtest: true
      },
      telemetryVisualization: 'points',
      speedtestVisualization: 'points',
      speedtestBarType: 'download',
      is3DMode: false,
      showOnlyUnmatched: false
    });
  };

  /* =========================
     KOLORY OPERATORÓW
     ========================= */
  const operatorColors = {
    'Play': 'var(--color-play)',
    'Orange': 'var(--color-orange)',
    'T-Mobile': 'var(--color-tmobile)',
    'Plus': 'var(--color-plus)'
  };

  const btsCount = useMemo(() => {
    return [...new Set(data.map(d => d.enb))].filter(Boolean).length;
  }, [data]);

  const unmatchedCount = useMemo(() => {
    return data.filter(d => !d.relatedBts).length;
  }, [data]);

  // Export local UI state
  const [exportTelemetry, setExportTelemetry] = useState(true);
  const [exportSpeedtest, setExportSpeedtest] = useState(false);

  return (
    <aside className={`sidebar ${isOpen ? 'open' : 'closed'}`}>
      <div className="sidebar-content">
        <div className="sidebar-header">
          <h2>Filtry</h2>
          <button className="reset-button" onClick={resetFilters}>Resetuj</button>
        </div>

        {loading && (
          <div className="sidebar-loading">
            <div className="small-spinner"></div>
            <p>Ładowanie opcji...</p>
          </div>
        )}

        {!loading && (
          <>


            {/* =========================
               WARSTWA DANYCH (ACCORDION)
               ========================= */}
            <div className="filter-section expandable">
              <h3
                className="expandable-header"
                onClick={() => setLayersExpanded(v => !v)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') setLayersExpanded(v => !v);
                }}
              >
                <span className={`expand-icon ${layersExpanded ? 'expanded' : ''}`}>▶</span>
                Warstwa danych
              </h3>

              {layersExpanded && (
                <div className="expandable-content">
                  <div className="filter-options">
                    <label className="checkbox-label layer-checkbox">
                      <input
                        type="checkbox"
                        checked={filters.showOnlyUnmatched || false}
                        onChange={() => onFiltersChange({ ...filters, showOnlyUnmatched: !filters.showOnlyUnmatched })}
                      />
                      <span className="checkbox-custom"></span>
                      <div className="layer-info">
                        <span className="layer-name">Punkty bez stacji BTS</span>
                        <span className="layer-count">{unmatchedCount} pkt</span>
                      </div>
                    </label>

                    <label className="checkbox-label layer-checkbox">
                      <input
                        type="checkbox"
                        checked={(filters.visibleLayers || {}).telemetry !== false}
                        onChange={() => toggleLayer('telemetry')}
                      />
                      <span className="checkbox-custom"></span>
                      <div className="layer-info">
                        <span className="layer-name">Punkty pomiarowe</span>
                        <span className="layer-count">{data.length} pkt</span>
                      </div>
                    </label>

                    <label className="checkbox-label layer-checkbox">
                      <input
                        type="checkbox"
                        checked={(filters.visibleLayers || {}).bts !== false}
                        onChange={() => toggleLayer('bts')}
                      />
                      <span className="checkbox-custom"></span>
                      <div className="layer-info">
                        <span className="layer-name">Stacje BTS</span>
                        <span className="layer-count">{btsCount} stacji</span>
                      </div>
                    </label>

                    <label className="checkbox-label layer-checkbox">
                      <input
                        type="checkbox"
                        checked={(filters.visibleLayers || {}).speedtest !== false}
                        onChange={() => toggleLayer('speedtest')}
                      />
                      <span className="checkbox-custom"></span>
                      <div className="layer-info">
                        <span className="layer-name">Speedtesty</span>
                        <span className="layer-count">{speedtestData?.length || 0} testów</span>
                      </div>
                    </label>
                  </div>
                </div>
              )}
            </div>

            {/* =========================
               SPEEDTEST (ACCORDION)
               ========================= */}
            <div className="filter-section expandable">
              <h3
                className="expandable-header"
                onClick={() => setSpeedtestExpanded(v => !v)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') setSpeedtestExpanded(v => !v);
                }}
              >
                <span className={`expand-icon ${speedtestExpanded ? 'expanded' : ''}`}>▶</span>
                Speedtest
              </h3>

              {speedtestExpanded && (
                <div className="expandable-content">
                  {/* TYP WIZUALIZACJI */}
                  <div className="filter-subsection">
                    <h4>Typ wizualizacji</h4>
                    <div className="visualization-options">
                      <button
                        className={`viz-button ${(filters.speedtestVisualization || 'points') === 'points' ? 'active' : ''}`}
                        onClick={() => handleSpeedtestVisualizationChange('points')}
                        title="Punkty"
                      >
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                          <circle cx="12" cy="12" r="8"/>
                        </svg>
                        <span>Punkty</span>
                      </button>

                      <button
                        className={`viz-button ${filters.speedtestVisualization === 'heatmap' ? 'active' : ''}`}
                        onClick={() => handleSpeedtestVisualizationChange('heatmap')}
                        title="Heatmapa"
                      >
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10 10-4.5 10-10S17.5 2 12 2z"/>
                          <circle cx="12" cy="12" r="6"/>
                          <circle cx="12" cy="12" r="2"/>
                        </svg>
                        <span>Heatmapa</span>
                      </button>

                      <button
                        className={`viz-button ${filters.speedtestVisualization === 'bars' ? 'active' : ''}`}
                        onClick={() => handleSpeedtestVisualizationChange('bars')}
                        title="Słupki"
                      >
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <rect x="4" y="10" width="4" height="10"/>
                          <rect x="10" y="6" width="4" height="14"/>
                          <rect x="16" y="12" width="4" height="8"/>
                        </svg>
                        <span>Słupki</span>
                      </button>
                    </div>
                  </div>

                  {/* TYP SŁUPKA (tylko gdy bars) */}
                  {filters.speedtestVisualization === 'bars' && (
                    <div className="filter-subsection">
                      <h4>Wartość słupka</h4>
                      <div className="bar-type-options">
                        <button
                          className={`bar-type-button ${(filters.speedtestBarType || 'download') === 'download' ? 'active' : ''}`}
                          onClick={() => handleSpeedtestBarTypeChange('download')}
                        >
                          Download
                        </button>
                        <button
                          className={`bar-type-button ${filters.speedtestBarType === 'upload' ? 'active' : ''}`}
                          onClick={() => handleSpeedtestBarTypeChange('upload')}
                        >
                          Upload
                        </button>
                        <button
                          className={`bar-type-button ${filters.speedtestBarType === 'ping' ? 'active' : ''}`}
                          onClick={() => handleSpeedtestBarTypeChange('ping')}
                        >
                          Ping
                        </button>
                      </div>
                    </div>
                  )}

                  {/* Operatorzy speedtest */}
                  <div className="filter-subsection">
                    <h4>Operator</h4>
                    <div className="filter-options">
                      {speedtestOperators.length === 0 ? (
                        <p className="no-data">Brak danych</p>
                      ) : (
                        speedtestOperators.map(operator => (
                          <label key={operator} className="checkbox-label">
                            <input
                              type="checkbox"
                              checked={
                                !filters.speedtestOperators ||
                                filters.speedtestOperators.length === 0 ||
                                filters.speedtestOperators.includes(operator)
                              }
                              onChange={() => toggleSpeedtestOperator(operator)}
                            />
                            <span
                              className="checkbox-custom"
                              style={{ '--operator-color': operatorColors[operator] }}
                            ></span>
                            <span className="operator-name">{operator}</span>
                            <span className="operator-count">
                              {speedtestData.filter(d => d.operator === operator).length}
                            </span>
                          </label>
                        ))
                      )}
                    </div>
                  </div>

                  {/* Zakres prędkości */}
                  <div className="filter-subsection">
                    <h4>Prędkość download (Mbps)</h4>
                    <div className="signal-range">
                      <div className="range-inputs">
                        <div className="range-input-group">
                          <label>Min</label>
                          <input
                            type="number"
                            value={filters.speedRange?.[0] || 0}
                            onChange={(e) => handleSpeedChange(0, e.target.value)}
                            min="0"
                            max="500"
                          />
                        </div>
                        <span className="range-separator">—</span>
                        <div className="range-input-group">
                          <label>Max</label>
                          <input
                            type="number"
                            value={filters.speedRange?.[1] || 500}
                            onChange={(e) => handleSpeedChange(1, e.target.value)}
                            min="0"
                            max="500"
                          />
                        </div>
                      </div>

                      <div className="range-slider">
                        <input
                          type="range"
                          min="0"
                          max="500"
                          value={filters.speedRange?.[0] || 0}
                          onChange={(e) => handleSpeedChange(0, e.target.value)}
                          className="range-min"
                        />
                        <input
                          type="range"
                          min="0"
                          max="500"
                          value={filters.speedRange?.[1] || 500}
                          onChange={(e) => handleSpeedChange(1, e.target.value)}
                          className="range-max"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Zakres upload */}
                  <div className="filter-subsection">
                    <h4>Prędkość upload (Mbps)</h4>
                    <div className="signal-range">
                      <div className="range-inputs">
                        <div className="range-input-group">
                          <label>Min</label>
                          <input
                            type="number"
                            value={filters.speedtestUploadRange?.[0] || 0}
                            onChange={(e) => handleUploadChange(0, e.target.value)}
                            min="0"
                            max="100"
                          />
                        </div>
                        <span className="range-separator">—</span>
                        <div className="range-input-group">
                          <label>Max</label>
                          <input
                            type="number"
                            value={filters.speedtestUploadRange?.[1] || 100}
                            onChange={(e) => handleUploadChange(1, e.target.value)}
                            min="0"
                            max="100"
                          />
                        </div>
                      </div>

                      <div className="range-slider">
                        <input
                          type="range"
                          min="0"
                          max="100"
                          value={filters.speedtestUploadRange?.[0] || 0}
                          onChange={(e) => handleUploadChange(0, e.target.value)}
                          className="range-min"
                        />
                        <input
                          type="range"
                          min="0"
                          max="100"
                          value={filters.speedtestUploadRange?.[1] || 100}
                          onChange={(e) => handleUploadChange(1, e.target.value)}
                          className="range-max"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Zakres ping */}
                  <div className="filter-subsection">
                    <h4>Ping (ms)</h4>
                    <div className="signal-range">
                      <div className="range-inputs">
                        <div className="range-input-group">
                          <label>Min</label>
                          <input
                            type="number"
                            value={filters.speedtestPingRange?.[0] || 0}
                            onChange={(e) => handlePingChange(0, e.target.value)}
                            min="0"
                            max="200"
                          />
                        </div>
                        <span className="range-separator">—</span>
                        <div className="range-input-group">
                          <label>Max</label>
                          <input
                            type="number"
                            value={filters.speedtestPingRange?.[1] || 200}
                            onChange={(e) => handlePingChange(1, e.target.value)}
                            min="0"
                            max="200"
                          />
                        </div>
                      </div>

                      <div className="range-slider">
                        <input
                          type="range"
                          min="0"
                          max="200"
                          value={filters.speedtestPingRange?.[0] || 0}
                          onChange={(e) => handlePingChange(0, e.target.value)}
                          className="range-min"
                        />
                        <input
                          type="range"
                          min="0"
                          max="200"
                          value={filters.speedtestPingRange?.[1] || 200}
                          onChange={(e) => handlePingChange(1, e.target.value)}
                          className="range-max"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Short code (Speedtest) */}
                  <div className="filter-subsection">
                    <h4>Short code (urządzenie)</h4>
                    <div className="limit-input-group">
                      <label htmlFor="short-code-input-speedtest">Wpisz kod:</label>
                      <input
                        id="short-code-input-speedtest"
                        type="text"
                        value={shortCodeInputSpeedtest}
                        onChange={(e) => setShortCodeInputSpeedtest(e.target.value)}
                        placeholder="np. ABCD-1234-XYZZ"
                        onKeyPress={(e) => {
                          if (e.key === 'Enter') {
                            onFiltersChange({ ...filters, speedtestShortCodeFilter: shortCodeInputSpeedtest });
                          }
                        }}
                      />
                    </div>
                    <div style={{ display: 'flex', gap: '6px', marginTop: '6px', alignItems: 'stretch' }}>
                      <button
                        className="time-button"
                        onClick={() => {
                          onFiltersChange({ ...filters, speedtestShortCodeFilter: shortCodeInputSpeedtest });
                        }}
                        style={{ flex: 1, minHeight: 40 }}
                      >
                        Szukaj
                      </button>
                      {shortCodeInputSpeedtest && (
                        <button
                          className="time-button"
                          onClick={() => {
                            setShortCodeInputSpeedtest('');
                            onFiltersChange({ ...filters, speedtestShortCodeFilter: '' });
                          }}
                          style={{ minWidth: 44, minHeight: 40 }}
                        >
                          ✕
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Zakres dat (Speedtest) */}
                  <div className="filter-subsection">
                    <h4>Zakres dat</h4>

                    <div className="date-inputs">
                      <div className="date-input-group">
                        <label htmlFor="speedtest-start-date">Od:</label>
                        <input
                          id="speedtest-start-date"
                          type="datetime-local"
                          value={filters.dateRange?.start || ''}
                          onChange={(e) =>
                            onFiltersChange({
                              ...filters,
                              dateRange: {
                                ...(filters.dateRange || {}),
                                start: e.target.value
                              }
                            })
                          }
                        />
                      </div>

                      <div className="date-input-group">
                        <label htmlFor="speedtest-end-date">Do:</label>
                        <input
                          id="speedtest-end-date"
                          type="datetime-local"
                          value={filters.dateRange?.end || ''}
                          onChange={(e) =>
                            onFiltersChange({
                              ...filters,
                              dateRange: {
                                ...(filters.dateRange || {}),
                                end: e.target.value
                              }
                            })
                          }
                        />
                      </div>
                    </div>

                    {(filters.dateRange?.start || filters.dateRange?.end) && (
                      <button
                        className="clear-dates-button"
                        onClick={() =>
                          onFiltersChange({
                            ...filters,
                            dateRange: { start: null, end: null }
                          })
                        }
                      >
                        ✕ Wyczyść daty
                      </button>
                    )}
                  </div>
                </div>
              )}
            </div>

            {/* =========================
               SYGNAŁY (ACCORDION)
               ========================= */}
            <div className="filter-section expandable">
              <h3
                className="expandable-header"
                onClick={() => setTelemetryExpanded(v => !v)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') setTelemetryExpanded(v => !v);
                }}
              >
                <span className={`expand-icon ${telemetryExpanded ? 'expanded' : ''}`}>▶</span>
                Sygnały
              </h3>

              {telemetryExpanded && (
                <div className="expandable-content">

                  {/* TYP WIZUALIZACJI TELEMETRII */}
                  <div className="filter-subsection">
                    <h4>Typ wizualizacji</h4>
                    <div className="visualization-options">
                      <button
                        className={`viz-button ${(filters.telemetryVisualization || 'points') === 'points' ? 'active' : ''}`}
                        onClick={() => handleTelemetryVisualizationChange('points')}
                        title="Punkty"
                      >
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                          <circle cx="12" cy="12" r="8"/>
                        </svg>
                        <span>Punkty</span>
                      </button>

                      <button
                        className={`viz-button ${filters.telemetryVisualization === 'heatmap' ? 'active' : ''}`}
                        onClick={() => handleTelemetryVisualizationChange('heatmap')}
                        title="Heatmapa"
                      >
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10 10-4.5 10-10S17.5 2 12 2z"/>
                          <circle cx="12" cy="12" r="6"/>
                          <circle cx="12" cy="12" r="2"/>
                        </svg>
                        <span>Heatmapa</span>
                      </button>

                      <button
                        className={`viz-button ${filters.telemetryVisualization === 'hexagons' ? 'active' : ''}`}
                        onClick={() => handleTelemetryVisualizationChange('hexagons')}
                        title="Hexagony"
                      >
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <path d="M12 2l8 5v10l-8 5-8-5V7z"/>
                        </svg>
                        <span>Hexagony</span>
                      </button>

                      <button
                        className={`viz-button ${filters.telemetryVisualization === 'bars' ? 'active' : ''}`}
                        onClick={() => handleTelemetryVisualizationChange('bars')}
                        title="Słupki"
                      >
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <rect x="4" y="10" width="4" height="10"/>
                          <rect x="10" y="6" width="4" height="14"/>
                          <rect x="16" y="12" width="4" height="8"/>
                        </svg>
                        <span>Słupki</span>
                      </button>
                    </div>
                  </div>

                  {/* Operatorzy */}
                  <div className="filter-subsection">
                    <h4>Operator</h4>
                    <div className="filter-options">
                      {operators.length === 0 ? (
                        <p className="no-data">Brak danych</p>
                      ) : (
                        operators.map(operator => (
                          <label key={operator} className="checkbox-label">
                            <input
                              type="checkbox"
                              checked={filters.operators.length === 0 || filters.operators.includes(operator)}
                              onChange={() => toggleOperator(operator)}
                            />
                            <span
                              className="checkbox-custom"
                              style={{ '--operator-color': operatorColors[operator] }}
                            ></span>
                            <span className="operator-name">{operator}</span>
                            <span className="operator-count">{data.filter(d => d.operator === operator).length}</span>
                          </label>
                        ))
                      )}
                    </div>
                  </div>

                  {/* Siła sygnału */}
                  <div className="filter-subsection">
                    <h4>Siła sygnału (dBm)</h4>
                    <div className="signal-range">
                      <div className="range-inputs">
                        <div className="range-input-group">
                          <label>Min</label>
                          <input
                            type="number"
                            value={filters.signalRange[0]}
                            onChange={(e) => handleSignalChange(0, e.target.value)}
                            min="-120"
                            max="-40"
                          />
                        </div>
                        <span className="range-separator">—</span>
                        <div className="range-input-group">
                          <label>Max</label>
                          <input
                            type="number"
                            value={filters.signalRange[1]}
                            onChange={(e) => handleSignalChange(1, e.target.value)}
                            min="-120"
                            max="-40"
                          />
                        </div>
                      </div>

                      <div className="range-slider">
                        <input
                          type="range"
                          min="-120"
                          max="-40"
                          value={filters.signalRange[0]}
                          onChange={(e) => handleSignalChange(0, e.target.value)}
                          className="range-min"
                        />
                        <input
                          type="range"
                          min="-120"
                          max="-40"
                          value={filters.signalRange[1]}
                          onChange={(e) => handleSignalChange(1, e.target.value)}
                          className="range-max"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Typ sieci */}
                  <div className="filter-subsection">
                    <h4>Typ sieci</h4>
                    <div className="filter-options">
                      {networkTypes.length === 0 ? (
                        <p className="no-data">Brak danych</p>
                      ) : (
                        networkTypes.map(type => (
                          <label key={type} className="checkbox-label">
                            <input
                              type="checkbox"
                              checked={filters.networkTypes.length === 0 || filters.networkTypes.includes(type)}
                              onChange={() => toggleNetworkType(type)}
                            />
                            <span className="checkbox-custom"></span>
                            <span className="network-type">{type}</span>
                            <span className="operator-count">{data.filter(d => d.networkType === type).length}</span>
                          </label>
                        ))
                      )}
                    </div>
                  </div>

                  {/* Filtr ilości punktów */}
                  <div className="filter-subsection">
                    <h4>Ilość punktów</h4>

                    <div className="limit-input-group">
                      <label htmlFor="point-limit">Wyświetlane punkty:</label>
                      <input
                        id="point-limit"
                        type="number"
                        min="0"
                        max="100000"
                        step="100"
                        value={filters.displayLimit || 100000}
                        onChange={(e) =>
                          onFiltersChange({
                            ...filters,
                            displayLimit: parseInt(e.target.value)
                          })
                        }
                      />
                      <span className="limit-hint">z {data.length} pobranych</span>
                    </div>

                    <input
                      type="range"
                      min="0"
                      max="100000"
                      step="100"
                      value={filters.displayLimit || 100000}
                      onChange={(e) =>
                        onFiltersChange({
                          ...filters,
                          displayLimit: parseInt(e.target.value)
                        })
                      }
                      className="limit-slider"
                    />
                  </div>

                  {/* Filtr przedziału czasowego */}
                  <div className="filter-subsection">
                    <h4>Przedział czasowy</h4>

                    <div className="time-range-options">
                      <button
                        className={`time-button ${!filters.timeRange ? 'active' : ''}`}
                        onClick={() => onFiltersChange({ ...filters, timeRange: null })}
                      >
                        Wszystkie
                      </button>
                      <button
                        className={`time-button ${filters.timeRange === 1 ? 'active' : ''}`}
                        onClick={() => onFiltersChange({ ...filters, timeRange: 1 })}
                      >
                        1 dzień
                      </button>
                      <button
                        className={`time-button ${filters.timeRange === 7 ? 'active' : ''}`}
                        onClick={() => onFiltersChange({ ...filters, timeRange: 7 })}
                      >
                        7 dni
                      </button>
                      <button
                        className={`time-button ${filters.timeRange === 14 ? 'active' : ''}`}
                        onClick={() => onFiltersChange({ ...filters, timeRange: 14 })}
                      >
                        14 dni
                      </button>
                    </div>
                  </div>

                  {/* Zakres dat */}
                  <div className="filter-subsection">
                    <h4>Zakres dat</h4>

                    <div className="date-inputs">
                      <div className="date-input-group">
                        <label htmlFor="start-date">Od:</label>
                        <input
                          id="start-date"
                          type="datetime-local"
                          value={filters.dateRange?.start || ''}
                          onChange={(e) =>
                            onFiltersChange({
                              ...filters,
                              dateRange: {
                                ...(filters.dateRange || {}),
                                start: e.target.value
                              }
                            })
                          }
                        />
                      </div>

                      <div className="date-input-group">
                        <label htmlFor="end-date">Do:</label>
                        <input
                          id="end-date"
                          type="datetime-local"
                          value={filters.dateRange?.end || ''}
                          onChange={(e) =>
                            onFiltersChange({
                              ...filters,
                              dateRange: {
                                ...(filters.dateRange || {}),
                                end: e.target.value
                              }
                            })
                          }
                        />
                      </div>
                    </div>

                    {(filters.dateRange?.start || filters.dateRange?.end) && (
                      <button
                        className="clear-dates-button"
                        onClick={() =>
                          onFiltersChange({
                            ...filters,
                            dateRange: { start: null, end: null }
                          })
                        }
                      >
                        ✕ Wyczyść daty
                      </button>
                    )}
                  </div>

                  {/* Short code */}
                  <div className="filter-subsection">
                    <h4>Short code (urządzenie)</h4>
                    <div className="limit-input-group">
                      <label htmlFor="short-code-input">Wpisz kod:</label>
                      <input
                        id="short-code-input"
                        type="text"
                        value={shortCodeInput}
                        onChange={(e) => setShortCodeInput(e.target.value)}
                        placeholder="np. ABCD-1234-XYZZ"
                        onKeyPress={(e) => {
                          if (e.key === 'Enter') {
                            onFiltersChange({ ...filters, shortCodeFilter: shortCodeInput });
                          }
                        }}
                      />
                    </div>
                    <div style={{ display: 'flex', gap: '6px', marginTop: '6px', alignItems: 'stretch' }}>
                      <button
                        className="time-button"
                        onClick={() => {
                          onFiltersChange({ ...filters, shortCodeFilter: shortCodeInput });
                        }}
                        style={{ flex: 1, minHeight: 40 }}
                      >
                        Szukaj
                      </button>
                      {shortCodeInput && (
                        <button
                          className="time-button"
                          onClick={() => {
                            setShortCodeInput('');
                            onFiltersChange({ ...filters, shortCodeFilter: '' });
                          }}
                          style={{ minWidth: 44, minHeight: 40 }}
                        >
                          ✕
                        </button>
                      )}
                    </div>
                  </div>


                </div>
              )}
            </div>

            {/* =========================ANIMACJA CZASOWA========================= */}
            <div className="filter-section expandable">
              <h3
                className="expandable-header"
                onClick={() => setAnimationExpanded(v => !v)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') setAnimationExpanded(v => !v);
                }}
              >
                <span className={`expand-icon ${animationExpanded ? 'expanded' : ''}`}>▶</span>
                Animacja czasowa
              </h3>
              {animationExpanded && (
                <div className="expandable-content">
                  <div className="filter-subsection">
                    <label className="checkbox-label">
                      <input
                        type="checkbox"
                        checked={isAnimationEnabled}
                        onChange={() => onAnimationToggle(!isAnimationEnabled)}
                      />
                      <span className="checkbox-custom"></span>
                      <span className="operator-name">Włącz animację</span>
                    </label>
                  </div>

                  {isAnimationEnabled && animationTime && minAnimationTime && maxAnimationTime && (
                    <>
                      <div className="filter-subsection">
                        <div className="animation-controls">
                          <button
                            className={`play-pause-button ${isPlaying ? 'playing' : ''}`}
                            onClick={onPlayPause}
                            title={isPlaying ? 'Pauza' : 'Odtwarzaj'}
                          >
                            {isPlaying ? (
                              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                                <rect x="6" y="4" width="4" height="16"/>
                                <rect x="14" y="4" width="4" height="16"/>
                              </svg>
                            ) : (
                              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                                <path d="M8 5v14l11-7z"/>
                              </svg>
                            )}
                          </button>
                          <div className="animation-time-display">
                            {animationTime ? (
                              <>
                                {animationTime.toLocaleTimeString('pl-PL')}
                                <br/>
                                {animationTime.toLocaleDateString('pl-PL')}
                              </>
                            ) : 'Brak czasu'}
                          </div>
                        </div>
                      </div>

                      <div className="filter-subsection">
                        <h4>Czas</h4>
                        <input
                          type="range"
                          min={minAnimationTime.getTime()}
                          max={maxAnimationTime.getTime()}
                          step={600000}
                          value={animationTime.getTime()}
                          onChange={(e) => onTimeChange(new Date(parseInt(e.target.value)))}
                          className="time-slider"
                          disabled={isPlaying}
                        />
                        <div className="time-range-labels">
                          <span className="time-min">{minAnimationTime.toLocaleDateString('pl-PL')}</span>
                          <span className="time-max">{maxAnimationTime.toLocaleDateString('pl-PL')}</span>
                        </div>
                      </div>

                      <div className="filter-subsection">
                        <h4>Prędkość</h4>
                        <div className="speed-options">
                          <button
                            className={`speed-button ${animationSpeed === 0.5 ? 'active' : ''}`}
                            onClick={() => onSpeedChange(0.5)}
                          >
                            0.5×
                          </button>
                          <button
                            className={`speed-button ${animationSpeed === 1 ? 'active' : ''}`}
                            onClick={() => onSpeedChange(1)}
                          >
                            1×
                          </button>
                          <button
                            className={`speed-button ${animationSpeed === 2 ? 'active' : ''}`}
                            onClick={() => onSpeedChange(2)}
                          >
                            2×
                          </button>
                          <button
                            className={`speed-button ${animationSpeed === 4 ? 'active' : ''}`}
                            onClick={() => onSpeedChange(4)}
                          >
                            4×
                          </button>
                        </div>
                      </div>
                    </>
                  )}

                  {isAnimationEnabled && (!animationTime || !minAnimationTime) && (
                    <div className="filter-subsection">
                      <p className="no-data">Brak danych do animacji. Sprawdź filtry.</p>
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* =========================TRYB 3D========================= */}
            <div className="filter-section">
              <div className="mode-3d-toggle toggle-row">
                <div className="layer-info">
                  <span className="layer-name">Tryb 3D</span>
                </div>
                <button
                  type="button"
                  className={`switch ${filters.is3DMode ? 'on' : ''}`}
                  onClick={toggle3DMode}
                  aria-pressed={!!filters.is3DMode}
                  aria-label="Przełącz tryb 3D"
                >
                  <span className="switch-handle" />
                </button>
              </div>
            </div>

            {/* =========================EKSPORT========================= */}
            <div className="filter-section expandable">
              <h3
                className="expandable-header"
                onClick={() => setExportExpanded(v => !v)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') setExportExpanded(v => !v);
                }}
              >
                <span className={`expand-icon ${exportExpanded ? 'expanded' : ''}`}>▶</span>
                Eksport
              </h3>
              {exportExpanded && (
              <div className="expandable-content">
                <div className="filter-subsection">
                  <div className="filter-options">
                    <label className="checkbox-label">
                      <input
                        type="checkbox"
                        checked={exportTelemetry}
                        onChange={() => setExportTelemetry(v => !v)}
                      />
                      <span className="checkbox-custom"></span>
                      <span className="operator-name">Sygnały (telemetria)</span>
                    </label>

                    <label className="checkbox-label">
                      <input
                        type="checkbox"
                        checked={exportSpeedtest}
                        onChange={() => setExportSpeedtest(v => !v)}
                      />
                      <span className="checkbox-custom"></span>
                      <span className="operator-name">Speedtesty</span>
                    </label>
                  </div>
                </div>

                <div className="filter-subsection">
                  <h4>Format</h4>
                  <div className="visualization-options">
                    <button
                      className="viz-button active"
                      onClick={() => onExport && onExport({ telemetry: exportTelemetry, speedtest: exportSpeedtest, format: 'csv' })}
                      title="CSV"
                    >
                      <span>CSV</span>
                    </button>
                    <button
                      className="viz-button"
                      onClick={() => onExport && onExport({ telemetry: exportTelemetry, speedtest: exportSpeedtest, format: 'geojson' })}
                      title="GeoJSON"
                    >
                      <span>GeoJSON</span>
                    </button>
                  </div>
                </div>

                <p className="filter-hint">Eksportuje wyfiltrowane dane zgodnie z ustawieniami powyżej.</p>
              </div>
              )}
            </div>
          </>
        )}
      </div>
    </aside>
  );
}

export default Sidebar;