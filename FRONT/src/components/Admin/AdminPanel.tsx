import React, { useEffect, useState } from 'react';
import { getAllUsuarios, updateUsuario, Usuario } from '../../servicios/usuarioService';
import { getAllMedicamentos, Medicamento } from '../../servicios/medicamentoService';
import { createPersonalizado } from '../../servicios/personalizadoService';
import { getAllDetalles, DetalleConteo } from '../../servicios/detalleConteoService';
import Swal from 'sweetalert2';
import * as XLSX from 'xlsx';
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
    IconSettings,
    IconDatabaseImport,
    IconFilePlus
} from '@tabler/icons-react';
import { bulkImportMedicamentos } from '../../servicios/medicamentoService';
import { bulkImportInventario } from '../../servicios/inventarioService';

const AdminPanel: React.FC = () => {
    const [activeTab, setActiveTab] = useState<'usuarios' | 'asignar' | 'reportes' | 'importar'>('usuarios');
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
    const [userSearchTerm, setUserSearchTerm] = useState('');

    // Filter states for reports
    const [startDate, setStartDate] = useState(new Date().toLocaleDateString('en-CA'));
    const [endDate, setEndDate] = useState(new Date().toLocaleDateString('en-CA'));
    const [filterUser, setFilterUser] = useState<number | ''>('');

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
            Swal.fire({
                icon: 'error',
                title: 'Error de Carga',
                text: 'No se pudieron cargar los datos necesarios.'
            });
        } finally {
            setLoading(false);
        }
    };

    const handleSaveAllQuotas = async () => {
        try {
            setLoading(true);
            await Promise.all(usuarios.map(u => updateUsuario(u.id, { numeroConteo: u.numeroConteo })));

            Swal.fire({
                icon: 'success',
                title: 'Cambios Guardados',
                text: 'Todas las cuotas diarias han sido actualizadas.',
                confirmButtonColor: '#f6952c',
                customClass: { popup: 'rounded-3xl' }
            });
        } catch (error) {
            Swal.fire('Error', 'No se pudieron guardar los cambios en las cuotas', 'error');
        } finally {
            setLoading(false);
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
        let filteredData = [...detalles];

        // Aplicar filtros de fecha y usuario
        if (startDate) filteredData = filteredData.filter(d => d.fechaRegistro >= startDate);
        if (endDate) filteredData = filteredData.filter(d => d.fechaRegistro <= endDate);
        if (filterUser) filteredData = filteredData.filter(d => d.idUsuario === Number(filterUser));

        if (filteredData.length === 0) {
            Swal.fire('Sin datos', 'No hay registros que coincidan con los filtros seleccionados.', 'info');
            return;
        }

        // Mapeamos los datos con nombres de columnas legibles para Excel
        const excelData = filteredData.map(d => ({
            'ID REPORTE': d.id,
            'MEDICAMENTO': d.medicamento?.descripcion || 'N/A',
            'PLU': d.medicamento?.plu || 'N/A',
            'USUARIO': d.usuario?.usuario?.toUpperCase() || 'N/A',
            'SEDE': d.usuario?.sede?.toUpperCase() || 'GLOBAL',
            'CANT. CONTADA': d.cantidadContada === null ? 'SIN CONTAR' : d.cantidadContada,
            'CANT. TEORICA': d.cantidadActual,
            'DIFERENCIA': d.cantidadContada !== null ? (d.cantidadContada - d.cantidadActual) : 'PÉRDIDA TÉCNICA (X)',
            'FECHA': d.fechaRegistro,
            'HORA REPORTE': d.horaRegistro || 'PENDIENTE',
            'TIPO CONTEO': d.tipoConteo
        }));

        // Crear el libro de trabajo (WorkBook) y la hoja (WorkSheet)
        const worksheet = XLSX.utils.json_to_sheet(excelData);
        const workbook = XLSX.utils.book_new();
        XLSX.utils.book_append_sheet(workbook, worksheet, "Inventario");

        // Ajustar anchos de columna básicos
        const wscols = [
            { wch: 12 }, { wch: 40 }, { wch: 12 }, { wch: 15 }, { wch: 15 },
            { wch: 15 }, { wch: 15 }, { wch: 12 }, { wch: 12 }, { wch: 12 }, { wch: 15 }
        ];
        worksheet['!cols'] = wscols;

        // Descargar el archivo
        XLSX.writeFile(workbook, `REPORTE_CONTEO_${startDate}_AL_${endDate}.xlsx`);
    };

    const handleExcelImport = async (e: React.ChangeEvent<HTMLInputElement>, type: 'medicamento' | 'inventario') => {
        const file = e.target.files?.[0];
        if (!file) return;

        setLoading(true);
        const reader = new FileReader();

        reader.onload = async (evt) => {
            try {
                const data = evt.target?.result;
                const workbook = XLSX.read(data, { type: 'binary' });
                const sheetName = workbook.SheetNames[0];
                const worksheet = workbook.Sheets[sheetName];
                const jsonData = XLSX.utils.sheet_to_json(worksheet) as any[];

                if (type === 'medicamento') {
                    const mapped = jsonData.map(row => ({
                        plu: String(row.PLU || row.plu || ''),
                        descripcion: row.DESCRIPCION || row.descripcion || row.NOMBRE || '',
                        codigoGenerico: String(row['CODIGO GENERICO'] || row.codigoGenerico || ''),
                        laboratorio: row.LABORATORIO || row.laboratorio || ''
                    })).filter(item => item.plu);

                    await bulkImportMedicamentos(mapped);
                    Swal.fire('Éxito', `${mapped.length} medicamentos procesados correctamente.`, 'success');
                } else {
                    const mapped = jsonData.map(row => ({
                        sede: String(row.SEDE || row.sede || row.USUARIO || ''),
                        plu: String(row.PLU || row.plu || ''),
                        cantidad: parseInt(row.SALDO || row.saldo || row.CANTIDAD || '0')
                    })).filter(item => item.plu && item.sede);

                    await bulkImportInventario(mapped);
                    Swal.fire('Éxito', `${mapped.length} registros de inventario sincronizados.`, 'success');
                }
                loadInitialData();
            } catch (error) {
                Swal.fire('Error', 'No se pudo procesar el archivo Excel. Verifica el formato.', 'error');
            } finally {
                setLoading(false);
                if (e.target) e.target.value = '';
            }
        };

        reader.readAsBinaryString(file);
    };

    const filteredMeds = medicamentos.filter(m =>
        (m.descripcion?.toLowerCase() || '').includes(medSearchTerm.toLowerCase()) ||
        (m.plu || '').includes(medSearchTerm) ||
        (m.codigoGenerico || '').includes(medSearchTerm)
    ).slice(0, 10); // Limit results for performance

    const filteredUsers = (usuarios || []).filter(u =>
        u.usuario?.toLowerCase().includes(userSearchTerm.toLowerCase()) ||
        (u.sede || '').toLowerCase().includes(userSearchTerm.toLowerCase())
    );

    return (
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-6 md:space-y-8 pb-20">
            {/* Cabecera */}
            <div className="bg-white rounded-3xl sm:rounded-[2.5rem] p-6 sm:p-8 shadow-xl shadow-gray-200/50 border border-gray-100 flex flex-col md:flex-row justify-between items-center gap-6">
                <div className="flex items-center gap-4 sm:gap-5">
                    <div className="w-12 h-12 sm:w-16 sm:h-16 bg-gray-900 rounded-2xl sm:rounded-[1.5rem] flex items-center justify-center text-white shadow-2xl shadow-gray-900/20">
                        <IconSettings size={28} className="sm:size-32" />
                    </div>
                    <div>
                        <h2 className="text-2xl sm:text-3xl font-black text-gray-900 tracking-tight">PANEL DE GESTIÓN</h2>
                        <p className="text-[10px] sm:text-xs font-black text-orange-500 uppercase tracking-[0.2em] sm:tracking-[0.3em] flex items-center gap-2 mt-1">
                            <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></span>
                            Modo Administrador
                        </p>
                    </div>
                </div>

                {/* Pestañas / Navegación Interna */}
                <div className="w-full px-4 sm:px-8 pb-4 border-b border-gray-100 overflow-x-auto no-scrollbar">
                    <div className="flex gap-4 min-w-max">
                        <button
                            onClick={() => setActiveTab('usuarios')}
                            className={`flex items-center gap-2 px-6 py-3 rounded-xl text-xs font-black transition-all ${activeTab === 'usuarios' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-400 hover:text-gray-600'}`}
                        >
                            <IconUsers size={18} /> GESTIÓN DE USUARIOS
                        </button>
                        <button
                            onClick={() => setActiveTab('asignar')}
                            className={`flex items-center gap-2 px-6 py-3 rounded-xl text-xs font-black transition-all ${activeTab === 'asignar' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-400 hover:text-gray-600'}`}
                        >
                            <IconCalendarPlus size={18} /> ASIGNACIÓN DIARIA
                        </button>
                        <button
                            onClick={() => setActiveTab('reportes')}
                            className={`flex items-center gap-2 px-6 py-3 rounded-xl text-xs font-black transition-all ${activeTab === 'reportes' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-400 hover:text-gray-600'}`}
                        >
                            <IconFileSpreadsheet size={18} /> REPORTES
                        </button>
                        <button
                            onClick={() => setActiveTab('importar')}
                            className={`flex items-center gap-2 px-6 py-3 rounded-xl text-xs font-black transition-all ${activeTab === 'importar' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-400 hover:text-gray-600'}`}
                        >
                            <IconDatabaseImport size={18} /> IMPORTACIÓN
                        </button>
                    </div>
                </div>
            </div>

            {/* Contenido Principal */}
            <div className="bg-white rounded-3xl sm:rounded-[2.5rem] shadow-2xl shadow-gray-200/30 border border-gray-100 overflow-hidden min-h-[400px] md:min-h-[600px]">

                {activeTab === 'usuarios' && (
                    <div className="p-4 sm:p-8">
                        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-8">
                            <div>
                                <h3 className="text-xl font-black text-gray-900 uppercase">Gestión de Cuota Diaria</h3>
                                <p className="text-[10px] text-gray-400 font-bold uppercase tracking-widest mt-1">Configura cuántos medicamentos debe contar cada usuario al día.</p>
                            </div>
                            <button
                                onClick={handleSaveAllQuotas}
                                disabled={loading}
                                className="flex items-center gap-3 px-8 py-4 bg-orange-500 text-white rounded-2xl font-black text-xs uppercase tracking-widest shadow-xl shadow-orange-500/20 hover:bg-orange-600 transition-all active:scale-95 disabled:opacity-50"
                            >
                                <IconDeviceFloppy size={20} /> GUARDAR
                            </button>
                        </div>

                        {/* Buscador de Usuarios */}
                        <div className="mb-6 relative">
                            <IconSearch className="absolute left-5 top-1/2 -translate-y-1/2 text-gray-300" size={20} />
                            <input
                                type="text"
                                placeholder="Buscar por usuario o sede..."
                                value={userSearchTerm}
                                onChange={(e) => setUserSearchTerm(e.target.value)}
                                className="w-full pl-14 pr-6 py-4 bg-white rounded-2xl border-2 border-gray-100 focus:border-orange-500 outline-none font-bold text-gray-700 shadow-sm transition-all"
                            />
                        </div>

                        {/* Vista de Escritorio (Tabla) */}
                        <div className="hidden md:block bg-white rounded-[2.5rem] shadow-sm border border-gray-100 overflow-hidden">
                            <div className="overflow-x-auto">
                                <table className="w-full">
                                    <thead>
                                        <tr className="bg-gray-50/50">
                                            <th className="px-8 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest italic">Usuario / Sede</th>
                                            <th className="px-8 py-5 text-center text-[10px] font-black text-gray-400 uppercase tracking-widest italic">Cuota Diaria</th>

                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-gray-50">
                                        {filteredUsers.filter(u => u.rol?.id === 1).map(u => (
                                            <tr key={u.id} className="hover:bg-gray-50/30 transition-colors">
                                                <td className="px-8 py-6">
                                                    <div className="flex items-center gap-4">
                                                        <div className="w-10 h-10 bg-orange-100 text-orange-600 rounded-xl flex items-center justify-center font-black">
                                                            {u.usuario.charAt(0).toUpperCase()}
                                                        </div>
                                                        <div>
                                                            <div className="font-black text-gray-900 text-sm uppercase">{u.usuario}</div>
                                                            <div className="text-[10px] font-bold text-gray-400 tracking-widest uppercase">Sede: {u.sede || 'Global'}</div>
                                                        </div>
                                                    </div>
                                                </td>
                                                <td className="px-8 py-6 text-center">
                                                    <div className="flex justify-center items-center gap-2">
                                                        <input
                                                            type="number"
                                                            min="0"
                                                            value={u.numeroConteo || 0}
                                                            onChange={(e) => {
                                                                const val = Math.max(0, parseInt(e.target.value) || 0);
                                                                setUsuarios(prev => prev.map(user => user.id === u.id ? { ...user, numeroConteo: val } : user));
                                                            }}
                                                            className="w-24 text-center py-3 bg-gray-50 rounded-xl border-2 border-transparent focus:border-orange-500 outline-none font-black text-orange-600 text-lg shadow-inner"
                                                        />
                                                    </div>
                                                </td>

                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>

                        {/* Vista Móvil (Tarjetas) */}
                        <div className="md:hidden space-y-4">
                            {filteredUsers.filter(u => u.rol?.id === 1).map(u => (
                                <div key={u.id} className="bg-white rounded-2xl p-4 shadow-sm border border-gray-100">
                                    <div className="flex items-center gap-3 mb-4">
                                        <div className="w-10 h-10 bg-orange-100 text-orange-600 rounded-xl flex items-center justify-center font-black text-lg">
                                            {u.usuario.charAt(0).toUpperCase()}
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <div className="font-black text-gray-900 text-sm uppercase truncate">{u.usuario}</div>
                                            <div className="text-[10px] font-bold text-gray-400 tracking-widest uppercase mt-0.5">Sede: {u.sede || 'Global'}</div>
                                        </div>
                                        <button className="p-2 text-gray-300 hover:text-orange-500 transition-colors">
                                            <IconSettings size={18} />
                                        </button>
                                    </div>

                                    <div className="pt-4 border-t border-gray-50">
                                        <div className="flex flex-col gap-2">
                                            <label className="text-[9px] font-black text-gray-400 uppercase tracking-widest">Cuota Diaria</label>
                                            <input
                                                type="number"
                                                min="0"
                                                value={u.numeroConteo || 0}
                                                onChange={(e) => {
                                                    const val = Math.max(0, parseInt(e.target.value) || 0);
                                                    setUsuarios(prev => prev.map(user => user.id === u.id ? { ...user, numeroConteo: val } : user));
                                                }}
                                                className="w-full py-3 bg-gray-50 rounded-xl border-2 border-transparent focus:border-orange-500 outline-none font-black text-orange-600 text-xl text-center shadow-inner"
                                            />
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {activeTab === 'asignar' && (
                    <div className="p-4 sm:p-10">
                        <div className="max-w-2xl mx-auto space-y-8">
                            <div className="text-center">
                                <h3 className="text-2xl font-black text-gray-900 uppercase mb-2 tracking-tight">Programar Conteo Extra</h3>
                                <p className="text-gray-400 font-bold text-sm uppercase tracking-widest italic">Asigna un medicamento específico a un usuario para una fecha determinada.</p>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
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
                    <div className="p-4 sm:p-8">
                        <div className="max-w-4xl mx-auto space-y-12">
                            <div className="text-center">
                                <div className="inline-flex p-4 bg-orange-50 rounded-3xl text-orange-500 mb-6">
                                    <IconFileSpreadsheet size={48} />
                                </div>
                                <h3 className="text-3xl font-black text-gray-900 uppercase tracking-tight">Generación de Informes</h3>
                                <p className="text-gray-400 font-bold text-xs uppercase tracking-[0.2em] mt-2 italic">Filtra la información por fechas o usuarios para descargar el reporte detallado.</p>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 bg-gray-50/50 p-8 rounded-[2.5rem] border border-gray-100">
                                <div className="space-y-2">
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest ml-1">Desde</label>
                                    <input
                                        type="date"
                                        value={startDate}
                                        onChange={(e) => setStartDate(e.target.value)}
                                        className="w-full p-4 bg-white rounded-2xl border-2 border-transparent focus:border-orange-500 outline-none font-bold text-gray-700 shadow-sm"
                                    />
                                </div>
                                <div className="space-y-2">
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest ml-1">Hasta</label>
                                    <input
                                        type="date"
                                        value={endDate}
                                        onChange={(e) => setEndDate(e.target.value)}
                                        className="w-full p-4 bg-white rounded-2xl border-2 border-transparent focus:border-orange-500 outline-none font-bold text-gray-700 shadow-sm"
                                    />
                                </div>
                                <div className="space-y-2">
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest ml-1">Usuario / Sede</label>
                                    <select
                                        value={filterUser}
                                        onChange={(e) => setFilterUser(e.target.value ? Number(e.target.value) : '')}
                                        className="w-full p-4 bg-white rounded-2xl border-2 border-transparent focus:border-orange-500 outline-none font-bold text-gray-700 shadow-sm appearance-none cursor-pointer"
                                    >
                                        <option value="">TODOS LOS USUARIOS</option>
                                        {usuarios.map(u => (
                                            <option key={u.id} value={u.id}>{u.usuario.toUpperCase()} ({u.sede || 'Global'})</option>
                                        ))}
                                    </select>
                                </div>
                            </div>

                            <div className="flex flex-col items-center gap-6">
                                <button
                                    onClick={downloadReport}
                                    className="px-12 py-6 bg-green-500 text-white rounded-[2rem] font-black text-lg uppercase tracking-widest shadow-2xl shadow-green-500/20 hover:bg-green-600 transition-all active:scale-95 flex items-center gap-4 group"
                                >
                                    <IconDownload size={28} className="group-hover:translate-y-1 transition-transform" />
                                    Descargar Informe
                                </button>

                                <div className="flex items-center gap-8 py-6 border-t border-gray-100 w-full justify-center">
                                    <div className="text-center">
                                        <p className="text-[10px] font-black text-gray-300 uppercase tracking-widest mb-1">Total Registros</p>
                                        <p className="text-xl font-black text-gray-900">{detalles.length}</p>
                                    </div>
                                    <div className="w-px h-8 bg-gray-100"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === 'importar' && (
                    <div className="p-4 sm:p-12">
                        <div className="max-w-4xl mx-auto">
                            <div className="text-center mb-12">
                                <div className="inline-flex p-5 bg-blue-50 text-blue-500 rounded-[2rem] mb-6">
                                    <IconDatabaseImport size={56} />
                                </div>
                                <h3 className="text-3xl font-black text-gray-900 uppercase tracking-tight">Sincronización de Datos Maestra</h3>
                                <p className="text-gray-400 font-bold text-sm uppercase tracking-widest mt-2 italic">Carga tus archivos Excel para actualizar el catálogo e inventario global.</p>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-10">
                                {/* Importar Catálogo */}
                                <div className="bg-gray-50/50 rounded-[2.5rem] p-10 border-2 border-dashed border-gray-200 hover:border-blue-400 transition-all group relative overflow-hidden">
                                    <div className="relative z-10 flex flex-col items-center text-center">
                                        <div className="w-16 h-16 bg-blue-500 text-white rounded-2xl flex items-center justify-center mb-6 shadow-xl shadow-blue-500/20 group-hover:scale-110 transition-transform">
                                            <IconFilePlus size={32} />
                                        </div>
                                        <h4 className="text-xl font-black text-gray-900 uppercase mb-3">Catálogo Base</h4>
                                        <p className="text-xs text-gray-400 font-bold leading-relaxed mb-8 uppercase tracking-tighter">Sincroniza descripciones, códigos genéricos y laboratorios vía PLU.</p>

                                        <label className="w-full">
                                            <input
                                                type="file"
                                                accept=".xlsx, .xls"
                                                className="hidden"
                                                onChange={(e) => handleExcelImport(e, 'medicamento')}
                                            />
                                            <div className="bg-gray-900 text-white py-4 px-8 rounded-2xl font-black text-xs uppercase tracking-widest cursor-pointer hover:bg-blue-600 transition-colors shadow-lg active:scale-95">
                                                Seleccionar Excel
                                            </div>
                                        </label>
                                    </div>
                                    <div className="absolute top-0 right-0 p-4 opacity-5 group-hover:opacity-10 transition-opacity">
                                        <IconFileSpreadsheet size={120} />
                                    </div>
                                </div>

                                {/* Importar Inventario */}
                                <div className="bg-gray-50/50 rounded-[2.5rem] p-10 border-2 border-dashed border-gray-200 hover:border-green-400 transition-all group relative overflow-hidden">
                                    <div className="relative z-10 flex flex-col items-center text-center">
                                        <div className="w-16 h-16 bg-green-500 text-white rounded-2xl flex items-center justify-center mb-6 shadow-xl shadow-green-500/20 group-hover:scale-110 transition-transform">
                                            <IconDownload size={32} />
                                        </div>
                                        <h4 className="text-xl font-black text-gray-900 uppercase mb-3">Saldos de Inventario</h4>
                                        <p className="text-xs text-gray-400 font-bold leading-relaxed mb-8 uppercase tracking-tighter">Cruza los saldos actuales con usuarios y sedes automáticamente.</p>

                                        <label className="w-full">
                                            <input
                                                type="file"
                                                accept=".xlsx, .xls"
                                                className="hidden"
                                                onChange={(e) => handleExcelImport(e, 'inventario')}
                                            />
                                            <div className="bg-gray-900 text-white py-4 px-8 rounded-2xl font-black text-xs uppercase tracking-widest cursor-pointer hover:bg-green-600 transition-colors shadow-lg active:scale-95">
                                                Seleccionar Excel
                                            </div>
                                        </label>
                                    </div>
                                    <div className="absolute top-0 right-0 p-4 opacity-5 group-hover:opacity-10 transition-opacity">
                                        <IconFileSpreadsheet size={120} />
                                    </div>
                                </div>
                            </div>

                            <div className="mt-12 bg-orange-50/50 p-6 rounded-3xl border border-orange-100 italic">
                                <p className="text-[10px] text-orange-600 font-black uppercase tracking-[0.15em] mb-2 font-bold">Nota Técnica del Mapeo:</p>
                                <p className="text-xs text-orange-800 leading-relaxed font-bold uppercase opacity-70">
                                    El sistema utiliza la columna <strong className="text-orange-950">PLU</strong> como identificador maestro. Para inventario, asegúrate de que la columna <strong className="text-orange-950">SEDE</strong> coincida con los códigos configurados en la gestión de usuarios.
                                </p>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default AdminPanel;
