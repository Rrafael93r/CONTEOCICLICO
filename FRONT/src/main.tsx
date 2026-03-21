import { StrictMode } from 'react';
import './servicios/axiosConfig'; // Importar configuración de Axios
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './estilos.css';
import 'bootstrap-icons/font/bootstrap-icons.css';

const root = document.getElementById('root');

if (!root) {
  throw new Error('Root element not found');
}

createRoot(root).render(
  <div style={{ backgroundColor: '#f8f9fa' }}>
    <StrictMode>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </StrictMode>
  </div>
);