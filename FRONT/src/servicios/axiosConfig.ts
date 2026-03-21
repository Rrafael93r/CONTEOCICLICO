import axios from 'axios';

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
    (error) => {
        if (error.response && (error.response.status === 401 || error.response.status === 403)) {
            console.error('Interceptor detectó error 401/403 en:', error.config?.url);
            console.error('Status:', error.response.status, 'Response:', error.response.data);
            
            // Pausar para que el usuario pueda ver el error
            alert('Error 401/403 detectado al llamar a: ' + error.config?.url + '\n\nPresiona Aceptar para continuar al login.');

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
