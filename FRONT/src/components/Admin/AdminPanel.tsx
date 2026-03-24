import React, { useEffect, useState } from 'react';
import { getAllUsuarios, updateUsuario, Usuario } from '../../servicios/usuarioService';
import { getAllMedicamentos, Medicamento } from '../../servicios/medicamentoService';
import { createPersonalizado } from '../../servicios/personalizadoService';
import { getAllDetalles, DetalleConteo } from '../../servicios/detalleConteoService';
import Swal from 'sweetalert2';
import { 
    IconUsers, 
    IconMedicineSyrup, 
    IconCalendarPlus, 
    IconFileSpreadsheet, 
    IconSearch, 
    IconDeviceFloppy,
    IconDownload,
    IconChevronRight,
    IconFilter,
    IconSettings
} from '@tabler/icons-react';

const AdminPanel: React.FC = () => {
    const [activeTab, setActiveTab] = useState<'usuarios' | 'asignar' | 'reportes'>('usuarios');
    const [usuarios, setUsuarios] = useState<Usuario[]>([]);
    const [medicamentos, setMedicamentos] = useState<Medicamento[]>([]);
    const [detalles, setDetalles] = useState<DetalleConteo[]>([]);
    const [loading, setLoading] = useState(false);

    // Form states for assignment
    const [selectedUser, setSelectedUser] = useState<number | ''>('');
    const [selectedMed, setSelectedMed] = useState<number | ''>('');
    const [assignDate, setAssignDate] = useState(new Date().toLocaleDateString('en-CA'));
    const [searchTerm, setSearchTerm] = useState('');
    const [medSearchTerm, setMedSearchTerm] = useState('');

    useEffect(() => {
        loadInitialData();
    }, [activeTab]);

    const loadInitialData = async () => {
        setLoading(true);
        try {
            if (activeTab === 'usuarios') {
                const data = await getAllUsuarios();
                // Solo mostrar usuarios con Rol 1 (Farmacia)
                setUsuarios(data.filter(u => u.rol?.id === 1));
            } else if (activeTab === 'asignar') {
                const [uData, mData] = await Promise.all([getAllUsuarios(), getAllMedicamentos()]);
                // Solo mostrar usuarios con Rol 1 (Farmacia) para asignar
                setUsuarios(uData.filter(u => u.rol?.id === 1));
                setMedicamentos(mData);
            }
 else if (activeTab === 'reportes') {
                const data = await getAllDetalles();
                setDetalles(data);
            }
        } catch (error) {
            console.error(error);
            Swal.fire({
                icon: 'error',
                title: 'Error de Carga',
                text: 'No se pudieron cargar los datos necesarios.'
            });
        } finally {
            setLoading(false);
        }
    };

    const handleUpdateNumeroConteo = async (userId: number, value: number) => {
        try {
            await updateUsuario(userId, { numeroConteo: value });
            setUsuarios(prev => prev.map(u => u.id === userId ? { ...u, numeroConteo: value } : u));
            Swal.fire({
                toast: true,
                position: 'top-end',
                icon: 'success',
                title: 'Cuota actualizada',
                showConfirmButton: false,
                timer: 1500
            });
        } catch (error) {
            Swal.fire('Error', 'No se pudo actualizar la cuota', 'error');
        }
    };

    const handleCreateAssignment = async () => {
        if (!selectedUser || !selectedMed || !assignDate) {
            Swal.fire('Campos incompletos', 'Por favor selecciona un usuario, un medicamento y una fecha.', 'warning');
            return;
        }

        try {
            setLoading(true);
            const fechaHoy = new Date().toLocaleDateString('en-CA');
            const now = new Date();
            const horaActual = now.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' });

            await createPersonalizado({
                idUsuario: Number(selectedUser),
                idMedicamento: Number(selectedMed),
                fechaProgramacion: assignDate,
                fechaRegistro: fechaHoy,
                horaRegistro: horaActual
            });

            Swal.fire({
                icon: 'success',
                title: 'Asignación Exitosa',
                text: 'El medicamento ha sido programado para el usuario.',
                confirmButtonColor: '#f6952c',
                customClass: { popup: 'rounded-3xl' }
            });
            
            setSelectedMed('');
            setMedSearchTerm('');
        } catch (error) {
            Swal.fire('Error', 'No se pudo realizar la asignación personalizada.', 'error');
        } finally {
            setLoading(false);
        }
    };

    const downloadReport = () => {
        if (detalles.length === 0) return;
        
        const headers = ["ID", "Medicamento", "PLU", "Usuario", "Cantidad Contada", "Cantidad Actual", "Fecha", "Tipo"];
        const rows = detalles.map(d => [
            d.id,
            d.medicamento?.descripcion || 'N/A',
            d.medicamento?.plu || 'N/A',
            d.usuario?.usuario || 'N/A',
            d.cantidadContada ?? 'Pendiente',
            d.cantidadActual,
            d.fechaRegistro,
            d.tipoConteo
        ]);

        const csvContent = [headers, ...rows].map(e => e.join(",")).join("\n");
        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.setAttribute("href", url);
        link.setAttribute("download", `Reporte_Conteo_${new Date().toISOString().split('T')[0]}.csv`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    const filteredMeds = medicamentos.filter(m => 
        (m.descripcion?.toLowerCase() || '').includes(medSearchTerm.toLowerCase()) || 
        (m.plu || '').includes(medSearchTerm) ||
        (m.codigoGenerico || '').includes(medSearchTerm)
    ).slice(0, 10); // Limit results for performance

    return (
        <div className="max-w-7xl mx-auto space-y-8 pb-20 animate-in fade-in slide-in-from-bottom-5 duration-700">
            {/* Cabecera */}
            <div className="bg-white rounded-[2.5rem] p-8 shadow-xl shadow-gray-200/50 border border-gray-100 flex flex-col md:flex-row justify-between items-center gap-6">
                <div className="flex items-center gap-5">
                    <div className="w-16 h-16 bg-gray-900 rounded-[1.5rem] flex items-center justify-center text-white shadow-2xl shadow-gray-900/20">
                        <IconSettings size={32} />
                    </div>
                    <div>
                        <h2 className="text-3xl font-black text-gray-900 tracking-tight">PANEL DE GESTIÓN</h2>
                        <p className="text-xs font-black text-orange-500 uppercase tracking-[0.3em] flex items-center gap-2 mt-1">
                            <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></span>
                            Modo Administrador
                        </p>
                    </div>
                </div>

                <div className="flex p-1.5 bg-gray-50 rounded-2xl border border-gray-100">
                    <button 
                        onClick={() => setActiveTab('usuarios')}
                        className={`flex items-center gap-2 px-6 py-3 rounded-xl text-xs font-black transition-all ${activeTab === 'usuarios' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-400 hover:text-gray-600'}`}
                    >
                        <IconUsers size={18} /> USUARIOS
                    </button>
                    <button 
                        onClick={() => setActiveTab('asignar')}
                        className={`flex items-center gap-2 px-6 py-3 rounded-xl text-xs font-black transition-all ${activeTab === 'asignar' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-400 hover:text-gray-600'}`}
                    >
                        <IconCalendarPlus size={18} /> ASIGNAR
                    </button>
                    <button 
                        onClick={() => setActiveTab('reportes')}
                        className={`flex items-center gap-2 px-6 py-3 rounded-xl text-xs font-black transition-all ${activeTab === 'reportes' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-400 hover:text-gray-600'}`}
                    >
                        <IconFileSpreadsheet size={18} /> REPORTES
                    </button>
                </div>
            </div>

            {/* Contenido Principal */}
            <div className="bg-white rounded-[2.5rem] shadow-2xl shadow-gray-200/30 border border-gray-100 overflow-hidden min-h-[600px]">
                
                {activeTab === 'usuarios' && (
                    <div className="p-8 animate-in fade-in duration-500">
                        <div className="flex justify-between items-center mb-8">
                            <h3 className="text-xl font-black text-gray-900 uppercase">Gestión de Cuota Diaria</h3>
                            <div className="relative">
                                <IconSearch className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-300" size={18} />
                                <input 
                                    type="text" 
                                    placeholder="Buscar usuario..." 
                                    className="pl-12 pr-6 py-3 bg-gray-50 rounded-2xl border-none outline-none focus:ring-2 focus:ring-orange-500/20 transition-all font-bold text-sm w-64"
                                />
                            </div>
                        </div>

                        <div className="overflow-x-auto">
                            <table className="w-full text-left">
                                <thead>
                                    <tr className="text-gray-400 text-[10px] font-black uppercase tracking-widest border-b border-gray-50">
                                        <th className="px-4 py-6">Usuario</th>
                                        <th className="px-4 py-6">Sede</th>
                                        <th className="px-4 py-6 text-center">Cuota de Conteo (numeroConteo)</th>
                                        <th className="px-4 py-6 text-center">Acciones</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-50">
                                    {usuarios.map(u => (
                                        <tr key={u.id} className="hover:bg-gray-50/50 transition-colors group">
                                            <td className="px-4 py-6">
                                                <div className="flex items-center gap-3">
                                                    <div className="w-10 h-10 bg-orange-100 rounded-xl flex items-center justify-center text-orange-600 font-black">
                                                        {u.usuario.charAt(0).toUpperCase()}
                                                    </div>
                                                    <span className="font-black text-gray-900 uppercase tracking-tight">{u.usuario}</span>
                                                </div>
                                            </td>
                                            <td className="px-4 py-6">
                                                <span className="bg-gray-100 text-gray-500 px-3 py-1 rounded-lg text-[10px] font-black uppercase tracking-widest">{u.sede || 'Global'}</span>
                                            </td>
                                            <td className="px-4 py-6 text-center">
                                                <div className="flex justify-center items-center gap-2">
                                                    <input 
                                                        type="number" 
                                                        defaultValue={u.numeroConteo || 10}
                                                        id={`quota-${u.id}`}
                                                        className="w-20 text-center py-2 bg-gray-50 rounded-xl border-2 border-transparent focus:border-orange-500 outline-none font-black text-orange-600"
                                                    />
                                                </div>
                                            </td>
                                            <td className="px-4 py-6 text-center">
                                                <button 
                                                    onClick={() => {
                                                        const val = (document.getElementById(`quota-${u.id}`) as HTMLInputElement).value;
                                                        handleUpdateNumeroConteo(u.id, parseInt(val));
                                                    }}
                                                    className="p-3 bg-gray-900 text-white rounded-xl hover:bg-orange-600 transition-all shadow-lg shadow-gray-200 active:scale-90"
                                                >
                                                    <IconDeviceFloppy size={18} />
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}

                {activeTab === 'asignar' && (
                    <div className="p-10 animate-in fade-in duration-500">
                        <div className="max-w-2xl mx-auto space-y-8">
                            <div className="text-center">
                                <h3 className="text-2xl font-black text-gray-900 uppercase mb-2 tracking-tight">Programar Conteo Extra</h3>
                                <p className="text-gray-400 font-bold text-sm uppercase tracking-widest italic">Asigna un medicamento específico a un usuario para una fecha determinada.</p>
                            </div>

                            <div className="grid grid-cols-1 gap-6">
                                <div className="space-y-2">
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest ml-1">1. Seleccionar Usuario</label>
                                    <select 
                                        value={selectedUser}
                                        onChange={(e) => setSelectedUser(Number(e.target.value))}
                                        className="w-full p-4 bg-gray-50 rounded-2xl border-2 border-transparent focus:border-orange-500 outline-none font-bold text-gray-700 appearance-none cursor-pointer"
                                    >
                                        <option value="">Selecciona un usuario...</option>
                                        {usuarios.map(u => (
                                            <option key={u.id} value={u.id}>{u.usuario.toUpperCase()} - {u.sede || 'Sede Global'}</option>
                                        ))}
                                    </select>
                                </div>

                                <div className="space-y-2">
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest ml-1">2. Buscar Medicamento</label>
                                    <div className="relative">
                                        <IconSearch className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-300" size={18} />
                                        <input 
                                            type="text" 
                                            placeholder="Nombre, PLU o Código Genérico..."
                                            value={medSearchTerm}
                                            onChange={(e) => setMedSearchTerm(e.target.value)}
                                            className="w-full pl-12 pr-6 py-4 bg-gray-50 rounded-2xl border-2 border-transparent focus:border-orange-500 outline-none font-bold text-gray-700"
                                        />
                                    </div>
                                    {medSearchTerm && (
                                        <div className="mt-2 bg-white border border-gray-100 rounded-2xl shadow-xl p-2 space-y-1">
                                            {filteredMeds.map(m => (
                                                <button 
                                                    key={m.id}
                                                    onClick={() => {
                                                        setSelectedMed(m.id);
                                                        setMedSearchTerm(m.descripcion || '');
                                                    }}
                                                    className={`w-full text-left p-4 rounded-xl flex items-center justify-between transition-colors ${selectedMed === m.id ? 'bg-orange-500 text-white' : 'hover:bg-gray-50'}`}
                                                >
                                                    <div className="flex flex-col">
                                                        <span className={`text-sm font-black ${selectedMed === m.id ? 'text-white' : 'text-gray-900'}`}>{m.descripcion}</span>
                                                        <span className={`text-[10px] font-bold ${selectedMed === m.id ? 'text-orange-100' : 'text-gray-400'}`}>PLU: {m.plu} | Cód: {m.codigoGenerico}</span>
                                                    </div>
                                                    <IconChevronRight size={16} opacity={0.5} />
                                                </button>
                                            ))}
                                            {filteredMeds.length === 0 && <p className="p-4 text-center text-gray-400 font-bold text-xs uppercase tracking-widest">No se encontraron resultados</p>}
                                        </div>
                                    )}
                                </div>

                                <div className="space-y-2">
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest ml-1">3. Fecha de Programación</label>
                                    <input 
                                        type="date" 
                                        value={assignDate}
                                        onChange={(e) => setAssignDate(e.target.value)}
                                        className="w-full p-4 bg-gray-50 rounded-2xl border-2 border-transparent focus:border-orange-500 outline-none font-bold text-gray-700 cursor-pointer"
                                    />
                                </div>

                                <button 
                                    onClick={handleCreateAssignment}
                                    disabled={loading}
                                    className="w-full py-5 bg-gray-900 text-white rounded-[2rem] font-black text-lg uppercase tracking-widest shadow-2xl shadow-gray-200 hover:bg-orange-600 transition-all active:scale-95 flex items-center justify-center gap-3 mt-4 disabled:opacity-50"
                                >
                                    {loading ? 'Procesando...' : <><IconCalendarPlus size={24} /> Confirmar Asignación</>}
                                </button>
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === 'reportes' && (
                    <div className="p-8 animate-in fade-in duration-500">
                        <div className="flex flex-col md:flex-row justify-between items-center mb-8 gap-4">
                            <div>
                                <h3 className="text-xl font-black text-gray-900 uppercase tracking-tight">Historial de Conteos</h3>
                                <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest mt-1 italic">Visualiza todos los reportes sincronizados por los usuarios.</p>
                            </div>
                            <div className="flex items-center gap-3">
                                <button className="flex items-center gap-2 px-5 py-3 bg-gray-50 rounded-2xl text-xs font-black text-gray-500 hover:bg-gray-100 transition-all border border-gray-100">
                                    <IconFilter size={18} /> FILTRAR
                                </button>
                                <button 
                                    onClick={downloadReport}
                                    className="flex items-center gap-2 px-6 py-3 bg-green-500 text-white rounded-2xl text-xs font-black shadow-lg shadow-green-500/20 hover:bg-green-600 transition-all active:scale-95"
                                >
                                    <IconDownload size={18} /> DESCARGAR CSV
                                </button>
                            </div>
                        </div>

                        <div className="overflow-x-auto">
                            <table className="w-full text-left">
                                <thead>
                                    <tr className="text-gray-400 text-[10px] font-black uppercase tracking-widest border-b border-gray-50">
                                        <th className="px-4 py-6">Fecha</th>
                                        <th className="px-4 py-6">Usuario</th>
                                        <th className="px-4 py-6">Medicamento</th>
                                        <th className="px-4 py-6 text-center">Contado</th>
                                        <th className="px-4 py-6 text-center">Teórico</th>
                                        <th className="px-4 py-6 text-center">Tipo</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-50 text-sm">
                                    {detalles.slice(0, 50).map(d => (
                                        <tr key={d.id} className="hover:bg-gray-50/30 transition-colors">
                                            <td className="px-4 py-6 font-mono text-xs text-gray-400">{d.fechaRegistro}</td>
                                            <td className="px-4 py-6 font-black text-gray-900 uppercase text-xs">{d.usuario?.usuario}</td>
                                            <td className="px-4 py-6">
                                                <div className="flex flex-col">
                                                    <span className="font-bold text-gray-700">{d.medicamento?.descripcion}</span>
                                                    <span className="text-[9px] text-gray-400 font-bold">PLU: {d.medicamento?.plu}</span>
                                                </div>
                                            </td>
                                            <td className="px-4 py-6 text-center">
                                                <span className={`font-black ${d.cantidadContada !== null ? 'text-green-600' : 'text-amber-500 italic'}`}>
                                                    {d.cantidadContada ?? '---'}
                                                </span>
                                            </td>
                                            <td className="px-4 py-6 text-center font-bold text-gray-500">{d.cantidadActual}</td>
                                            <td className="px-4 py-6 text-center">
                                                <span className={`px-3 py-1 rounded-lg text-[9px] font-black uppercase tracking-widest border ${d.tipoConteo === 'Cíclico' ? 'bg-orange-50 text-orange-500 border-orange-100' : 'bg-purple-50 text-purple-600 border-purple-100'}`}>
                                                    {d.tipoConteo}
                                                </span>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                            {detalles.length === 0 && <p className="text-center py-20 text-gray-400 font-bold uppercase tracking-widest">No hay datos para mostrar</p>}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default AdminPanel;
