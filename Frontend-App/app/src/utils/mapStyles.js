export const MAP_STYLES = {
  'STREETS': { name: 'Streets', url: 'streets-v2' },
  'STREETS.DARK': { name: 'Streets Dark', url: 'streets-v2-dark' },
  'STREETS.LIGHT': { name: 'Streets Light', url: 'streets-v2-light' },
  'STREETS.PASTEL': { name: 'Streets Pastel', url: 'streets-v2-pastel' },
  
  'OUTDOOR': { name: 'Outdoor', url: 'outdoor-v2' },
  'OUTDOOR.DARK': { name: 'Outdoor Dark', url: 'outdoor-v2-dark' },
  
  'WINTER': { name: 'Winter', url: 'winter-v2' },
  'WINTER.DARK': { name: 'Winter Dark', url: 'winter-v2-dark' },
  
  'SATELLITE': { name: 'Satellite', url: 'satellite' },
  'HYBRID': { name: 'Hybrid', url: 'hybrid' },
  
  'BASIC': { name: 'Basic', url: 'basic-v2' },
  'BASIC.DARK': { name: 'Basic Dark', url: 'basic-v2-dark' },
  'BASIC.LIGHT': { name: 'Basic Light', url: 'basic-v2-light' },
  
  'BRIGHT': { name: 'Bright', url: 'bright-v2' },
  'BRIGHT.DARK': { name: 'Bright Dark', url: 'bright-v2-dark' },
  'BRIGHT.LIGHT': { name: 'Bright Light', url: 'bright-v2-light' },
  'BRIGHT.PASTEL': { name: 'Bright Pastel', url: 'bright-v2-pastel' },
  
  'OPENSTREETMAP': { name: 'OpenStreetMap', url: 'openstreetmap' },
  
  'TOPO': { name: 'Topo', url: 'topo-v2' },
  'TOPO.DARK': { name: 'Topo Dark', url: 'topo-v2-dark' },
  'TOPO.PASTEL': { name: 'Topo Pastel', url: 'topo-v2-pastel' },
  
  'TONER': { name: 'Toner', url: 'toner-v2' },
  'TONER.LITE': { name: 'Toner Lite', url: 'toner-v2-lite' },
  
  'DATAVIZ': { name: 'Dataviz', url: 'dataviz' },
  'DATAVIZ.DARK': { name: 'Dataviz Dark', url: 'dataviz-dark' },
  'DATAVIZ.LIGHT': { name: 'Dataviz Light', url: 'dataviz-light' },
  
  'BACKDROP': { name: 'Backdrop', url: 'backdrop' },
  'BACKDROP.DARK': { name: 'Backdrop Dark', url: 'backdrop-dark' },
  'BACKDROP.LIGHT': { name: 'Backdrop Light', url: 'backdrop-light' },
  
  'OCEAN': { name: 'Ocean', url: 'ocean' },
  
  'AQUARELLE': { name: 'Aquarelle', url: 'aquarelle' },
  'AQUARELLE.DARK': { name: 'Aquarelle Dark', url: 'aquarelle-dark' },
  'AQUARELLE.VIVID': { name: 'Aquarelle Vivid', url: 'aquarelle-vivid' },
  
  'LANDSCAPE': { name: 'Landscape', url: 'landscape' },
  'LANDSCAPE.DARK': { name: 'Landscape Dark', url: 'landscape-dark' },
  'LANDSCAPE.VIVID': { name: 'Landscape Vivid', url: 'landscape-vivid' },
};

export const getMapStyleUrl = (styleKey, apiKey) => {
  const style = MAP_STYLES[styleKey];
  if (!style) return `https://api.maptiler.com/maps/dataviz-dark/style.json?key=${apiKey}`;
  return `https://api.maptiler.com/maps/${style.url}/style.json?key=${apiKey}`;
};