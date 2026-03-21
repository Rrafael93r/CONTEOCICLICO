import React, { useEffect, useState } from 'react';
import { getAllPersonalizados, Personalizado } from '../../servicios/personalizadoService';
import { getCurrentUser } from '../../servicios/authServices';
import Layout from '../Layout/Layout';
import Swal from 'sweetalert2';

const PersonalizadoTable: React.FC = () => {
    const [personalizados, setPersonalizados] = useState<Personalizado[]>([]);
    const [loading, setLoading] = useState(true);

    const fetchData = async () => {
        setLoading(true);
        try {
            const currentUser = getCurrentUser();
            if (!currentUser) return;
            
            const data = await getAllPersonalizados(currentUser.id);
            setPersonalizados(data);
        } catch (error) {
            Swal.fire({
                icon: 'error',
                title: 'Error de Carga',
                text: 'No se pudieron obtener las programaciones personalizadas.',
                confirmButtonColor: '#f6952c',
                customClass: { popup: 'rounded-3xl' }
            });
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    if (loading && personalizados.length === 0) return (
        <div className="p-20 text-center">
            <div className="w-12 h-12 border-4 border-orange-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
            <p className="text-gray-500 font-bold animate-pulse uppercase tracking-widest text-xs">Cargando Programaciones...</p>
        </div>
    );

    return (
        <div className="max-w-7xl mx-auto space-y-6 pb-24 transition-all duration-500 animate-in fade-in slide-in-from-bottom-4">
            {/* Cabecera de la Página */}
            <div className="bg-white rounded-[2.5rem] shadow-xl shadow-orange-900/5 border border-orange-50/50 p-6 md:p-8 flex flex-col md:flex-row justify-between items-center gap-6">
                <div className="text-center md:text-left">
                    <div className="flex items-center justify-center md:justify-start gap-3 mb-2">
                        <div className="w-12 h-12 bg-gray-900 rounded-2xl flex items-center justify-center text-white shadow-lg shadow-gray-900/20">
                            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" /></svg>
                        </div>
                        <h3 className="text-2xl md:text-3xl font-black text-gray-900 tracking-tight uppercase">CONTEO PERSONALIZADO</h3>
                    </div>
                    <p className="text-xs md:text-sm text-gray-400 font-bold uppercase tracking-[0.2em] flex items-center justify-center md:justify-start gap-2">
                        <span className="w-2 h-2 bg-orange-500 rounded-full animate-pulse"></span>
                        Gestión de Programaciones Específicas
                    </p>
                </div>
                
                <div className="flex flex-wrap justify-center items-center gap-3">
                    <div className="bg-orange-50 px-6 py-3 rounded-2xl border-2 border-orange-100/50 flex flex-col items-center">
                        <span className="text-[10px] font-black text-orange-400 uppercase tracking-widest leading-none mb-1">Activos</span>
                        <span className="text-xl font-black text-orange-600 leading-none">{personalizados.length}</span>
                    </div>
                    <button 
                        onClick={fetchData}
                        className="p-4 text-gray-400 hover:bg-gray-900 hover:text-white transition-all bg-white rounded-2xl border-2 border-gray-100 shadow-sm active:scale-95 group"
                    >
                        <svg className="w-6 h-6 group-hover:rotate-180 transition-transform duration-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 00-15.357-2m15.357 2H15" /></svg>
                    </button>
                </div>
            </div>

            {/* Vista Desktop: Tabla */}
            <div className="hidden lg:block bg-white rounded-[2.5rem] shadow-2xl shadow-gray-200/50 border border-gray-100 overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="bg-gray-50/50 text-gray-400 text-[10px] font-black uppercase tracking-[0.2em]">
                                <th className="px-10 py-6">Medicamento Relacionado</th>
                                <th className="px-10 py-6 text-center">Referencia</th>
                                <th className="px-10 py-6">Responsable</th>
                                <th className="px-10 py-6 text-center">Fecha Programada</th>
                                <th className="px-10 py-6 text-center">Plan de Acción</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-50 text-sm">
                            {personalizados.map((p) => (
                                <tr key={p.id} className="hover:bg-orange-50/10 transition-colors group">
                                    <td className="px-10 py-8">
                                        <div className="flex flex-col">
                                            <span className="text-gray-900 font-black text-lg group-hover:text-orange-600 transition-colors leading-tight">
                                                {p.medicamento?.descripcion || 'No definido'}
                                            </span>
                                            <span className="text-[11px] text-gray-400 font-bold mt-1 uppercase tracking-wider">
                                                ID REGISTRO: #{p.id}
                                            </span>
                                        </div>
                                    </td>
                                    <td className="px-10 py-8 text-center">
                                        <span className="bg-gray-100 text-gray-500 px-4 py-1.5 rounded-xl text-[11px] font-black tracking-wide border border-gray-200/50 uppercase">
                                            {p.medicamento?.codigoGenerico || 'S/N'}
                                        </span>
                                    </td>
                                    <td className="px-10 py-8">
                                        <div className="flex items-center gap-3">
                                            <div className="w-8 h-8 rounded-lg bg-orange-100 flex items-center justify-center text-orange-600">
                                                <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20"><path fillRule="evenodd" d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" clipRule="evenodd" /></svg>
                                            </div>
                                            <span className="text-gray-600 font-bold uppercase text-xs tracking-tight">
                                                {p.usuario?.usuario || 'Sin asignar'}
                                            </span>
                                        </div>
                                    </td>
                                    <td className="px-10 py-8 text-center">
                                        <span className="text-gray-500 font-black font-mono">
                                            {p.fechaProgramacion}
                                        </span>
                                    </td>
                                    <td className="px-10 py-8">
                                        <div className="flex justify-center">
                                            <button className="px-6 py-2 rounded-2xl bg-gray-900 text-white text-[10px] font-black uppercase tracking-widest border border-gray-900 hover:bg-orange-500 hover:border-orange-500 transition-all shadow-lg shadow-gray-200 active:scale-95">
                                                Gestionar
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Vista Móvil: Cards */}
            <div className="lg:hidden space-y-4 px-1">
                {personalizados.map((p) => (
                    <div key={p.id} className="bg-white rounded-[2rem] p-6 shadow-lg border-2 border-white transition-all hover:border-orange-100">
                        <div className="flex justify-between items-start mb-6">
                            <div className="flex-1">
                                <h4 className="text-lg font-black text-gray-900 leading-tight mb-2 tracking-tight">
                                    {p.medicamento?.descripcion || 'No definido'}
                                </h4>
                                <div className="flex flex-wrap gap-2 items-center">
                                    <span className="text-[10px] font-black text-orange-500 bg-orange-50 px-3 py-1 rounded-lg uppercase tracking-widest border border-orange-100">
                                        {p.medicamento?.codigoGenerico || 'S/N'}
                                    </span>
                                </div>
                            </div>
                            <div className="bg-gray-50 p-3 rounded-2xl text-gray-400 border border-gray-100">
                                <span className="text-[10px] font-black">#{p.id}</span>
                            </div>
                        </div>
                        
                        <div className="space-y-4">
                            <div className="flex items-center justify-between text-[11px] py-4 border-y border-gray-50 font-bold">
                                <div className="flex items-center gap-2 text-gray-400 uppercase tracking-widest">
                                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" /></svg>
                                    Asignado a:
                                </div>
                                <span className="text-gray-900 uppercase">{p.usuario?.usuario || 'S/A'}</span>
                            </div>
                            
                            <div className="flex items-center justify-between py-2">
                                <div className="flex flex-col">
                                    <span className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1">Fecha Programada</span>
                                    <span className="text-lg font-black text-gray-900 font-mono tracking-tighter">{p.fechaProgramacion}</span>
                                </div>
                                <button className="p-4 bg-orange-500 text-white rounded-[1.5rem] shadow-lg shadow-orange-500/30 active:scale-90 transition-transform">
                                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M12 6v6m0 0v6m0-6h6m-6 0H6" /></svg>
                                </button>
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {/* Empty State */}
            {personalizados.length === 0 && !loading && (
                <div className="bg-white rounded-[2.5rem] p-20 text-center shadow-xl border border-gray-100">
                    <div className="flex flex-col items-center">
                        <div className="w-24 h-24 bg-gray-50 text-gray-200 rounded-[2rem] flex items-center justify-center mb-8 rotate-12 border border-gray-100">
                            <svg className="w-12 h-12" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" /></svg>
                        </div>
                        <h4 className="text-2xl font-black text-gray-900 tracking-tight">SIN PROGRAMACIONES</h4>
                        <p className="text-gray-400 font-bold max-w-xs mt-3 uppercase text-[10px] tracking-[0.2em] leading-relaxed italic">No se han registrado conteos personalizados para tu usuario.</p>
                    </div>
                </div>
            )}

            {/* Botón Flotante para Acciones Globales (Ejemplo de consistencia) */}
            <div className="fixed bottom-10 left-0 right-0 px-6 z-40 flex justify-center pointer-events-none">
                <div className="max-w-md w-full pointer-events-auto">
                    <button
                        className="w-full flex items-center justify-center gap-4 px-8 py-6 bg-gray-900/90 backdrop-blur-md text-white font-black rounded-[2.5rem] shadow-[0_20px_50px_rgba(0,0,0,0.3)] hover:shadow-orange-500/40 hover:bg-orange-600 transition-all duration-500 group disabled:opacity-30 transform hover:-translate-y-1 active:scale-95"
                    >
                        <div className="bg-orange-500 p-2 rounded-xl group-hover:bg-white group-hover:text-orange-600 transition-colors shadow-lg shadow-orange-500/20">
                            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M12 6v6m0 0v6m0-6h6m-6 0H6" /></svg>
                        </div>
                        <span className="text-lg tracking-tight uppercase">Nueva Programación</span>
                    </button>
                </div>
            </div>
        </div>
    );
};

export default PersonalizadoTable;
