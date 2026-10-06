import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@/styles/globals.css';
import App from '@/App';

const container = document.getElementById('root');
if (!container) {
  throw new Error('Root container #root is missing from index.html');
}

// App owns the single BrowserRouter — a second one nested inside throws on render.
createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);