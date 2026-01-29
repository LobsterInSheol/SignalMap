import React, { useState } from 'react';
import './Header.css';
import { MAP_STYLES } from '../../utils/mapStyles';
import logoLight from '../../assets/signal_logo_for_light_mode.png';
import logoDark from '../../assets/signal_logo_for_dark_mode.png';

function Header({ onToggleSidebar, sidebarOpen, mapStyle, onMapStyleChange,   themeMode, onThemeModeChange }) {
  const [showInfo, setShowInfo] = useState(false);
  const [showMapStyles, setShowMapStyles] = useState(false);
  const [showThemeMenu, setShowThemeMenu] = useState(false);
  // Grupuj style po kategoriach
  const styleCategories = {
    'Navigation': ['STREETS', 'STREETS.DARK', 'STREETS.LIGHT', 'STREETS.PASTEL'],
    'Outdoor': ['OUTDOOR', 'OUTDOOR.DARK', 'WINTER', 'WINTER.DARK'],
    'Satellite': ['SATELLITE', 'HYBRID'],
    'Basic': ['BASIC', 'BASIC.DARK', 'BASIC.LIGHT'],
    'Bright': ['BRIGHT', 'BRIGHT.DARK', 'BRIGHT.LIGHT', 'BRIGHT.PASTEL'],
    'Data Viz': ['DATAVIZ', 'DATAVIZ.DARK', 'DATAVIZ.LIGHT'],
    'Backdrop': ['BACKDROP', 'BACKDROP.DARK', 'BACKDROP.LIGHT'],
    'Topographic': ['TOPO', 'TOPO.DARK', 'TOPO.PASTEL', 'OPENSTREETMAP'],
    'Artistic': ['TONER', 'TONER.LITE', 'AQUARELLE', 'AQUARELLE.DARK', 'AQUARELLE.VIVID'],
    'Other': ['OCEAN', 'LANDSCAPE', 'LANDSCAPE.DARK', 'LANDSCAPE.VIVID'],
  };

const effectiveTheme =
  themeMode === 'auto'
    ? (mapStyle?.includes('DARK') ? 'dark' : 'light')
    : themeMode;

const logoSrc = effectiveTheme === 'light' ? logoLight : logoDark;

  return (
    <>
      <header className="header">
        <div className="header-left">
          <button 
            className="sidebar-toggle"
            onClick={onToggleSidebar}
            aria-label="Toggle sidebar"
          >
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path 
                d="M3 5h14M3 10h14M3 15h14" 
                stroke="currentColor" 
                strokeWidth="2" 
                strokeLinecap="round"
              />
            </svg>
          </button>
          
<div className="logo">
<img src={logoSrc} alt="SignalMap" className="logo-image" />
  <div className="logo-text">
    <h1> SignalMap </h1>
    <span className="version">v0.1.0</span>
  </div>
</div>
        </div>
        
         <div className="header-right">
          <button 
            className="header-button" 
            title="Informacje"
            onClick={() => setShowInfo(true)}
          >
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <circle cx="10" cy="10" r="8" stroke="currentColor" strokeWidth="2"/>
              <path d="M10 6v4m0 4h.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
            </svg>
          </button>
          
          {/* Przełącznik motywu */}
          <div className="theme-dropdown">
            <button 
              className="header-button" 
              title="Motyw interfejsu"
              onClick={() => setShowThemeMenu(!showThemeMenu)}
            >
              {themeMode === 'light' ? (
<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
  <path strokeLinecap="round" strokeLinejoin="round" d="M12 3v2.25m6.364.386-1.591 1.591M21 12h-2.25m-.386 6.364-1.591-1.591M12 18.75V21m-4.773-4.227-1.591 1.591M5.25 12H3m4.227-4.773L5.636 5.636M15.75 12a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0Z" />
</svg>
              ) : themeMode === 'dark' ? (
                <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                  <path d="M17.293 13.293A8 8 0 016.707 2.707a8.001 8.001 0 1010.586 10.586z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              ) : (
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 2v2"/>
    <path d="M14.837 16.385a6 6 0 1 1-7.223-7.222c.624-.147.97.66.715 1.248a4 4 0 0 0 5.26 5.259c.589-.255 1.396.09 1.248.715"/>
    <path d="M16 12a4 4 0 0 0-4-4"/>
    <path d="m19 5-1.256 1.256"/>
    <path d="M20 12h2"/>
  </svg>
              )}
            </button>
            
            {showThemeMenu && (
              <>
                <div className="dropdown-overlay" onClick={() => setShowThemeMenu(false)} />
                <div className="theme-menu">
                  <div className="dropdown-header">
                    <h3>Motyw interfejsu</h3>
                  </div>
                  
                  <button
                    className={`theme-option ${themeMode === 'auto' ? 'active' : ''}`}
                    onClick={() => {
                      onThemeModeChange('auto');
                      setShowThemeMenu(false);
                    }}
                  >
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
  <path d="M12 2v2"/>
  <path d="M14.837 16.385a6 6 0 1 1-7.223-7.222c.624-.147.97.66.715 1.248a4 4 0 0 0 5.26 5.259c.589-.255 1.396.09 1.248.715"/>
  <path d="M16 12a4 4 0 0 0-4-4"/>
  <path d="m19 5-1.256 1.256"/>
  <path d="M20 12h2"/>
</svg>
                    <div>
                      <div className="theme-name">Automatyczny</div>
                      <div className="theme-description">Dopasowany do mapy</div>
                    </div>
                    {themeMode === 'auto' && <span className="check">✓</span>}
                  </button>
                  
                  <button
                    className={`theme-option ${themeMode === 'light' ? 'active' : ''}`}
                    onClick={() => {
                      onThemeModeChange('light');
                      setShowThemeMenu(false);
                    }}
                  >
<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
  <path strokeLinecap="round" strokeLinejoin="round" d="M12 3v2.25m6.364.386-1.591 1.591M21 12h-2.25m-.386 6.364-1.591-1.591M12 18.75V21m-4.773-4.227-1.591 1.591M5.25 12H3m4.227-4.773L5.636 5.636M15.75 12a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0Z" />
</svg>
                    <div>
                      <div className="theme-name">Jasny</div>
                      <div className="theme-description">Zawsze jasne UI</div>
                    </div>
                    {themeMode === 'light' && <span className="check">✓</span>}
                  </button>
                  
                  <button
                    className={`theme-option ${themeMode === 'dark' ? 'active' : ''}`}
                    onClick={() => {
                      onThemeModeChange('dark');
                      setShowThemeMenu(false);
                    }}
                  >
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                      <path d="M17.293 13.293A8 8 0 016.707 2.707a8.001 8.001 0 1010.586 10.586z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                    <div>
                      <div className="theme-name">Ciemny</div>
                      <div className="theme-description">Zawsze ciemne UI</div>
                    </div>
                    {themeMode === 'dark' && <span className="check">✓</span>}
                  </button>
                </div>
              </>
            )}
          </div>
          
          {/* Dropdown stylów mapy */}
          <div className="map-style-dropdown">
            <button 
  className="header-button" 
  title="Styl mapy"
  onClick={() => setShowMapStyles(!showMapStyles)}
>
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M14.106 5.553a2 2 0 0 0 1.788 0l3.659-1.83A1 1 0 0 1 21 4.619v12.764a1 1 0 0 1-.553.894l-4.553 2.277a2 2 0 0 1-1.788 0l-4.212-2.106a2 2 0 0 0-1.788 0l-3.659 1.83A1 1 0 0 1 3 19.381V6.618a1 1 0 0 1 .553-.894l4.553-2.277a2 2 0 0 1 1.788 0z"/>
    <path d="M15 5.764v15"/>
    <path d="M9 3.236v15"/>
  </svg>
</button>
            
            {showMapStyles && (
              <>
                <div className="dropdown-overlay" onClick={() => setShowMapStyles(false)} />
                <div className="dropdown-menu">
                  <div className="dropdown-header">
                    <h3>Styl mapy</h3>
                    <span className="current-style">{MAP_STYLES[mapStyle]?.name || 'Dataviz Dark'}</span>
                  </div>
                  
                  <div className="dropdown-content">
                    {Object.entries(styleCategories).map(([category, styles]) => (
                      <div key={category} className="style-category">
                        <div className="category-name">{category}</div>
                        {styles.map(styleKey => (
                          <button
                            key={styleKey}
                            className={`style-option ${mapStyle === styleKey ? 'active' : ''}`}
                            onClick={() => {
                              onMapStyleChange(styleKey);
                              setShowMapStyles(false);
                            }}
                          >
                            {MAP_STYLES[styleKey]?.name}
                            {mapStyle === styleKey && <span className="check">✓</span>}
                          </button>
                        ))}
                      </div>
                    ))}
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Modal z informacjami */}
      {showInfo && (
        <div className="modal-overlay" onClick={() => setShowInfo(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <button className="modal-close" onClick={() => setShowInfo(false)}>
              ✕
            </button>
            
            <h2>SignalMap</h2>
            <p className="version-info">Wersja 0.1.0</p>
            
            <div className="info-section">
              <h3>O aplikacji</h3>
              <p>
                SignalMap to interaktywna mapa pokazująca jakość sieci komórkowej.
                Wizualizuje dane telemetryczne z pomiarów siły sygnału różnych operatorów.
                Domyślnie pobierane są pomiary z ostatnich 2 miesięcy. 

                Pomiar mocy sygnału referencyjnego RSRP w sieciach LTE jest definiowany jako średnia moc odbierana z sygnałów referencyjnych, podczas gdy w sieciach 3G i 2G parametr RSCP mierzy całkowitą moc odebranego sygnału. Ze względu na tę różnicę, wartości te nie mogą być bezpośrednio porównywane.
              </p>
            </div>

            <div className="info-section">
              <h3>Jak korzystać?</h3>
              <ul>
                <li><strong>Kliknij punkt</strong> - zobacz szczegóły pomiaru/stacji</li>
                <li><strong>Zoom</strong> - scroll lub +/- na mapie</li>
                <li><strong>Filtruj</strong> - użyj panelu po lewej stronie</li>
                <li><strong>Tryb</strong> - przełącz UI jasny/ciemny w górnym menu lub wybierz rodzaj mapy</li>
                <li><strong>Obracanie</strong> Użyj controla + myszkę żeby zmienić kąt patrzenia na mape</li>
              </ul>
            </div>

            <div className="info-section">
              <h3>Legenda kolorów</h3>
              <div className="color-legend">
                <div className="color-item">
                  <span className="color-dot" style={{background: '#22c55e'}}></span>
                  <span>Świetny (≥ -70 dBm)</span>
                </div>
                <div className="color-item">
                  <span className="color-dot" style={{background: '#eab308'}}></span>
                  <span>Dobry (-70 do -85 dBm)</span>
                </div>
                <div className="color-item">
                  <span className="color-dot" style={{background: '#f97316'}}></span>
                  <span>Średni (-85 do -100 dBm)</span>
                </div>
                <div className="color-item">
                  <span className="color-dot" style={{background: '#ef4444'}}></span>
                  <span>Słaby (&lt; -100 dBm)</span>
                </div>
              </div>
            </div>

            <div className="info-footer">
              <p>Dane dotyczące stacji bazowych pochodzą z btsearch.pl</p>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

export default Header;