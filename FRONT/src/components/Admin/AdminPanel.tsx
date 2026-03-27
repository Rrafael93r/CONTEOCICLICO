import React, { useEffect, useState } from 'react';
import { getAllUsuarios, updateUsuario, Usuario } from '../../servicios/usuarioService';
import { getAllMedicamentos, Medicamento, bulkImportMedicamentos, bulkUpdateInventory, resetCycleByUsuario } from '../../servicios/medicamentoService';
import { createPersonalizado } from '../../servicios/personalizadoService';
import { getAllDetalles, DetalleConteo } from '../../servicios/detalleConteoService';
import Swal from 'sweetalert2';
import * as XLSX from 'xlsx';
import axios from '../../servicios/axiosConfig';
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
                const [uData, mData] = await Promise.all([getAllUsuarios(), getAllMedicamentos()]);
                // Solo mostrar usuarios con Rol 1 (Farmacia)
                setUsuarios(uData.filter(u => u.rol?.id === 1));
                setMedicamentos(mData);
            } else if (activeTab === 'asignar') {
                const [uData, mData] = await Promise.all([getAllUsuarios(), getAllMedicamentos()]);
                // Solo mostrar usuarios con Rol 1 (Farmacia) para asignar
                setUsuarios(uData.filter(u => u.rol?.id === 1));
                setMedicamentos(mData);
            }
            else if (activeTab === 'reportes') {
                const data = await getAllDetalles(undefined, undefined, startDate, endDate);
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

    const handleResetCycle = async (idUsuario: number, userName: string) => {
        const result = await Swal.fire({
            title: '¿Reiniciar Ciclo?',
            text: `Se habilitarán todos los medicamentos de ${userName.toUpperCase()} que ya fueron marcados como contados para que vuelvan a aparecer en el ciclo.`,
            icon: 'warning',
            showCancelButton: true,
            confirmButtonColor: '#f6952c',
            confirmButtonText: 'Sí, reiniciar todo',
            cancelButtonText: 'Cancelar'
        });

        if (!result.isConfirmed) return;

        try {
            setLoading(true);
            await resetCycleByUsuario(idUsuario);
            Swal.fire('Ciclo Reiniciado', 'Los medicamentos ahora están disponibles para contar nuevamente.', 'success');
            loadInitialData(); // Refresh counts
        } catch (error) {
            Swal.fire('Error', 'No se pudo reiniciar el ciclo.', 'error');
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

        const controller = new AbortController();

        Swal.fire({
            title: '<span class="text-2xl font-black text-gray-900 uppercase italic">Procesando Datos</span>',
            html: `
                <div class="py-10">
                    <div class="w-20 h-20 border-8 border-orange-500 border-t-transparent rounded-full animate-spin mx-auto mb-6 shadow-2xl shadow-orange-500/20"></div>
                    <p class="text-gray-500 font-bold uppercase tracking-widest text-xs animate-pulse">Sincronización por Código de Sede...</p>
                    <p class="text-gray-400 text-[10px] mt-2 italic px-8">Estamos vinculando los registros usando el número único de sede para máxima precisión.</p>
                </div>
            `,
            allowOutsideClick: false,
            showCancelButton: true,
            cancelButtonText: 'DETENER OPERACIÓN',
            confirmButtonColor: '#f6952c',
            customClass: {
                popup: 'rounded-[3rem] border-none shadow-2xl overflow-hidden bg-white',
                cancelButton: 'rounded-2xl font-black text-[10px] uppercase tracking-widest px-6 py-4'
            },
            showConfirmButton: false
        }).then((result) => {
            if (result.isConfirmed) {
                // Not relevant for showConfirmButton: false
            } else if (result.isDismissed && result.dismiss === Swal.DismissReason.cancel) {
                controller.abort();
                Swal.fire({
                    icon: 'info',
                    title: 'Operación Abortada',
                    text: 'Se ha cancelado la importación. No se realizaron cambios permanentes.',
                    confirmButtonColor: '#111827',
                    customClass: { popup: 'rounded-[2rem]' }
                });
            }
        });

        const reader = new FileReader();
        reader.onload = async (evt) => {
            try {
                const data = evt.target?.result;
                const workbook = XLSX.read(data, { type: 'binary' });
                const sheetName = workbook.SheetNames[0];
                const worksheet = workbook.Sheets[sheetName];
                const jsonData = XLSX.utils.sheet_to_json(worksheet) as any[];

                if (type === 'medicamento') {
                    let currentUsers = [...usuarios];
                    if (currentUsers.length === 0) {
                        currentUsers = await getAllUsuarios();
                        setUsuarios(currentUsers);
                    }

                    const mapped = jsonData.map((row, index) => {
                        const rawSede = row.Sede || row.sede || row.SEDE || row['Sede '] || row['SEDE '];
                        const sedeCode = String(rawSede || '').trim();

                        const foundUser = currentUsers.find(u => {
                            const uSedeNum = u.sede ? parseInt(String(u.sede).trim(), 10) : NaN;
                            const rowSedeNum = sedeCode ? parseInt(sedeCode, 10) : NaN;
                            if (!isNaN(uSedeNum) && !isNaN(rowSedeNum) && uSedeNum === rowSedeNum) return true;
                            if (u.sede && String(u.sede).toUpperCase() === sedeCode.toUpperCase()) return true;
                            return false;
                        });

                        return {
                            plu: String(row.PLU || row.plu || ''),
                            descripcion: String(row.Descripcion || row.descripcion || row.DESCRIPCION || ''),
                            codigoGenerico: String(row.Generico || row.generico || row['CODIGO GENERICO'] || ''),
                            idUsuario: foundUser?.id,
                            inventario: parseInt(row.Inventario || row.inventario || '0'),
                            costo: parseFloat(row.Costo || row.costo || '0'),
                            costoTotal: parseFloat(row['Costo total'] || row.costoTotal || '0'),
                            laboratorio: String(row.Laboratorio || row.laboratorio || row.LABORATORIO || 'N/A')
                        };
                    }).filter(item => item.plu);

                    if (mapped.some(item => !item.idUsuario)) {
                        Swal.fire({
                            icon: 'error',
                            title: '<span class="text-xl font-black text-red-600 uppercase">Usuarios no encontrados</span>',
                            text: 'Algunos registros del Excel no pudieron vincularse a un usuario existente. Revisa los códigos de sede.',
                            confirmButtonColor: '#dc2626',
                            customClass: { popup: 'rounded-[2rem]' }
                        });
                        return;
                    }

                    await bulkImportMedicamentos(mapped);

                    Swal.fire({
                        icon: 'success',
                        title: '<span class="text-xl font-black text-gray-900 uppercase">¡Sincronización Exitosa!</span>',
                        text: `Se han vinculado ${mapped.length} medicamentos correctamente.`,
                        confirmButtonColor: '#f6952c',
                        customClass: { popup: 'rounded-[2rem]' }
                    });
                } else {
                    const mapped = jsonData.map(row => ({
                        sede: String(row.SEDE || row.sede || row.USUARIO || row.Punto || ''),
                        plu: String(row.PLU || row.plu || ''),
                        cantidad: parseInt(row.SALDO || row.saldo || row.CANTIDAD || row.Inventario || '0')
                    })).filter(item => item.plu && item.sede);

                    await bulkUpdateInventory(mapped);

                    Swal.fire({
                        icon: 'success',
                        title: '<span class="text-xl font-black text-gray-900 uppercase">¡Inventario Sincronizado!</span>',
                        text: `Se actualizaron ${mapped.length} saldos en la red.`,
                        confirmButtonColor: '#f6952c',
                        customClass: { popup: 'rounded-[2rem]' }
                    });
                }
                loadInitialData();
            } catch (error: any) {
                if (error.name === 'CanceledError') return;
                Swal.fire({
                    icon: 'error',
                    title: 'Fallo en Importación',
                    text: 'El archivo Excel no tiene el formato correcto.',
                    confirmButtonColor: '#dc2626'
                });
            } finally {
                if (e.target) e.target.value = '';
            }
        };
        reader.readAsBinaryString(file);
    };

    const downloadTemplate = (type: 'medicamento' | 'inventario') => {
        const headers = type === 'medicamento'
            ? [['Sede', 'Generico', 'PLU', 'Descripcion', 'Inventario', 'Costo', 'Costo total', 'Laboratorio']]
            : [['SEDE', 'PLU']];

        const worksheet = XLSX.utils.aoa_to_sheet(headers);
        const workbook = XLSX.utils.book_new();
        XLSX.utils.book_append_sheet(workbook, worksheet, "Plantilla");

        const wscols = type === 'medicamento'
            ? [{ wch: 10 }, { wch: 15 }, { wch: 15 }, { wch: 40 }, { wch: 10 }, { wch: 10 }, { wch: 15 }, { wch: 20 }]
            : [{ wch: 15 }, { wch: 15 }];
        worksheet['!cols'] = wscols;

        XLSX.writeFile(workbook, `PLANTILLA_${type.toUpperCase()}.xlsx`);
    };

    const filteredMeds = medicamentos.filter(m =>
        (m.descripcion?.toLowerCase() || '').includes(medSearchTerm.toLowerCase()) ||
        (m.plu || '').includes(medSearchTerm) ||
        (m.codigoGenerico || '').includes(medSearchTerm)
    ).slice(0, 10);

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
                        <IconSettings size={28} />
                    </div>
                    <div>
                        <h2 className="text-2xl sm:text-3xl font-black text-gray-900 tracking-tight">PANEL DE GESTIÓN</h2>
                        <p className="text-[10px] sm:text-xs font-black text-orange-500 uppercase tracking-[0.2em] sm:tracking-[0.3em] flex items-center gap-2 mt-1">
                            <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></span>
                            Modo Administrador
                        </p>
                    </div>
                </div>

                <div className="w-full md:w-auto overflow-x-auto no-scrollbar">
                    <div className="flex bg-gray-50 p-2 rounded-2xl gap-2 min-w-max">
                        {(['usuarios', 'asignar', 'reportes', 'importar'] as const).map(tab => (
                            <button
                                key={tab}
                                onClick={() => setActiveTab(tab)}
                                className={`flex items-center gap-2 px-6 py-3 rounded-xl text-xs font-black transition-all ${activeTab === tab ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-400 hover:text-gray-600'}`}
                            >
                                {tab === 'usuarios' && <IconUsers size={18} />}
                                {tab === 'asignar' && <IconCalendarPlus size={18} />}
                                {tab === 'reportes' && <IconFileSpreadsheet size={18} />}
                                {tab === 'importar' && <IconDatabaseImport size={18} />}
                                {tab.toUpperCase()}
                            </button>
                        ))}
                    </div>
                </div>
            </div>

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
                                className="flex items-center gap-2 px-8 py-4 bg-orange-500 text-white rounded-2xl font-black text-xs uppercase tracking-widest shadow-xl shadow-orange-500/20 hover:bg-orange-600 transition-all active:scale-95 disabled:opacity-50"
                            >
                                <IconDeviceFloppy size={20} /> GUARDAR CAMBIOS
                            </button>
                        </div>

                        <div className="mb-6 relative">
                            <IconSearch className="absolute left-5 top-1/2 -translate-y-1/2 text-gray-300" size={20} />
                            <input
                                type="text"
                                placeholder="Buscar por sede..."
                                value={userSearchTerm}
                                onChange={(e) => setUserSearchTerm(e.target.value)}
                                className="w-full pl-14 pr-6 py-4 bg-white rounded-2xl border-2 border-gray-100 focus:border-orange-500 outline-none font-bold text-gray-700 shadow-sm transition-all"
                            />
                        </div>

                        <div className="hidden md:block bg-white rounded-[2.5rem] shadow-sm border border-gray-100 overflow-hidden">
                            <div className="overflow-x-auto">
                                <table className="w-full">
                                    <thead>
                                        <tr className="bg-gray-50/50">
                                            <th className="px-8 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest italic">Usuario / Sede</th>
                                            <th className="px-8 py-5 text-center text-[10px] font-black text-gray-400 uppercase tracking-widest italic">Pendientes Por Contar</th>
                                            <th className="px-8 py-5 text-center text-[10px] font-black text-gray-400 uppercase tracking-widest italic">Cuota Diaria</th>
                                            <th className="px-8 py-5 text-right text-[10px] font-black text-gray-400 uppercase tracking-widest italic">Acciones</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-gray-50">
                                        {filteredUsers.map(u => (
                                            <tr key={u.id} className="hover:bg-gray-50/30 transition-colors">
                                                <td className="px-8 py-6">
                                                    <div className="flex items-center gap-4">
                                                        <div className="w-10 h-10 bg-orange-100 text-orange-600 rounded-xl flex items-center justify-center font-black">
                                                            {u.usuario?.charAt(0).toUpperCase()}
                                                        </div>
                                                        <div>
                                                            <div className="font-black text-gray-900 text-sm uppercase">{u.usuario}</div>
                                                            <div className="text-[10px] font-bold text-gray-400 tracking-widest uppercase">Sede: {u.sede || 'Global'}</div>
                                                        </div>
                                                    </div>
                                                </td>
                                                <td className="px-8 py-6 text-center">
                                                    <span className="text-sm font-black text-blue-600 bg-blue-50 px-3 py-1 rounded-lg">
                                                        {medicamentos.filter(m => m.idUsuario === u.id && m.estadoDelConteo?.toLowerCase() === 'no').length}
                                                    </span>
                                                </td>
                                                <td className="px-8 py-6 text-center">
                                                    <input
                                                        type="number"
                                                        min="0"
                                                        value={u.numeroConteo || 0}
                                                        onChange={(e) => {
                                                            const val = Math.max(0, parseInt(e.target.value) || 0);
                                                            setUsuarios(prev => prev.map(user => user.id === u.id ? { ...user, numeroConteo: val } : user));
                                                        }}
                                                        className="w-24 text-center py-3 bg-gray-50 rounded-xl border-2 border-transparent focus:border-orange-500 outline-none font-black text-orange-600 text-lg"
                                                    />
                                                </td>
                                                <td className="px-8 py-6 text-right">
                                                    <button
                                                        onClick={() => handleResetCycle(u.id, u.usuario)}
                                                        disabled={loading}
                                                        className="px-4 py-2 bg-gray-100 text-gray-500 hover:bg-orange-500 hover:text-white rounded-xl text-[9px] font-black uppercase transition-all"
                                                    >
                                                        Reiniciar Ciclo
                                                    </button>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>

                        <div className="md:hidden space-y-4">
                            {filteredUsers.map(u => (
                                <div key={u.id} className="bg-white rounded-2xl p-4 shadow-sm border border-gray-100">
                                    <div className="flex justify-between items-start mb-4">
                                        <div className="flex items-center gap-3">
                                            <div className="w-10 h-10 bg-orange-100 text-orange-600 rounded-xl flex items-center justify-center font-black">
                                                {u.usuario?.charAt(0).toUpperCase()}
                                            </div>
                                            <div>
                                                <div className="font-black text-gray-900 text-sm uppercase">{u.usuario}</div>
                                                <div className="text-[10px] font-bold text-gray-400 tracking-widest uppercase">Sede: {u.sede || 'Global'}</div>
                                            </div>
                                        </div>
                                        <span className="text-[10px] font-black text-blue-600 bg-blue-50 px-2 py-1 rounded-lg">
                                            {medicamentos.filter(m => m.idUsuario === u.id && m.estadoDelConteo?.toLowerCase() === 'no').length} pendientes
                                        </span>
                                    </div>
                                    <div className="flex items-center justify-between gap-4 pt-4 border-t border-gray-50">
                                        <div className="flex-1">
                                            <p className="text-[9px] font-black text-gray-400 uppercase mb-1">Cuota Diaria</p>
                                            <input
                                                type="number"
                                                min="0"
                                                value={u.numeroConteo || 0}
                                                onChange={(e) => {
                                                    const val = Math.max(0, parseInt(e.target.value) || 0);
                                                    setUsuarios(prev => prev.map(user => user.id === u.id ? { ...user, numeroConteo: val } : user));
                                                }}
                                                className="w-full py-2 bg-gray-50 rounded-xl text-center font-black text-orange-600"
                                            />
                                        </div>
                                        <button
                                            onClick={() => handleResetCycle(u.id, u.usuario)}
                                            disabled={loading}
                                            className="px-4 py-3 bg-gray-900 text-white rounded-xl text-[9px] font-black uppercase"
                                        >
                                            Reiniciar
                                        </button>
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

                            <div className="grid grid-cols-1 gap-6">
                                <div className="space-y-2">
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest ml-1">1. Seleccionar Usuario</label>
                                    <select
                                        value={selectedUser}
                                        onChange={(e) => setSelectedUser(Number(e.target.value))}
                                        className="w-full p-4 bg-gray-50 rounded-2xl border-2 border-transparent focus:border-orange-500 outline-none font-bold text-gray-700 cursor-pointer"
                                    >
                                        <option value="">Selecciona un usuario...</option>
                                        {usuarios.map(u => (
                                            <option key={u.id} value={u.id}>{u.usuario.toUpperCase()} - {u.sede || 'Global'}</option>
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
                                        <div className="mt-2 bg-white border border-gray-100 rounded-2xl shadow-xl p-2 max-h-60 overflow-y-auto">
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
                                                        <span className="text-sm font-black">{m.descripcion}</span>
                                                        <span className="text-[10px] font-bold opacity-70">PLU: {m.plu} | Sede: {usuarios.find(u => u.id === m.idUsuario)?.sede || 'N/A'}</span>
                                                    </div>
                                                </button>
                                            ))}
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
                                    className="w-full py-5 bg-gray-900 text-white rounded-[2rem] font-black uppercase tracking-widest shadow-xl flex items-center justify-center gap-3 mt-4 disabled:opacity-50"
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
                                <p className="text-gray-400 font-bold text-xs uppercase tracking-widest mt-2 italic">Descarga el reporte detallado de los conteos realizados.</p>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 bg-gray-50 p-8 rounded-[2.5rem]">
                                <div className="space-y-2">
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest">Desde</label>
                                    <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} className="w-full p-4 bg-white rounded-2xl border-none shadow-sm font-bold" />
                                </div>
                                <div className="space-y-2">
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest">Hasta</label>
                                    <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)} className="w-full p-4 bg-white rounded-2xl border-none shadow-sm font-bold" />
                                </div>
                                <div className="space-y-2">
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest">Sede</label>
                                    <select value={filterUser} onChange={e => setFilterUser(e.target.value ? Number(e.target.value) : '')} className="w-full p-4 bg-white rounded-2xl border-none shadow-sm font-bold">
                                        <option value="">TODAS</option>
                                        {usuarios.map(u => <option key={u.id} value={u.id}>{u.usuario.toUpperCase()} ({u.sede})</option>)}
                                    </select>
                                </div>
                            </div>

                            <div className="flex flex-col items-center gap-6">
                                <button onClick={downloadReport} className="px-12 py-6 bg-green-500 text-white rounded-[2rem] font-black uppercase tracking-widest shadow-xl flex items-center gap-4 hover:bg-green-600 transition-all">
                                    <IconDownload size={28} /> Descargar Informe (.xlsx)
                                </button>
                                <div className="text-center">
                                    <p className="text-[10px] font-black text-gray-300 uppercase tracking-widest">Total Registros Filtrados</p>
                                    <p className="text-xl font-black text-gray-900">{detalles.length}</p>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === 'importar' && (
                    <div className="p-4 sm:p-12">
                        <div className="max-w-4xl mx-auto text-center">
                            <div className="inline-flex p-5 bg-blue-50 text-blue-500 rounded-[2rem] mb-6">
                                <IconDatabaseImport size={56} />
                            </div>
                            <h3 className="text-3xl font-black text-gray-900 uppercase mb-4">Cargue de reporte de inventario</h3>
                            <p className="text-gray-400 font-bold text-sm uppercase mb-12 italic">Actualiza el inventario global de la red desde un archivo Excel.</p>

                            <div className="flex justify-center">
                                <div className="max-w-md bg-gray-50 rounded-[2.5rem] p-10 border-2 border-dashed border-gray-200 hover:border-blue-400 transition-all group flex flex-col items-center">
                                    <IconFilePlus size={48} className="text-blue-500 mb-6" />
                                    <h4 className="text-xl font-black text-gray-900 uppercase mb-3">Reporte de inventario</h4>
                                    <p className="text-xs text-gray-400 font-bold uppercase mb-8">Sincroniza saldos vía PLU y código de sede.</p>

                                    <label className="w-full cursor-pointer">
                                        <input type="file" accept=".xlsx, .xls" className="hidden" onChange={e => handleExcelImport(e, 'medicamento')} />
                                        <div className="bg-gray-900 text-white py-4 px-8 rounded-2xl font-black uppercase tracking-widest hover:bg-blue-600 transition-colors">Seleccionar Archivo</div>
                                    </label>

                                    <button onClick={() => downloadTemplate('medicamento')} className="mt-6 flex items-center gap-2 text-blue-500 font-black text-[10px] uppercase">
                                        <IconDownload size={16} /> Descargar Formato
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default AdminPanel;
