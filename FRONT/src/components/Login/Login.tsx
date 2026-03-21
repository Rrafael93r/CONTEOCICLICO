import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { User, Lock, ArrowRight, ShieldCheck, Mail } from 'lucide-react';
import { login } from '../../servicios/authServices';
import logoph from "../../assets/inner.png";
import sistemas from "../../assets/SISTEMA_DE_GESTION_TIC_enhanced.jpg";
import Swal from 'sweetalert2';
import 'sweetalert2/dist/sweetalert2.min.css';

const Login = () => {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      const user = await login(username, password);
      if (user && user.roles) {
        navigate('/inicio');
      } else {
        Swal.fire({
          icon: 'error',
          title: '¡Oops!',
          text: 'Error en la estructura del usuario.',
          background: '#fff',
          confirmButtonColor: '#f6952c'
        });
      }
    } catch (err) {
      Swal.fire({
        icon: 'error',
        title: 'Acceso Denegado',
        text: 'Usuario o contraseña incorrectos. Por favor intenta de nuevo.',
        background: '#fff',
        confirmButtonColor: '#f6952c'
      });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#f8fafc] flex items-center justify-center p-4 relative overflow-hidden font-sans">
      {/* Elementos Decorativos de Fondo */}
      <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-orange-100 rounded-full blur-[120px] opacity-60"></div>
      <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-blue-50 rounded-full blur-[120px] opacity-60"></div>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease: "easeOut" }}
        className="container mx-auto max-w-5xl z-10"
      >
        <div className="bg-white/80 backdrop-blur-xl rounded-[2.5rem] shadow-2xl shadow-gray-200/50 overflow-hidden flex flex-col lg:flex-row border border-white">

          {/* Sección Izquierda: Formulario */}
          <div className="w-full lg:w-[45%] p-8 lg:p-14 flex flex-col justify-between bg-white/50">
            <div>
              <motion.div
                initial={{ opacity: 0, scale: 0.8 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ delay: 0.2 }}
                className="mb-10"
              >
                <img src={logoph} alt="Logo" className="h-12 w-auto object-contain" />
              </motion.div>

              <div className="mb-10">
                <h2 className="text-4xl font-black text-gray-900 mb-3 tracking-tight">Bienvenido</h2>
                <p className="text-gray-500 font-medium">Ingresa tus credenciales para continuar al sistema.</p>
              </div>

              <form onSubmit={handleSubmit} className="space-y-5">
                <div className="space-y-1.5">
                  <label className="text-sm font-bold text-gray-700 ml-1">Usuario</label>
                  <div className="relative group">
                    <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-gray-400 group-focus-within:text-orange-500 transition-colors">
                      <User size={20} />
                    </div>
                    <input
                      type="text"
                      className="w-full pl-11 pr-4 py-4 rounded-2xl bg-gray-50/50 border-2 border-transparent focus:border-orange-400 focus:bg-white outline-none transition-all font-medium text-gray-700 placeholder:text-gray-400"
                      placeholder="nombre_usuario"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      required
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <label className="text-sm font-bold text-gray-700 ml-1">Contraseña</label>
                  <div className="relative group">
                    <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-gray-400 group-focus-within:text-orange-500 transition-colors">
                      <Lock size={20} />
                    </div>
                    <input
                      type="password"
                      className="w-full pl-11 pr-4 py-4 rounded-2xl bg-gray-50/50 border-2 border-transparent focus:border-orange-400 focus:bg-white outline-none transition-all font-medium text-gray-700 placeholder:text-gray-400"
                      placeholder="••••••••"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                    />
                  </div>
                </div>

                <motion.button
                  whileHover={{ scale: 1.01 }}
                  whileTap={{ scale: 0.99 }}
                  type="submit"
                  disabled={isLoading}
                  className="w-full py-4 rounded-2xl font-bold text-white shadow-lg shadow-orange-200 bg-gradient-to-r from-orange-400 to-orange-500 hover:from-orange-500 hover:to-orange-600 transition-all flex items-center justify-center gap-2 group disabled:opacity-70"
                >
                  {isLoading ? (
                    <div className="w-6 h-6 border-3 border-white/30 border-t-white rounded-full animate-spin"></div>
                  ) : (
                    <>
                      Iniciar Sesión
                      <ArrowRight size={20} className="group-hover:translate-x-1 transition-transform" />
                    </>
                  )}
                </motion.button>
              </form>
            </div>

            <div className="mt-12 pt-8 border-t border-gray-100 flex flex-col items-center gap-4">
              <div className="text-center text-gray-400 text-[10px] leading-relaxed">
                <p>© Todos los derechos reservados</p>
                <div className="group relative inline-block mt-0.5 pointer-events-auto cursor-help">
                  <span className="hover:text-gray-600 transition-colors">Desarrollado por R.R.R.</span>
                  <div className="invisible group-hover:visible absolute bottom-full left-1/2 -translate-x-1/2 mb-2 p-3 bg-gray-900 text-white rounded-xl shadow-2xl opacity-0 group-hover:opacity-100 transition-all duration-300 z-50 min-w-[200px]">
                    <p className="font-bold text-xs mb-1">Rafael Rojas Ramírez</p>
                    <div className="absolute bottom-[-6px] left-1/2 -translate-x-1/2 w-3 h-3 bg-gray-900 rotate-45"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Sección Derecha: Visual */}
          <div className="hidden lg:flex w-[55%] bg-gradient-to-br from-gray-50 to-orange-50/30 items-center justify-center p-12 relative overflow-hidden">
            {/* Patrón de fondo sutil */}
            <div className="absolute inset-0 opacity-[0.03]" style={{ backgroundImage: 'radial-gradient(#000 1px, transparent 0)', backgroundSize: '24px 24px' }}></div>

            <motion.div
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: 0.4, duration: 0.8 }}
              className="relative z-10"
            >
              <img
                src={sistemas}
                className="max-w-full h-auto object-contain rounded-2xl shadow-2xl shadow-orange-900/10 transform hover:scale-[1.02] transition-transform duration-700"
                alt="Dashboard Visual"
              />

            </motion.div>
          </div>

        </div>
      </motion.div>
    </div>
  );
};

export default Login;