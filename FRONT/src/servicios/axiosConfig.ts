import axios from 'axios';
import Swal from 'sweetalert2';
import 'sweetalert2/dist/sweetalert2.min.css';

// Centralización del BaseURL para producción/desarrollo
const API_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
axios.defaults.baseURL = API_URL;

// Configurar interceptor globalmente
axios.interceptors.request.use(
    (config) => {
        // Obtener el token del localStorage
        const token = localStorage.getItem('token');

        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        } else {
            // Opcional: mantener compatibilidad con endpoints públicos o migraciones graduales
            const apiKey = import.meta.env.VITE_API_KEY || 'pharmaser_secure_api_key_2026';
            if (apiKey && !config.headers['Authorization']) {
                config.headers['x-api-key'] = apiKey;
            }
        }

        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);
// Manejo global de errores HTTP
axios.interceptors.response.use(
    (response) => response,
    async (error) => {
        const status = error.response?.status;

        if (status === 401) {
            // Sesión expirada o token inválido → cerrar sesión y redirigir al login
            await Swal.fire({
                icon: 'warning',
                title: 'Sesión Expirada',
                text: 'Tu sesión ha expirado. Por favor inicia sesión nuevamente.',
                confirmButtonColor: '#f6952c',
                background: '#fff',
                customClass: { popup: 'rounded-3xl' }
            });
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            if (window.location.pathname !== '/login') {
                window.location.href = '/login';
            }
        } else if (status === 403) {
            // Autenticado pero sin permisos → NO cerrar sesión, solo notificar
            await Swal.fire({
                icon: 'error',
                title: 'Acceso Denegado',
                text: 'No tienes permisos para realizar esta acción.',
                confirmButtonColor: '#f6952c',
                background: '#fff',
                customClass: { popup: 'rounded-3xl' }
            });
        }

        return Promise.reject(error);
    }
);

export default axios;
