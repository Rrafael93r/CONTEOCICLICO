import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { login } from '../../servicios/authServices';
import logoph from "../../assets/inner.png";
import sistemas from "../../assets/SISTEMA_DE_GESTION_TIC_enhanced.webp";
import Swal from 'sweetalert2';
import 'sweetalert2/dist/sweetalert2.min.css';

const Login = () => {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    try {
      const user = await login(username, password); // Llamada a la función login del servicio authServices
      console.log('Login.tsx: Backend returned user:', user);

      if (user && user.roles) {
        console.log('Login.tsx: Navigating to /inicio');
        navigate('/inicio');
      } else {
        console.error('Login.tsx: Missing user.roles', user);
        Swal.fire({
          icon: 'error',
          title: 'Error',
          text: 'Error en la estructura del usuario.',
        });
      }
    } catch (err) {
      console.error('Error durante el login:', err);
      Swal.fire({
        icon: 'error',
        title: 'Usuario o contraseña incorrectos',
        text: 'Por favor, verifica tus credenciales.',
      });
    }
  };

  return (
    <div className="min-h-screen bg-gray-100 flex items-center justify-center p-4">
      <div className="container mx-auto max-w-5xl">
        <div className="bg-white rounded-2xl shadow-xl overflow-hidden flex flex-col lg:flex-row relative">

          {/* Formulario (Izquierda) */}
          <div className="w-full lg:w-1/2 p-8 lg:p-12 flex flex-col justify-center">

            <div className="lg:hidden text-center mb-8">
              <img src={logoph} alt="Logo" className="max-w-[100px] inline-block h-auto" />
            </div>

            <h2 className="text-3xl font-bold mb-4 text-gray-800">Iniciar Sesión</h2>
            <h4 className="text-lg font-bold mb-2 text-gray-700">Accede a tu cuenta</h4>
            <p className="text-gray-500 text-sm mb-8">
              Bienvenido de vuelta a <strong className="font-semibold text-gray-700">nuestro sistema</strong>
            </p>

            <form onSubmit={handleSubmit} className="space-y-6">
              <div>
                <input
                  type="text"
                  className="w-full px-4 py-3 rounded-lg bg-gray-50 border border-gray-200 focus:border-orange-500 focus:bg-white focus:ring-2 focus:ring-orange-200 outline-none transition-all"
                  placeholder="Usuario"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  required
                />
              </div>
              <div>
                <input
                  type="password"
                  className="w-full px-4 py-3 rounded-lg bg-gray-50 border border-gray-200 focus:border-orange-500 focus:bg-white focus:ring-2 focus:ring-orange-200 outline-none transition-all"
                  placeholder="Contraseña"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>
              <button
                type="submit"
                className="w-full py-3 rounded-lg font-semibold text-white transition-colors duration-200 hover:bg-orange-600"
                style={{ backgroundColor: '#f6952c' }}
              >
                Iniciar Sesión
              </button>
            </form>

            <div className="relative my-8 text-center bg-transparent">
              <div className="absolute inset-0 flex items-center">
                <div className="w-full border-t border-gray-200"></div>
              </div>
              <span className="relative px-4 bg-white text-sm text-gray-500">
                o
              </span>
            </div>

            <div className="text-center text-gray-400 text-xs flex flex-col items-center">
              <span>Copyright © 2026 Todos los derechos reservados</span>
              <span className="group relative inline-block mt-1 cursor-default opacity-70 hover:opacity-100 transition-opacity">
                Desarrollado por R.R.R.
                <span className="invisible group-hover:visible absolute bottom-full left-1/2 -translate-x-1/2 mb-1 px-2 py-1 bg-gray-700 text-white text-[10px] rounded whitespace-nowrap opacity-0 group-hover:opacity-100 transition-all duration-300 z-50">
                  Rafael Rojas Ramírez
                  <svg className="absolute text-gray-700 h-2 w-full left-0 top-full" x="0px" y="0px" viewBox="0 0 255 255" xmlSpace="preserve"><polygon className="fill-current" points="0,0 127.5,127.5 255,0" /></svg>
                </span>
              </span>
            </div>
          </div>

          {/* Logo central (Escritorio) */}
          <div className="hidden lg:block absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 z-10">
            <div className="bg-white p-4 rounded-full shadow-lg">
              <img src={logoph} alt="Logo" className="w-[60px] h-[60px] object-contain" />
            </div>
          </div>

          {/* Imagen ilustrativa (Derecha) */}
          <div className="w-full lg:w-1/2 bg-gray-50 hidden lg:flex items-center justify-center p-8">
            <img src={sistemas} className="max-w-full h-auto object-contain drop-shadow-md hover:scale-105 transition-transform duration-500" alt="Sistema" />
          </div>

        </div>
      </div>
    </div>
  );
};

export default Login;