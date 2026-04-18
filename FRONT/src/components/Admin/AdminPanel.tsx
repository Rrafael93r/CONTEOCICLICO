import React, { useEffect, useState, useMemo } from 'react';
import { getAllUsuarios, updateUsuario, Usuario } from '../../servicios/usuarioService';
import { getAllMedicamentos, Medicamento, bulkImportMedicamentos, bulkUpdateInventory, resetCycleByUsuario, getMedicamentoSummary, getDashboardStats, searchMedicamentos } from '../../servicios/medicamentoService';
import { createPersonalizado } from '../../servicios/personalizadoService';
import { getAllDetalles, DetalleConteo } from '../../servicios/detalleConteoService';
import { getSeguimientoDiario, getSeguimientoMensual, SeguimientoSede, GlobalSeguimientoMensualDTO, SeguimientoMensualDTO } from '../../servicios/seguimientoService';
import { getAllSedeConfigs, updateSedeConfig, syncSedes, SedeConfig } from '../../servicios/sedeConfigService';
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
    IconFilePlus,
    IconChartBar,
    IconPlus,
    IconCheck,
    IconUpload,
    IconInfoCircle,
    IconTrash
} from '@tabler/icons-react';

const AdminPanel: React.FC = () => {
    const [activeTab, setActiveTab] = useState<'usuarios' | 'asignar' | 'reportes' | 'importar' | 'seguimiento'>('usuarios');
    const [searchTermSeguimiento, setSearchTermSeguimiento] = useState('');
    const [usuarios, setUsuarios] = useState<Usuario[]>([]);
    const [medicamentos, setMedicamentos] = useState<Medicamento[]>([]);
    const [detalles, setDetalles] = useState<DetalleConteo[]>([]);
    const [seguimiento, setSeguimiento] = useState<SeguimientoSede[]>([]);
    const [seguimientoMensual, setSeguimientoMensual] = useState<GlobalSeguimientoMensualDTO | null>(null);
    const [medSummary, setMedSummary] = useState<any[]>([]);
    const [globalStats, setGlobalStats] = useState<any>(null);
    const [medResults, setMedResults] = useState<Medicamento[]>([]);
    const [sedeConfigs, setSedeConfigs] = useState<SedeConfig[]>([]);

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
        const fetchMeds = async () => {
            if (activeTab === 'asignar' && medSearchTerm.length > 2) {
                const results = await searchMedicamentos(medSearchTerm);
                setMedResults(results);
            }
        };
        const timer = setTimeout(fetchMeds, 300);
        return () => clearTimeout(timer);
    }, [medSearchTerm, activeTab]);

    useEffect(() => {
        loadInitialData();
    }, [activeTab, startDate, endDate, filterUser]);

    const loadInitialData = async () => {
        setLoading(true);
        try {
            if (activeTab === 'usuarios') {
                const [configs, summary, uData] = await Promise.all([
                    getAllSedeConfigs(),
                    getMedicamentoSummary(),
                    getAllUsuarios()
                ]);
                setSedeConfigs(configs);
                setMedSummary(summary);
                setUsuarios(uData.filter(u => u.idRol === 1));
            } else if (activeTab === 'asignar') {
                const uData = await getAllUsuarios();
                setUsuarios(uData.filter(u => u.idRol === 1));
                // No cargamos medicamentos aquí, se buscan dinámicamente
            }
            else if (activeTab === 'reportes') {
                const data = await getAllDetalles(
                    filterUser ? Number(filterUser) : undefined,
                    undefined,
                    startDate,
                    endDate
                );
                setDetalles(data);
            } else if (activeTab === 'seguimiento') {
                const [data, stats, monthlyData] = await Promise.all([
                    getSeguimientoDiario(),
                    getDashboardStats(),
                    getSeguimientoMensual()
                ]);
                setSeguimiento(data);
                setGlobalStats(stats);
                setSeguimientoMensual(monthlyData);
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

    const filteredSeguimiento = useMemo(() => {
        if (!seguimientoMensual) return [];
        return seguimientoMensual.reportePorSedes.filter(item =>
            item.usuario?.toLowerCase().includes(searchTermSeguimiento.toLowerCase()) ||
            item.sede?.toLowerCase().includes(searchTermSeguimiento.toLowerCase())
        );
    }, [seguimientoMensual, searchTermSeguimiento]);

    const handleToggleExtraBlock = async (id: number) => {
        try {
            const response = await axios.post(`/api/medicamento/admin/bloque-extra/${id}`);
            Swal.fire({
                title: '¡Bloque Autorizado!',
                text: response.data || 'El usuario ya puede descargar un nuevo bloque hoy.',
                icon: 'success',
                timer: 2000,
                showConfirmButton: false,
                toast: true,
                position: 'top-end'
            });
            // Refrescar datos del dashboard
            const mens = await getSeguimientoMensual();
            setSeguimientoMensual(mens);
        } catch (error: any) {
            const msg = error.response?.data || 'Error al autorizar bloque extra.';
            Swal.fire({
                title: 'No disponible',
                text: msg,
                icon: 'info',
                toast: true,
                position: 'top-end',
                timer: 3000,
                showConfirmButton: false
            });
        }
    };

    const handleSaveAllQuotas = async () => {
        try {
            setLoading(true);
            await Promise.all(sedeConfigs.map(config => {
                if (config.id) {
                    return updateSedeConfig(config.id, {
                        numeroConteo: config.numeroConteo,
                        tipoConteo: config.tipoConteo
                    });
                }
                return Promise.resolve();
            }));

            Swal.fire({
                icon: 'success',
                title: 'Configuración Guardada',
                text: 'Todas las cuotas por sede han sido actualizadas.',
                confirmButtonColor: '#f6952c',
                customClass: { popup: 'rounded-3xl' }
            });
            await loadInitialData();
        } catch (error) {
            Swal.fire('Error', 'No se pudieron guardar los cambios en las sedes', 'error');
        } finally {
            setLoading(false);
        }
    };

    const handleSyncSedes = async () => {
        try {
            setLoading(true);
            const msg = await syncSedes();
            Swal.fire('Sincronización', msg, 'success');
            await loadInitialData();
        } catch (error) {
            Swal.fire('Error', 'No se pudo sincronizar las sedes', 'error');
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

    const handleMainUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
        handleExcelImport(e, 'medicamento');
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

                if (jsonData.length === 0) {
                    Swal.fire('Archivo Vacío', 'No se encontraron datos para procesar.', 'warning');
                    return;
                }

                Swal.update({
                    title: '<span class="text-2xl font-black text-gray-900 uppercase italic">Sincronizando con Servidor</span>',
                    html: `
                        <div class="py-10">
                            <div class="w-20 h-20 border-8 border-orange-500 border-t-transparent rounded-full animate-spin mx-auto mb-6 shadow-2xl shadow-orange-500/20"></div>
                            <p class="text-gray-500 font-bold uppercase tracking-widest text-xs animate-pulse">El servidor está procesando ${jsonData.length} registros...</p>
                            <p class="text-gray-400 text-[10px] mt-2 italic px-8">Mapeando columnas y validando reglas ABC.</p>
                        </div>
                    `
                });

                const { syncInventoryData } = await import('../../servicios/medicamentoService');
                // We send raw JSON, backend handles "Centro Costo" -> "Sede" etc.
                const response = await syncInventoryData(jsonData);

                Swal.fire({
                    icon: response.procesados > 0 ? 'success' : 'warning',
                    title: `<span class="text-xl font-black text-gray-900 uppercase italic">${response.mensaje}</span>`,
                    html: `
                        <div class="text-left bg-gray-50 p-6 rounded-2xl mt-4 border border-gray-100">
                            <p class="text-xs font-black text-gray-400 uppercase mb-2">Resumen de Operación:</p>
                            <div class="grid grid-cols-2 gap-4 mb-4">
                                <div class="bg-white p-3 rounded-xl border border-gray-100">
                                    <p class="text-xl font-black text-gray-900">${response.leidos}</p>
                                    <p class="text-[8px] font-bold text-gray-400 uppercase">Leídos</p>
                                </div>
                                <div class="bg-white p-3 rounded-xl border border-gray-100">
                                    <p class="text-xl font-black text-emerald-500">${response.procesados}</p>
                                    <p class="text-[8px] font-bold text-gray-400 uppercase">Procesados</p>
                                </div>
                            </div>
                            ${response.logs ? `
                                <div class="mt-4">
                                    <p class="text-[10px] font-black text-red-400 uppercase mb-1">Incidencias Detectadas:</p>
                                    <pre class="text-[9px] text-gray-500 overflow-auto max-h-40 bg-white p-3 rounded-xl border border-gray-100 font-mono">
                                        ${response.logs}
                                    </pre>
                                </div>
                            ` : ''}
                        </div>
                    `,
                    confirmButtonColor: '#f6952c',
                    customClass: { popup: 'rounded-[3rem] w-[32rem]' }
                });

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

    const downloadTemplate = (type: 'medicamento' | 'inventario' = 'medicamento') => {
        const headers = type === 'medicamento'
            ? [['Centro Costo', 'PLU', 'Descripcion', 'Inventario', 'Ultimo Costo', 'Total']]
            : [['Centro Costo', 'PLU']];

        const worksheet = XLSX.utils.aoa_to_sheet(headers);
        const workbook = XLSX.utils.book_new();
        XLSX.utils.book_append_sheet(workbook, worksheet, "Plantilla");

        const wscols = type === 'medicamento'
            ? [{ wch: 10 }, { wch: 15 }, { wch: 40 }, { wch: 10 }, { wch: 10 }, { wch: 15 }, { wch: 15 }]
            : [{ wch: 15 }, { wch: 15 }];
        worksheet['!cols'] = wscols;

        XLSX.writeFile(workbook, `PLANTILLA_${type.toUpperCase()}.xlsx`);
    };

    const filteredMeds = medicamentos.filter(m => {
        // Filtrar por el usuario/sede seleccionado actualmente
        if (selectedUser && m.idUsuario !== Number(selectedUser)) return false;

        return (
            (m.descripcion?.toLowerCase() || '').includes(medSearchTerm.toLowerCase()) ||
            (m.plu || '').includes(medSearchTerm)
        );
    }).slice(0, 10);

    const filteredSedeConfigs = (sedeConfigs || []).filter(c =>
        c.codigoSede?.toLowerCase().includes(userSearchTerm.toLowerCase())
    );

    const totalMolecules = globalStats?.total || 0;
    const countedMolecules = globalStats?.contados || 0;
    const globalCoverage = totalMolecules > 0 ? Math.round((countedMolecules / totalMolecules) * 100) : 0;

    const coverageByCategory = [
        { cat: 'A', total: globalStats?.totalA || 0, counted: globalStats?.contadosA || 0 },
        { cat: 'B', total: globalStats?.totalB || 0, counted: globalStats?.contadosB || 0 },
        { cat: 'C', total: globalStats?.totalC || 0, counted: globalStats?.contadosC || 0 },
    ].map(item => ({
        ...item,
        percentage: item.total > 0 ? Math.round((item.counted / item.total) * 100) : 0
    }));

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
                        {(['usuarios', 'seguimiento', 'asignar', 'reportes', 'importar'] as const).map(tab => (
                            <button
                                key={tab}
                                onClick={() => setActiveTab(tab)}
                                className={`flex items-center gap-2 px-6 py-3 rounded-xl text-xs font-black transition-all ${activeTab === tab ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-400 hover:text-gray-600'}`}
                            >
                                {tab === 'usuarios' && <IconUsers size={18} />}
                                {tab === 'seguimiento' && <IconChartBar size={18} />}
                                {tab === 'asignar' && <IconCalendarPlus size={18} />}
                                {tab === 'reportes' && <IconFileSpreadsheet size={18} />}
                                {tab === 'importar' && <IconDatabaseImport size={18} />}
                                {tab === 'seguimiento' ? 'SEGUIMIENTO' : tab.toUpperCase()}
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
                                <h3 className="text-xl font-black text-gray-900 uppercase">Gestión de Cuota por Sede</h3>
                                <p className="text-[10px] text-gray-400 font-bold uppercase tracking-widest mt-1">Configura la cuota diaria y tipo de conteo para cada sede operativa.</p>
                            </div>
                            <div className="flex gap-4">
                                <button
                                    onClick={handleSyncSedes}
                                    className="flex items-center gap-2 px-6 py-4 bg-gray-100 text-gray-600 rounded-2xl font-black text-xs uppercase tracking-widest hover:bg-gray-200 transition-all"
                                >
                                    <IconDatabaseImport size={20} /> Sincronizar Sedes
                                </button>
                                <button
                                    onClick={handleSaveAllQuotas}
                                    disabled={loading}
                                    className="flex items-center gap-2 px-8 py-4 bg-orange-500 text-white rounded-2xl font-black text-xs uppercase tracking-widest shadow-xl shadow-orange-500/20 hover:bg-orange-600 transition-all active:scale-95 disabled:opacity-50"
                                >
                                    <IconDeviceFloppy size={20} /> GUARDAR CONFIGURACIÓN
                                </button>
                            </div>
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
                                            <th className="px-8 py-5 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest italic">Código de Sede</th>
                                            <th className="px-8 py-5 text-center text-[10px] font-black text-gray-400 uppercase tracking-widest italic">Usuarios Activos</th>
                                            <th className="px-8 py-5 text-center text-[10px] font-black text-gray-400 uppercase tracking-widest italic">Conteo diario</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-gray-50">
                                        {filteredSedeConfigs.map(config => (
                                            <tr key={config.id} className="hover:bg-gray-50/30 transition-colors">
                                                <td className="px-8 py-6">
                                                    <div className="flex items-center gap-4">
                                                        <div className="w-10 h-10 bg-orange-100 text-orange-600 rounded-xl flex items-center justify-center font-black">
                                                            S
                                                        </div>
                                                        <div>
                                                            <input 
                                                                type="text"
                                                                value={config.nombre || ''}
                                                                placeholder="Nombre de la sede..."
                                                                onChange={(e) => {
                                                                    setSedeConfigs(prev => prev.map(c => c.id === config.id ? { ...c, nombre: e.target.value } : c));
                                                                }}
                                                                className="bg-transparent border-b border-gray-100 focus:border-orange-500 outline-none font-black text-gray-900 text-sm uppercase w-full"
                                                            />
                                                            <div className="text-[10px] font-bold text-gray-400 tracking-widest uppercase mt-1">CÓDIGO: {config.codigoSede}</div>
                                                        </div>
                                                    </div>
                                                </td>
                                                <td className="px-8 py-6 text-center">
                                                    <span className="text-sm font-black text-blue-600 bg-blue-50 px-3 py-1 rounded-lg">
                                                        {usuarios.filter(u => u.sede === config.codigoSede).length} operarios
                                                    </span>
                                                </td>
                                                <td className="px-8 py-6 text-center">
                                                    <input
                                                        type="number"
                                                        min="0"
                                                        value={config.numeroConteo || 0}
                                                        onChange={(e) => {
                                                            const val = Math.max(0, parseInt(e.target.value) || 0);
                                                            setSedeConfigs(prev => prev.map(c => c.id === config.id ? { ...c, numeroConteo: val } : c));
                                                        }}
                                                        className="w-24 text-center py-3 bg-gray-50 rounded-xl border-2 border-transparent focus:border-orange-500 outline-none font-black text-orange-600 text-lg"
                                                    />
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                            <div className="md:hidden space-y-4">
                                {filteredSedeConfigs.map(config => (
                                    <div key={config.id} className="bg-white rounded-2xl p-4 shadow-sm border border-gray-100">
                                        <div className="flex justify-between items-start mb-4">
                                            <div className="flex items-center gap-3">
                                                <div className="w-10 h-10 bg-orange-100 text-orange-600 rounded-xl flex items-center justify-center font-black">
                                                    S
                                                </div>
                                                <div className="flex-1">
                                                    <input 
                                                        type="text"
                                                        value={config.nombre || ''}
                                                        placeholder="Nombre..."
                                                        onChange={(e) => {
                                                            setSedeConfigs(prev => prev.map(c => c.id === config.id ? { ...c, nombre: e.target.value } : c));
                                                        }}
                                                        className="bg-transparent border-b border-gray-100 focus:border-orange-500 outline-none font-black text-gray-900 text-sm uppercase w-full"
                                                    />
                                                    <div className="text-[10px] font-bold text-gray-400 tracking-widest uppercase mt-1">CÓDIGO: {config.codigoSede}</div>
                                                </div>
                                            </div>
                                            <span className="text-[10px] font-black text-blue-600 bg-blue-50 px-2 py-1 rounded-lg">
                                                {usuarios.filter(u => u.sede === config.codigoSede).length} operarios
                                            </span>
                                        </div>
                                        <div className="grid grid-cols-1 gap-4 pt-4 border-t border-gray-50">
                                            <div className="flex-1">
                                                <p className="text-[9px] font-black text-gray-400 uppercase mb-1">Configuración Sedes</p>
                                                <div className="px-4 py-2 bg-gray-50 rounded-xl text-center font-black text-gray-400 text-[10px] uppercase">
                                                    Modo ABC Activo
                                                </div>
                                            </div>
                                            <div className="flex-1">
                                                <p className="text-[9px] font-black text-gray-400 uppercase mb-1">Cuota</p>
                                                <input
                                                    type="number"
                                                    min="0"
                                                    value={config.numeroConteo || 0}
                                                    onChange={(e) => {
                                                        const val = Math.max(0, parseInt(e.target.value) || 0);
                                                        setSedeConfigs(prev => prev.map(c => c.id === config.id ? { ...c, numeroConteo: val } : c));
                                                    }}
                                                    className="w-full py-2 bg-gray-50 rounded-xl text-center font-black text-orange-600"
                                                />
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>
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
                                        onChange={(e) => {
                                            const val = e.target.value;
                                            setSelectedUser(val === "" ? "" : Number(val));
                                            setSelectedMed(""); // Limpiar medicamento al cambiar sede
                                            setMedSearchTerm("");
                                        }}
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
                                            {medResults.map(m => (
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
                                                        <span className="text-[10px] font-bold opacity-70">PLU: {m.plu} | Inventario: {m.inventario} | Estado: {m.estadoDelConteo?.toUpperCase()}</span>
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
                    <div className="p-4 sm:p-10">
                        <div className="max-w-4xl mx-auto space-y-8">
                            <div className="text-center mb-12">
                                <h3 className="text-2xl font-black text-gray-900 uppercase tracking-tight">Sincronización Maestra</h3>
                                <p className="text-gray-400 font-bold text-xs uppercase tracking-widest mt-2 italic">Actualiza productos, costos y existencias en un solo proceso omnicanal.</p>
                            </div>

                            <div className="bg-orange-50/50 p-8 rounded-[2.5rem] border-2 border-dashed border-orange-200 text-center space-y-6">
                                <div className="w-20 h-20 bg-orange-500 rounded-[2rem] flex items-center justify-center text-white mx-auto shadow-2xl shadow-orange-500/20">
                                    <IconDatabaseImport size={40} />
                                </div>
                                <div className="space-y-2">
                                    <h4 className="text-xl font-black text-gray-900 uppercase">Carga de Datos Unificada</h4>
                                    <p className="text-xs font-bold text-gray-400 uppercase tracking-widest leading-relaxed px-10">
                                        Selecciona el archivo Excel generado por el sistema central.
                                        El motor ABC procesará automáticamente las prioridades financieras.
                                    </p>
                                </div>
                                <div className="flex flex-col sm:flex-row gap-4 justify-center pt-4">
                                    <button
                                        onClick={() => downloadTemplate()}
                                        className="px-8 py-4 border-2 border-gray-200 rounded-2xl text-[10px] font-black uppercase tracking-widest text-gray-500 hover:border-orange-500 hover:text-orange-500 transition-all flex items-center justify-center gap-2"
                                    >
                                        <IconDownload size={18} /> Descargar Plantilla
                                    </button>
                                    <div className="relative">
                                        <input
                                            type="file"
                                            id="main-unified-upload"
                                            className="hidden"
                                            accept=".xlsx, .xls, .csv"

                                            onChange={handleMainUpload}
                                        />
                                        <label
                                            htmlFor="main-unified-upload"
                                            className="px-10 py-4 bg-gray-900 text-white rounded-2xl text-[10px] font-black uppercase flex items-center justify-center gap-2 cursor-pointer hover:bg-orange-500 transition-all shadow-xl shadow-gray-900/10"
                                        >
                                            <IconUpload size={18} /> Iniciar Sincronización
                                        </label>
                                    </div>
                                </div>
                            </div>

                        </div>
                    </div>
                )}

                {activeTab === 'seguimiento' && (
                    <div className="space-y-8 p-2 sm:p-4">
                        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-8 rounded-[2.5rem] border border-gray-100 shadow-sm">
                            <div>
                                <h3 className="text-2xl font-black text-gray-900 uppercase tracking-tight">Panel de Monitoreo Ciclo Mensual</h3>
                                <p className="text-gray-400 font-bold text-xs uppercase tracking-widest mt-1">Consolidado en tiempo real por sede y clasificación ABC</p>
                            </div>
                            <div className="flex items-center gap-6">
                                <div className="text-right">
                                    <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1 text-right">Cobertura Global</p>
                                    <p className="text-5xl font-black text-orange-500">{seguimientoMensual ? Math.round(seguimientoMensual.consolidadoGlobal.coberturaSede) : 0}%</p>
                                </div>
                                <div className="h-12 w-px bg-gray-100 hidden md:block"></div>
                                <div className="hidden lg:block">
                                    <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1">Métricas Detalladas</p>
                                    <p className="text-xl font-black text-gray-900 italic">
                                        {(seguimientoMensual?.consolidadoGlobal?.contadasA || 0) + (seguimientoMensual?.consolidadoGlobal?.contadasB || 0) + (seguimientoMensual?.consolidadoGlobal?.contadasC || 0)}
                                        <span className="text-gray-300 mx-2">/</span>
                                        {(seguimientoMensual?.consolidadoGlobal?.totalA || 0) + (seguimientoMensual?.consolidadoGlobal?.totalB || 0) + (seguimientoMensual?.consolidadoGlobal?.totalC || 0)}
                                    </p>
                                </div>
                            </div>
                        </div>

                        {/* Tarjetas de Categoría Global */}
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                            <div className="bg-white p-6 rounded-[2rem] border border-gray-100 shadow-xl relative overflow-hidden group">
                                <div className="absolute top-0 right-0 p-4 opacity-5 group-hover:scale-110 transition-transform">
                                    <IconMedicineSyrup size={80} className="text-red-500" />
                                </div>
                                <h5 className="text-[10px] font-black text-red-500 uppercase tracking-widest mb-4 flex items-center gap-2">
                                    <div className="w-2 h-2 rounded-full bg-red-500 animate-pulse"></div> Tipo A
                                </h5>
                                <div className="space-y-4">
                                    <div>
                                        <div className="flex justify-between text-[10px] font-black mb-1">
                                            <span className="text-gray-400 uppercase">Primer Conteo (1x)</span>
                                            <span className="text-gray-900">{seguimientoMensual?.consolidadoGlobal?.contadasA || 0} / {seguimientoMensual?.consolidadoGlobal?.totalA || 0}</span>
                                        </div>
                                        <div className="h-1.5 bg-gray-50 rounded-full overflow-hidden">
                                            <div className="h-full bg-red-400" style={{ width: `${seguimientoMensual?.consolidadoGlobal?.totalA ? (seguimientoMensual.consolidadoGlobal.contadasA / seguimientoMensual.consolidadoGlobal.totalA) * 100 : 0}%` }}></div>
                                        </div>
                                    </div>
                                    <div>
                                        <div className="flex justify-between text-[10px] font-black mb-1">
                                            <span className="text-gray-400 uppercase">Segundo Conteo (2x)</span>
                                            <span className="text-emerald-500 font-black tracking-tighter">COMPLETO: {seguimientoMensual?.consolidadoGlobal?.aContadasDosVeces || 0}</span>
                                        </div>
                                        <div className="h-1.5 bg-gray-50 rounded-full overflow-hidden">
                                            <div className="h-full bg-emerald-400" style={{ width: `${seguimientoMensual?.consolidadoGlobal?.totalA ? (seguimientoMensual.consolidadoGlobal.aContadasDosVeces / seguimientoMensual.consolidadoGlobal.totalA) * 100 : 0}%` }}></div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div className="bg-white p-6 rounded-[2rem] border border-gray-100 shadow-xl group">
                                <h5 className="text-[10px] font-black text-orange-500 uppercase tracking-widest mb-4 flex items-center gap-2">
                                    <div className="w-2 h-2 rounded-full bg-orange-500"></div> Tipo B
                                </h5>
                                <div className="flex items-center justify-between mb-2">
                                    <div className="flex flex-col">
                                        <span className="text-3xl font-black text-gray-900">
                                            {seguimientoMensual?.consolidadoGlobal?.totalB ? Math.round((seguimientoMensual.consolidadoGlobal.contadasB / seguimientoMensual.consolidadoGlobal.totalB) * 100) : 0}%
                                        </span>
                                        <span className="text-[10px] font-bold text-gray-400 uppercase">
                                            {seguimientoMensual?.consolidadoGlobal?.contadasB} / {seguimientoMensual?.consolidadoGlobal?.totalB}
                                        </span>
                                    </div>
                                    <IconChartBar size={32} className="text-orange-100" />
                                </div>
                                <div className="h-2 bg-gray-50 rounded-full overflow-hidden">
                                    <div className="h-full bg-orange-500 shadow-lg shadow-orange-500/20" style={{ width: `${seguimientoMensual?.consolidadoGlobal?.totalB ? (seguimientoMensual.consolidadoGlobal.contadasB / seguimientoMensual.consolidadoGlobal.totalB) * 100 : 0}%` }}></div>
                                </div>
                                <p className="text-[8px] font-bold text-gray-400 uppercase mt-2">{seguimientoMensual?.consolidadoGlobal?.contadasB} de {seguimientoMensual?.consolidadoGlobal?.totalB} procesados</p>
                            </div>

                            <div className="bg-white p-6 rounded-[2rem] border border-gray-100 shadow-xl group">
                                <h5 className="text-[10px] font-black text-green-500 uppercase tracking-widest mb-4 flex items-center gap-2">
                                    <div className="w-2 h-2 rounded-full bg-green-500"></div> Tipo C
                                </h5>
                                <div className="flex items-center justify-between mb-2">
                                    <div className="flex flex-col">
                                        <span className="text-3xl font-black text-gray-900">
                                            {seguimientoMensual?.consolidadoGlobal?.totalC ? Math.round((seguimientoMensual.consolidadoGlobal.contadasC / seguimientoMensual.consolidadoGlobal.totalC) * 100) : 0}%
                                        </span>
                                        <span className="text-[10px] font-bold text-gray-400 uppercase">
                                            {seguimientoMensual?.consolidadoGlobal?.contadasC} / {seguimientoMensual?.consolidadoGlobal?.totalC}
                                        </span>
                                    </div>
                                    <IconCheck size={32} className="text-green-100" />
                                </div>
                                <div className="h-2 bg-gray-50 rounded-full overflow-hidden">
                                    <div className="h-full bg-green-500 shadow-lg shadow-green-500/20" style={{ width: `${seguimientoMensual?.consolidadoGlobal?.totalC ? (seguimientoMensual.consolidadoGlobal.contadasC / seguimientoMensual.consolidadoGlobal.totalC) * 100 : 0}%` }}></div>
                                </div>
                                <p className="text-[8px] font-bold text-gray-400 uppercase mt-2">{seguimientoMensual?.consolidadoGlobal?.contadasC} de {seguimientoMensual?.consolidadoGlobal?.totalC} procesados</p>
                            </div>
                        </div>

                        <div className="bg-white p-6 rounded-3xl border border-gray-100 shadow-sm flex items-center gap-4">
                            <div className="bg-gray-50 flex-1 flex items-center gap-3 px-6 py-3 rounded-2xl border border-transparent focus-within:border-orange-500 transition-all">
                                <IconSearch size={18} className="text-gray-400" />
                                <input
                                    type="text"
                                    placeholder="Buscar por usuario o farmacia..."
                                    value={searchTermSeguimiento}
                                    onChange={(e) => setSearchTermSeguimiento(e.target.value)}
                                    className="bg-transparent border-none outline-none w-full font-bold text-gray-700 placeholder:text-gray-300"
                                />
                            </div>
                        </div>

                        {/* Listado de Sedes Simplificado */}
                        <div className="bg-white rounded-[2.5rem] border border-gray-100 shadow-xl overflow-hidden">
                            <table className="w-full text-left">
                                <thead className="bg-gray-50 border-b border-gray-100">
                                    <tr>
                                        <th className="px-8 py-4 text-[10px] font-black text-gray-400 uppercase tracking-widest italic text-left">Usuario / Farmacia</th>
                                        <th className="px-8 py-4 text-[10px] font-black text-gray-400 uppercase tracking-widest text-center italic">Cobertura</th>
                                        <th className="px-8 py-4 text-[10px] font-black text-gray-400 uppercase tracking-widest text-center italic">A (1x / 2x)</th>
                                        <th className="px-8 py-4 text-[10px] font-black text-gray-400 uppercase tracking-widest text-center italic">Tipo B</th>
                                        <th className="px-8 py-4 text-[10px] font-black text-gray-400 uppercase tracking-widest text-center italic">Tipo C</th>
                                        {/* <th className="px-8 py-4 text-[10px] font-black text-gray-400 uppercase tracking-widest text-center italic">Programación Extra</th> */}
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredSeguimiento.map((userStat: SeguimientoMensualDTO) => (
                                        <tr key={userStat.usuario + "_" + userStat.sede} className="border-b border-gray-50 hover:bg-gray-50 transition-colors">
                                            <td className="px-8 py-6 text-left">
                                                <div className="flex items-center gap-4">
                                                    <div className="w-10 h-10 bg-orange-100 text-orange-600 rounded-xl flex items-center justify-center font-black">
                                                        {(userStat.usuario || userStat.sede || '?').charAt(0).toUpperCase()}
                                                    </div>
                                                    <div>
                                                        <div className="font-black text-gray-900 text-sm uppercase">
                                                            {userStat.usuario || `SEDE ${userStat.sede}`}
                                                        </div>
                                                        {userStat.usuario && userStat.sede && userStat.usuario !== userStat.sede && (
                                                            <div className="text-[10px] font-bold text-gray-400 tracking-widest uppercase italic">Sede: {userStat.sede}</div>
                                                        )}
                                                        <span className="text-[8px] font-black text-gray-300 bg-gray-50 px-2 py-0.5 rounded uppercase tracking-tighter mt-1 block w-fit">
                                                            Catálogo: {userStat.totalA + userStat.totalB + userStat.totalC}
                                                        </span>
                                                    </div>
                                                </div>
                                            </td>
                                            <td className="px-8 py-6 text-center">
                                                <div className="flex flex-col items-center gap-1">
                                                    <span className="text-xl font-black text-orange-500">{Math.round(userStat.coberturaSede)}%</span>
                                                    <div className="w-20 h-1 bg-gray-100 rounded-full overflow-hidden">
                                                        <div className="h-full bg-orange-500" style={{ width: `${userStat.coberturaSede}%` }}></div>
                                                    </div>
                                                </div>
                                            </td>
                                            <td className="px-8 py-6">
                                                <div className="flex flex-col gap-2 max-w-[120px] mx-auto">
                                                    <div className="flex justify-between items-center text-[10px] font-black">
                                                        <span className="text-gray-400 italic">1x</span>
                                                        <span className="text-blue-500">{userStat.contadasA} / {userStat.totalA}</span>
                                                    </div>
                                                    <div className="flex justify-between items-center text-[10px] font-black">
                                                        <span className="text-gray-400 italic">2x</span>
                                                        <span className="text-emerald-500">{userStat.aContadasDosVeces} / {userStat.totalA}</span>
                                                    </div>
                                                </div>
                                            </td>
                                            <td className="px-8 py-6 text-center">
                                                <div className="w-10 h-10 rounded-full border-4 border-gray-50 flex flex-col items-center justify-center mx-auto relative group">
                                                    <span className="text-[8px] font-black text-gray-900 leading-tight">{userStat.contadasB}</span>
                                                    <span className="text-[6px] font-bold text-gray-400 border-t border-gray-200 pt-0.5">{userStat.totalB}</span>
                                                    <svg className="absolute inset-0 w-full h-full -rotate-90">
                                                        <circle cx="20" cy="20" r="18" fill="none" stroke="#f6952c" strokeWidth="4" strokeDasharray="113" strokeDashoffset={113 - (113 * (userStat.contadasB / (userStat.totalB || 1)))} strokeLinecap="round" className="opacity-40" />
                                                    </svg>
                                                </div>
                                            </td>
                                            <td className="px-8 py-6 text-center">
                                                <div className="w-10 h-10 rounded-full border-4 border-gray-50 flex flex-col items-center justify-center mx-auto relative">
                                                    <span className="text-[8px] font-black text-gray-900 leading-tight">{userStat.contadasC}</span>
                                                    <span className="text-[6px] font-bold text-gray-400 border-t border-gray-200 pt-0.5">{userStat.totalC}</span>
                                                    <svg className="absolute inset-0 w-full h-full -rotate-90">
                                                        <circle cx="20" cy="20" r="18" fill="none" stroke="#22c55e" strokeWidth="4" strokeDasharray="113" strokeDashoffset={113 - (113 * (userStat.contadasC / (userStat.totalC || 1)))} strokeLinecap="round" className="opacity-40" />
                                                    </svg>
                                                </div>
                                            </td>
                                            {/* <td className="px-8 py-6 text-center">
                                                <div className="flex items-center justify-center">
                                                    {userStat.fechaBloqueExtra && userStat.fechaBloqueExtra.startsWith(new Date().toLocaleDateString('en-CA')) ? (
                                                        <div className="flex flex-col items-center gap-1">
                                                            <div className="w-8 h-8 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center shadow-inner animate-pulse">
                                                                <IconCalendarPlus size={16} />
                                                            </div>
                                                            <span className="text-[8px] font-black text-emerald-500 uppercase tracking-tighter">Habilitado Hoy</span>
                                                        </div>
                                                    ) : (
                                                        <button
                                                            onClick={() => handleToggleExtraBlock(userStat.idUsuario)}
                                                            className="group flex flex-col items-center gap-1 hover:scale-105 transition-all"
                                                        >
                                                            <div className="w-8 h-8 bg-gray-50 text-gray-400 group-hover:bg-orange-500 group-hover:text-white rounded-full flex items-center justify-center transition-all shadow-sm">
                                                                <IconCalendarPlus size={16} />
                                                            </div>
                                                            <span className="text-[8px] font-black text-gray-300 group-hover:text-orange-500 uppercase tracking-tighter">Habilitar Bloque</span>
                                                        </button>
                                                    )}
                                                </div>
                                            </td> */}
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

const ProgressRow: React.FC<{ label: string; current: number; total: number; color: string }> = ({ label, current, total, color }) => {
    const percentage = total > 0 ? Math.round((current / total) * 100) : 0;
    return (
        <div className="space-y-1">
            <div className="flex justify-between items-center px-1">
                <span className="text-[10px] font-black text-gray-500 uppercase italic">{label}</span>
                <span className="text-[10px] font-black text-gray-900">{current} / {total} <span className="text-gray-300 ml-1 text-[8px]">({percentage}%)</span></span>
            </div>
            <div className="h-2 bg-gray-50 rounded-full overflow-hidden border border-gray-100">
                <div className={`h-full ${color} transition-all duration-1000`} style={{ width: `${percentage}%` }}></div>
            </div>
        </div>
    );
};

export default AdminPanel;
