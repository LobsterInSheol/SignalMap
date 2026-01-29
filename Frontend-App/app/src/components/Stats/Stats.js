import React from 'react';
import './Stats.css';

function Stats({ stats, sidebarOpen, visible, onToggle }) {
  return (
    <div 
      className="stats-container" 
      style={{ 
        left: sidebarOpen ? 'calc(320px + 24px)' : '24px',
        transition: 'left 0.25s ease-in-out'
      }}
    >
      {/* Karty statystyk - pokaż tylko gdy visible */}
      {visible && (
        <>
          <div className="stat-card">
            <div className="stat-icon">
  <svg
    width="28"
    height="28"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M18 8c0 3.613-3.869 7.429-5.393 8.795a1 1 0 0 1-1.214 0C9.87 15.429 6 11.613 6 8a6 6 0 0 1 12 0" />
    <circle cx="12" cy="8" r="2" />
    <path d="M8.714 14h-3.71a1 1 0 0 0-.948.683l-2.004 6A1 1 0 0 0 3 22h18a1 1 0 0 0 .948-1.316l-2-6a1 1 0 0 0-.949-.684h-3.712" />
  </svg>
</div>
            <div className="stat-content">
              <div className="stat-label">Punkty pomiarowe</div>
              <div className="stat-value">{stats.total.toLocaleString('pl-PL')}</div>
            </div>
          </div>
          
          <div className="stat-card">
            <div className="stat-icon">
            <svg
  width="28"
  height="28"
  viewBox="0 0 24 24"
  fill="none"
  stroke="currentColor"
  strokeWidth="2"
  strokeLinecap="round"
  strokeLinejoin="round"
>
  <path d="M2 20h.01" />
  <path d="M7 20v-4" />
  <path d="M12 20v-8" />
  <path d="M17 20V8" />
  <path d="M22 4v16" />
</svg>
</div>
            <div className="stat-content">
              <div className="stat-label">Średni sygnał</div>
              <div className="stat-value">
                {stats.avgSignal} <span className="stat-unit">dBm</span>
              </div>
            </div>
          </div>
          
          <div className="stat-card">
            <div className="stat-icon">
            <svg
  width="24"
  height="24"
  viewBox="0 0 24 24"
  fill="none"
  stroke="currentColor"
  strokeWidth="2"
  strokeLinecap="round"
  strokeLinejoin="round"
>
  <path d="M13.832 16.568a1 1 0 0 0 1.213-.303l.355-.465A2 2 0 0 1 17 15h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2A18 18 0 0 1 2 4a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-.8 1.6l-.468.351a1 1 0 0 0-.292 1.233 14 14 0 0 0 6.392 6.384" />
</svg>
</div>
            <div className="stat-content">
              <div className="stat-label">Operatorzy</div>
              <div className="stat-value">{stats.operators}</div>
            </div>
          </div>
        </>
      )}

      {/* Przycisk minimalizacji*/}
      <button 
        className="stats-minimize-btn"
        onClick={onToggle}
        title={visible ? "Ukryj statystyki" : "Pokaż statystyki"}
      >
        {visible ? (    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M10 3L5 8l5 5" strokeLinecap="round" strokeLinejoin="round"/>
      <path d="M6 3L1 8l5 5" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>) : 
  <svg
    fill="none"
    viewBox="0 0 24 24"
    strokeWidth={1.5}
    stroke="currentColor"
    className="size-6"
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M7.5 14.25v2.25m3-4.5v4.5m3-6.75v6.75m3-9v9M6 20.25h12A2.25 2.25 0 0 0 20.25 18V6A2.25 2.25 0 0 0 18 3.75H6A2.25 2.25 0 0 0 3.75 6v12A2.25 2.25 0 0 0 6 20.25Z"
    />
  </svg>
}
      </button>
    </div>
  );
}

export default Stats;