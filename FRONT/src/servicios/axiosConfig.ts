import axios from 'axios';
import Swal from 'sweetalert2';
import 'sweetalert2/dist/sweetalert2.min.css';

// Centralización del BaseURL para producción/desarrollo
const API_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
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
// Manejo global de errores (opcional, pero útil)
axios.interceptors.response.use(
    (response) => response,
    async (error) => {
        if (error.response && (error.response.status === 401 || error.response.status === 403)) {
            
            // Pausar para que el usuario pueda ver el error
            await Swal.fire({
                icon: 'warning',
                title: 'Sesión Finalizada o No Autorizado',
                text: 'Tu sesión ha expirado o no tienes permisos para realizar esta acción. Serás redirigido al login.',
                confirmButtonColor: '#f6952c',
                background: '#fff',
                customClass: {
                    popup: 'rounded-3xl'
                }
            });

            // Token expirado o inválido
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            // Redirigir al login si no estamos ya allí
            if (window.location.pathname !== '/login') {
                window.location.href = '/login';
            }
        }
        return Promise.reject(error);
    }
);

export default axios;
