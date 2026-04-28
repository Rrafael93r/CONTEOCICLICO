import React, { useEffect, useState } from 'react';
import { getAllMedicamentos, updateMedicamento, Medicamento, resetCycleByUsuario, bulkUpdateMedicamentoStatus, getDashboardStats } from '../../servicios/medicamentoService';
import { createDetalle, getAllDetalles, updateDetalle, DetalleConteo, bulkCreateDetalles, bulkUpdateDetalles } from '../../servicios/detalleConteoService';
import { getAllPersonalizados } from '../../servicios/personalizadoService';
import { getCurrentUser } from '../../servicios/authServices';
import Swal from 'sweetalert2';
import {
    IconSearch,
    IconPlus,
    IconCheck,
    IconEdit,
    IconDeviceFloppy,
    IconRefresh,
    IconTrash
} from '@tabler/icons-react';

interface DetalleConteoEditable extends DetalleConteo {
    inputValue: number | '';
    inputLote: string;
    inputFechaVencimiento: string;
    isNew?: boolean;
    tempId?: string;
}

const Toast = Swal.mixin({
    toast: true,
    position: 'top-end',
    showConfirmButton: false,
    timer: 3000,
    timerProgressBar: true
});

const DetalleConteoTable: React.FC = () => {
    const [detalles, setDetalles] = useState<DetalleConteoEditable[]>([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [globalStats, setGlobalStats] = useState<any>(null);
    const fetchingRef = React.useRef(false);

    const normalizeDate = (date: any): string => {
        if (!date) return '';
        if (Array.isArray(date)) {
            const [y, m, d] = date;
            return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
        }
        if (typeof date === 'string') return date.split('T')[0];
        return String(date);
    };

    const fetchData = async () => {
        if (fetchingRef.current) return;
        fetchingRef.current = true;
        setLoading(true);
        try {
            const currentUser = getCurrentUser();
            if (!currentUser) return;

            const now = new Date();
            const fechaHoy = now.getFullYear() + "-" + String(now.getMonth() + 1).padStart(2, '0') + "-" + String(now.getDate()).padStart(2, '0');
            
            const [allMedsForLookup, rawDetalles, rawPersonalizados, stats] = await Promise.all([
                getAllMedicamentos(currentUser.id),
                getAllDetalles(currentUser.id, fechaHoy),
                getAllPersonalizados(currentUser.id, fechaHoy),
                getDashboardStats()
            ]);

            setGlobalStats(stats);

            // Filtro local estricto adicional al remoto
            let hoyUserDetalles = rawDetalles.filter(d => normalizeDate(d.fechaRegistro) === fechaHoy);
            const hoyPersonalizados = rawPersonalizados.filter(p => normalizeDate(p.fechaProgramacion) === fechaHoy);


            // 1. PROCESAR PERSONALIZADOS (SIN DUPLICADOS)
            for (const p of hoyPersonalizados) {
                const pId = p.idMedicamento || p.medicamento?.id;
                if (!pId) continue;

                const yaEnDetalle = hoyUserDetalles.some(d => d.idPersonalizado === p.id);

                if (!yaEnDetalle) {
                    const medInfo = p.medicamento || allMedsForLookup.find(m => Number(m.id) === Number(pId));
                    const cantAct = medInfo?.inventario || 0;

                    const newDetalle = await createDetalle({
                        idMedicamento: pId,
                        idUsuario: currentUser.id,
                        cantidadContada: null,
                        cantidadActual: cantAct,
                        fechaRegistro: fechaHoy,
                        horaRegistro: null,
                        tipoConteo: 'Personalizado',
                        idPersonalizado: p.id
                    });

                    if (medInfo) {
                        await updateMedicamento(medInfo.id, { ...medInfo, estadoDelConteo: 'sí' });
                    }

                    hoyUserDetalles.push({
                        ...newDetalle,
                        medicamento: medInfo
                    });
                }
            }

            // 2. VINCULAR INFORMACIÓN DE MEDICAMENTOS FALTANTE
            hoyUserDetalles = hoyUserDetalles.map(d => {
                const medId = d.idMedicamento || d.medicamento?.id;
                if ((!d.medicamento || !d.medicamento.descripcion) && medId) {
                    const found = allMedsForLookup.find(m => Number(m.id) == Number(medId));
                    if (found) return { ...d, medicamento: found };
                }
                return d;
            });

            // 3. GENERAR TAREA CÍCLICA SI ES LA PRIMERA VEZ DE HOY O SI YA COMPLETÓ EL BLOQUE ACTUAL
            const cyclicTasks = hoyUserDetalles.filter(d => d.tipoConteo === 'Cíclico');
            const isFirstLoadOfToday = cyclicTasks.length === 0;
            const allCyclicCompleted = cyclicTasks.length > 0 && cyclicTasks.every(d => d.cantidadContada !== null);

            if (isFirstLoadOfToday || allCyclicCompleted) {
                const { generateABCBlock } = await import('../../utils/cycleGenerator');
                const generatedMeds = await generateABCBlock(currentUser, fechaHoy);
                
                if (generatedMeds.length > 0) {
                    const updatedRaw = await getAllDetalles(currentUser.id, fechaHoy);
                    hoyUserDetalles = updatedRaw.filter(d => normalizeDate(d.fechaRegistro) === fechaHoy);
                }
            }

            setDetalles(hoyUserDetalles.map(d => ({
                ...d,
                inputValue: d.cantidadContada !== null ? d.cantidadContada : '',
                inputLote: d.lote || '',
                inputFechaVencimiento: d.fechaVencimiento ? normalizeDate(d.fechaVencimiento) : ''
            })));
            Swal.close();

        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
            fetchingRef.current = false;
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    const handleInputValueChange = (targetId: number | string, newValue: string) => {
        if (newValue === '') {
            setDetalles(prev => prev.map(d => (d.id === targetId || d.tempId === targetId) ? { ...d, inputValue: '' } : d));
            return;
        }
        const numValue = Math.max(0, parseInt(newValue) || 0);
        setDetalles(prev => prev.map(d => (d.id === targetId || d.tempId === targetId) ? { ...d, inputValue: numValue } : d));
    };

    const handleLoteChange = (targetId: number | string, newValue: string) => {
        setDetalles(prev => prev.map(d => (d.id === targetId || d.tempId === targetId) ? { ...d, inputLote: newValue } : d));
    };

    const handleFechaVencimientoChange = (targetId: number | string, newValue: string) => {
        setDetalles(prev => prev.map(d => (d.id === targetId || d.tempId === targetId) ? { ...d, inputFechaVencimiento: newValue } : d));
    };

    const handleAddBatchRow = (parent: DetalleConteoEditable) => {
        // Contar cuántos lotes hay ya para este medicamento en la lista actual
        const currentBatchesCount = detalles.filter(d => 
            d.idMedicamento === parent.idMedicamento && 
            normalizeDate(d.fechaRegistro) === normalizeDate(parent.fechaRegistro)
        ).length;

        if (currentBatchesCount >= 11) { // 1 base + 10 extras
            Toast.fire({
                icon: 'warning',
                title: 'Límite alcanzado',
                text: 'Solo se permiten hasta 10 lotes adicionales por molécula.'
            });
            return;
        }

        const newRow: DetalleConteoEditable = {
            ...parent,
            id: 0, // ID dummy
            tempId: `new-${Date.now()}`,
            isNew: true,
            inputValue: '',
            inputLote: '',
            inputFechaVencimiento: '',
            cantidadContada: null,
            horaRegistro: null
        };
        
        // Insertar justo después del padre o del último lote de este padre
        setDetalles(prev => {
            const index = prev.findIndex(d => d.id === parent.id || (parent.tempId && d.tempId === parent.tempId));
            const newArray = [...prev];
            newArray.splice(index + 1, 0, newRow);
            return newArray;
        });
    };

    const handleRemoveBatchRow = (tempId: string) => {
        setDetalles(prev => prev.filter(d => d.tempId !== tempId));
    };

    const handleSavePartial = async () => {
        const toSave = detalles.filter(d => d.inputValue !== '' && d.cantidadContada === null);
        if (toSave.length === 0) {
            Toast.fire({ icon: 'info', title: 'No hay cambios para guardar.' });
            return;
        }

        // VALIDACIÓN: Verificar que todos tengan Lote y Fecha
        const incomplete = toSave.some(d => !d.inputLote?.trim() || !d.inputFechaVencimiento);
        if (incomplete) {
            Swal.fire({
                icon: 'warning',
                title: 'Campos incompletos',
                text: 'Debes ingresar el Lote y la Fecha de Vencimiento para todos los medicamentos que vas a reportar.',
                confirmButtonColor: '#f6952c'
            });
            return;
        }

        const result = await Swal.fire({
            title: '¿Enviar reportes?',
            text: `Vas a registrar ${toSave.length} cantidades.`,
            icon: 'question',
            showCancelButton: true,
            confirmButtonColor: '#f6952c',
            confirmButtonText: 'Sí, enviar ahora'
        });

        if (!result.isConfirmed) return;

        setLoading(true);
        Swal.fire({
            title: '<span class="text-2xl font-black text-gray-900 uppercase italic">Sincronizando</span>',
            html: `
                <div class="py-10">
                    <div class="w-20 h-20 border-8 border-orange-500 border-t-transparent rounded-full animate-spin mx-auto mb-6 shadow-2xl shadow-orange-500/20"></div>
                    <p class="text-gray-500 font-bold uppercase tracking-widest text-xs animate-pulse">Guardando ${toSave.length} reportes...</p>
                </div>
            `,
            allowOutsideClick: false,
            showConfirmButton: false
        });

        try {
            const now = new Date();
            const horaActual = now.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' });

            // Limpiar y preparar TODOS los registros a guardar (nuevos y existentes)
            const allToSave = toSave.map(d => ({
                id: d.id || 0,
                idMedicamento: d.idMedicamento,
                idUsuario: d.idUsuario,
                cantidadContada: Number(d.inputValue),
                cantidadActual: d.cantidadActual,
                fechaRegistro: d.fechaRegistro,
                horaRegistro: horaActual,
                tipoConteo: d.tipoConteo,
                idPersonalizado: d.idPersonalizado,
                lote: d.inputLote?.trim() || null,
                fechaVencimiento: d.inputFechaVencimiento || null
            }));

            // Enviar todo en una sola petición al endpoint de bulk
            await bulkUpdateDetalles(allToSave);

            await fetchData();
            
            Toast.fire({ icon: 'success', title: '¡Reportes sincronizados!' });
        } catch (error) {
            Swal.fire({ icon: 'error', title: 'Error al Guardar', text: 'No se pudieron enviar los reportes.' });
        } finally {
            setLoading(false);
        }
    };

    const filteredDetalles = detalles.filter(d => {
        const now = new Date();
        const today = now.getFullYear() + "-" + String(now.getMonth() + 1).padStart(2, '0') + "-" + String(now.getDate()).padStart(2, '0');

        return normalizeDate(d.fechaRegistro) === today && d.cantidadContada === null && (
            (d.medicamento?.descripcion.toLowerCase() || '').includes(searchTerm.toLowerCase()) ||
            (d.medicamento?.plu || '').includes(searchTerm)
        );
    });

    if (loading && detalles.length === 0) return (
        <div className="p-20 text-center">
            <div className="w-12 h-12 border-4 border-orange-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
            <p className="text-gray-500 font-bold animate-pulse uppercase tracking-widest text-xs">Cargando Tareas...</p>
        </div>
    );

    const userQuota = getCurrentUser()?.numeroConteo || 0;
    const detailsToSaveCount = detalles.filter(d => d.inputValue !== '' && d.cantidadContada === null).length;

    // Cálculo de estadísticas locales para hoy
    const uniqueAssignedMeds = new Set(detalles.filter(d => !d.isNew).map(d => d.idMedicamento)).size;
    const uniqueCountedMeds = new Set(detalles.filter(d => d.cantidadContada !== null).map(d => d.idMedicamento)).size;
    const avanceHoy = uniqueAssignedMeds > 0 ? Math.round((uniqueCountedMeds / uniqueAssignedMeds) * 100) : 0;
    const totalContadas = globalStats?.contados || 0;
    const totalMeds = globalStats?.total || 0;
    const avanceGral = totalMeds > 0 ? Math.round((totalContadas / totalMeds) * 100) : 0;

    return (
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-4 sm:space-y-6 pb-24">
            
            {/* Dashboard de Indicadores */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6">
                <div className="bg-white rounded-3xl p-6 shadow-sm border border-gray-100 group hover:border-orange-200 transition-all">
                    <span className="text-[10px] font-black text-gray-400 uppercase tracking-widest italic mb-2 block">Asignadas Hoy</span>
                    <div className="flex items-baseline gap-2">
                        <span className="text-4xl font-black text-gray-900">{uniqueAssignedMeds}</span>
                        <span className="text-xs font-bold text-gray-400 uppercase">Mols</span>
                    </div>
                </div>
                <div className="bg-white rounded-3xl p-6 shadow-sm border border-gray-100 group hover:border-orange-200 transition-all">
                    <span className="text-[10px] font-black text-gray-400 uppercase tracking-widest italic mb-2 block">Contadas Hoy</span>
                    <div className="flex items-baseline gap-2">
                        <span className="text-4xl font-black text-orange-500">{uniqueCountedMeds}</span>
                        <span className="text-xs font-bold text-gray-400 uppercase">Mols</span>
                    </div>
                </div>
                <div className="bg-white rounded-3xl p-6 shadow-sm border border-gray-100 group hover:border-orange-200 transition-all">
                    <span className="text-[10px] font-black text-gray-400 uppercase tracking-widest italic mb-2 block">Avance del Día</span>
                    <div className="flex items-center gap-4">
                        <span className="text-4xl font-black text-green-600">{avanceHoy}%</span>
                        <div className="flex-1 h-2 bg-gray-100 rounded-full overflow-hidden">
                            <div className="h-full bg-green-500 transition-all duration-500" style={{ width: `${avanceHoy}%` }}></div>
                        </div>
                    </div>
                </div>
                <div className="bg-white rounded-3xl p-6 shadow-sm border border-gray-100 group hover:border-orange-200 transition-all">
                    <span className="text-[10px] font-black text-gray-400 uppercase tracking-widest italic mb-2 block">General</span>
                    <div className="flex justify-between items-center">
                        <div>
                            <span className="text-2xl font-black text-gray-900">{totalContadas}</span>
                            <span className="text-[9px] font-bold text-gray-400 block uppercase leading-none">Totales</span>
                        </div>
                        <div className="text-right">
                            <span className="text-2xl font-black text-orange-500">{avanceGral}%</span>
                            <span className="text-[9px] font-bold text-gray-400 block uppercase leading-none">Progreso</span>
                        </div>
                    </div>
                </div>
            </div>

            {/* Header / Buscador Responsive */}
            <div className="bg-white rounded-3xl sm:rounded-[2.5rem] p-4 sm:p-8 shadow-sm border border-gray-100">
                <div className="flex flex-col lg:flex-row gap-6">
                    <div className="flex-1 relative">
                        <IconSearch className="absolute left-6 top-1/2 -translate-y-1/2 text-gray-400" size={20} />
                        <input
                            type="text"
                            placeholder="Buscar medicamento o PLU..."
                            className="w-full pl-16 pr-8 py-5 bg-gray-50 rounded-2xl border-2 border-transparent focus:border-orange-500 outline-none font-bold text-sm shadow-inner transition-all"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                        />
                    </div>
                </div>
            </div>

            {/* Vista de Escritorio (Tabla) */}
            <div className="hidden lg:block bg-white rounded-3xl sm:rounded-[2.5rem] shadow-xl border border-gray-100 overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full">
                        <thead>
                            <tr className="bg-gray-50/50">
                                <th className="px-8 py-6 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest italic">Medicamento / PLU</th>
                                <th className="px-8 py-6 text-center text-[10px] font-black text-gray-400 uppercase tracking-widest italic w-32">Lote</th>
                                <th className="px-8 py-6 text-center text-[10px] font-black text-gray-400 uppercase tracking-widest italic w-44">Vencimiento</th>
                                <th className="px-8 py-6 text-center text-[10px] font-black text-gray-400 uppercase tracking-widest italic w-32">Contado</th>
                                <th className="px-8 py-6 text-right text-[10px] font-black text-gray-400 uppercase tracking-widest italic">Estado</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-50">
                            {filteredDetalles.map((d) => (
                                <tr key={d.id ? d.id : d.tempId} className="hover:bg-gray-50/30 transition-colors group">
                                    <td className="px-8 py-8">
                                        <div className="flex items-center gap-5">
                                            <div className="w-12 h-12 bg-white rounded-2xl shadow-sm border border-orange-100 flex items-center justify-center text-orange-500 font-black group-hover:bg-orange-500 group-hover:text-white transition-all transform group-hover:rotate-6">
                                                {d.medicamento?.descripcion.charAt(0).toUpperCase()}
                                            </div>
                                            <div>
                                                <div className="font-black text-gray-900 text-sm uppercase tracking-tight leading-tight flex items-center gap-2">
                                                    {d.medicamento?.descripcion}
                                                    {!d.isNew && d.cantidadContada === null && (
                                                        <button 
                                                            onClick={() => handleAddBatchRow(d)}
                                                            className="p-1.5 bg-orange-50 text-orange-600 rounded-lg hover:bg-orange-500 hover:text-white transition-all shadow-sm"
                                                            title="Agregar otro lote"
                                                        >
                                                            <IconPlus size={14} stroke={3} />
                                                        </button>
                                                    )}
                                                    {d.isNew && (
                                                        <button 
                                                            onClick={() => handleRemoveBatchRow(d.tempId!)}
                                                            className="p-1.5 bg-red-50 text-red-600 rounded-lg hover:bg-red-500 hover:text-white transition-all shadow-sm"
                                                            title="Eliminar lote adicional"
                                                        >
                                                            <IconTrash size={14} stroke={3} />
                                                        </button>
                                                    )}
                                                </div>
                                                <div className="flex flex-col gap-0.5 mt-1">
                                                    <div className="text-[10px] font-bold text-gray-400 tracking-widest uppercase">
                                                        PLU: {d.medicamento?.plu} {d.isNew && <span className="text-orange-500 ml-1 font-black italic">(NUEVO LOTE)</span>}
                                                    </div>
                                                    <div className="text-[10px] font-black text-orange-400 tracking-widest uppercase italic">Programado: {normalizeDate(d.fechaRegistro)}</div>
                                                </div>
                                            </div>
                                        </div>
                                    </td>
                                    <td className="px-4 py-8">
                                        <input
                                            type="text"
                                            className={`w-full text-center py-4 rounded-xl border-2 border-transparent outline-none font-bold text-xs shadow-inner transition-all placeholder:text-gray-200 ${loading ? 'bg-gray-200 text-gray-400 cursor-not-allowed' : 'bg-gray-50 focus:border-orange-500 text-gray-600'}`}
                                            placeholder="LOTE"
                                            value={d.inputLote}
                                            onChange={(e) => handleLoteChange(d.id || d.tempId!, e.target.value)}
                                            disabled={loading}
                                        />
                                    </td>
                                    <td className="px-4 py-8">
                                        <input
                                            type="text"
                                            className={`w-full text-center py-4 rounded-xl border-2 border-transparent outline-none font-bold text-xs shadow-inner transition-all placeholder:text-gray-300 ${loading ? 'bg-gray-200 text-gray-400 cursor-not-allowed' : 'bg-gray-50 focus:border-orange-500 text-gray-600'}`}
                                            placeholder="AAAA-MM-DD"
                                            value={d.inputFechaVencimiento}
                                            onChange={(e) => handleFechaVencimientoChange(d.id || d.tempId!, e.target.value)}
                                            onFocus={(e) => e.target.type = 'date'}
                                            onBlur={(e) => { if(!e.target.value) e.target.type = 'text' }}
                                            disabled={loading}
                                        />
                                    </td>
                                    <td className="px-8 py-8">
                                        {d.cantidadContada !== null ? (
                                            <div className="text-center py-4 bg-green-50 rounded-2xl border border-green-100 text-xl font-black text-green-600 italic">
                                                {d.cantidadContada}
                                            </div>
                                        ) : (
                                            <input
                                                type="number"
                                                className={`w-full text-center py-5 rounded-2xl border-2 border-transparent outline-none font-black text-xl shadow-inner transition-all placeholder:text-gray-200 ${loading ? 'bg-gray-200 text-gray-400 cursor-not-allowed' : 'bg-gray-50 focus:border-orange-500 text-orange-600'}`}
                                                placeholder="0"
                                                value={d.inputValue}
                                                onChange={(e) => handleInputValueChange(d.id || d.tempId!, e.target.value)}
                                                disabled={loading}
                                            />
                                        )}
                                    </td>
                                    <td className="px-8 py-8 text-right">
                                        <span className={`inline-flex items-center gap-2 px-5 py-2.5 rounded-2xl text-[10px] font-black uppercase tracking-widest ${d.cantidadContada !== null ? 'bg-green-100 text-green-700' : 'bg-orange-50 text-orange-600'}`}>
                                            {d.cantidadContada !== null ? <IconCheck size={14} stroke={3} /> : 'Pendiente'}
                                        </span>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Vista Móvil (Tarjetas) */}
            <div className="lg:hidden space-y-4">
                {filteredDetalles.map((d) => (
                    <div key={d.id ? d.id : d.tempId} className={`bg-white rounded-3xl p-4 shadow-sm border-2 transition-all ${d.cantidadContada !== null ? 'border-green-100' : 'border-transparent active:scale-[0.98]'}`}>
                        <div className="flex justify-between items-start mb-4">
                            <div className="flex-1">
                                <h4 className="text-base font-black text-gray-900 leading-tight mb-2 uppercase">{d.medicamento?.descripcion}</h4>
                                <div className="flex flex-wrap gap-2 items-center">
                                    <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider bg-gray-50 px-2 py-1 rounded-lg">PLU: {d.medicamento?.plu}</span>
                                    <span className="text-[9px] font-bold text-gray-400 italic">Asignado: {normalizeDate(d.fechaRegistro)}</span>
                                </div>
                            </div>
                            <div className="ml-4">
                                        {d.cantidadContada !== null ? (
                                            <div className="w-10 h-10 bg-green-500 text-white rounded-xl flex items-center justify-center shadow-lg shadow-green-500/20">
                                                <IconCheck size={20} stroke={3} />
                                            </div>
                                        ) : (
                                            <div className="flex gap-2">
                                                {!d.isNew && (
                                                    <button 
                                                        onClick={() => handleAddBatchRow(d)}
                                                        className="w-10 h-10 bg-orange-100 text-orange-600 rounded-xl flex items-center justify-center active:scale-90 transition-transform"
                                                    >
                                                        <IconPlus size={20} stroke={3} />
                                                    </button>
                                                )}
                                                {d.isNew && (
                                                    <button 
                                                        onClick={() => handleRemoveBatchRow(d.tempId!)}
                                                        className="w-10 h-10 bg-red-100 text-red-600 rounded-xl flex items-center justify-center active:scale-90 transition-transform"
                                                    >
                                                        <IconTrash size={20} stroke={3} />
                                                    </button>
                                                )}
                                                <div className="w-10 h-10 bg-orange-100 text-orange-600 rounded-xl flex items-center justify-center">
                                                    <IconRefresh size={20} className="animate-spin-slow" />
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                </div>
                                {d.isNew && (
                                    <div className="px-2 py-1 bg-orange-500 text-white text-[8px] font-black uppercase rounded-full w-fit mb-2 animate-pulse">
                                        Nuevo Lote Adicional
                                    </div>
                                )}

                        <div className="space-y-4 border-t border-gray-50 pt-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1 block text-center">Lote</label>
                                    <input
                                        type="text"
                                        className={`w-full py-3 border-2 border-transparent rounded-xl text-center font-bold text-sm outline-none transition-all shadow-inner ${loading ? 'bg-gray-200 text-gray-400 cursor-not-allowed' : 'bg-gray-50 focus:border-orange-500 text-gray-600'}`}
                                        placeholder="LOTE"
                                        value={d.inputLote}
                                        onChange={(e) => handleLoteChange(d.id || d.tempId!, e.target.value)}
                                        disabled={loading}
                                    />
                                </div>
                                <div>
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1 block text-center">Vencimiento</label>
                                    <input
                                        type="text"
                                        className={`w-full py-3 border-2 border-transparent rounded-xl text-center font-bold text-sm outline-none transition-all shadow-inner placeholder:text-gray-300 ${loading ? 'bg-gray-200 text-gray-400 cursor-not-allowed' : 'bg-gray-50 focus:border-orange-500 text-gray-600'}`}
                                        placeholder="AAAA-MM-DD"
                                        value={d.inputFechaVencimiento}
                                        onChange={(e) => handleFechaVencimientoChange(d.id || d.tempId!, e.target.value)}
                                        onFocus={(e) => e.target.type = 'date'}
                                        onBlur={(e) => { if(!e.target.value) e.target.type = 'text' }}
                                        disabled={loading}
                                    />
                                </div>
                            </div>
                            <div className="pt-2">
                                <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2 block text-center">Cantidad Física Contada</label>
                                {d.cantidadContada !== null ? (
                                    <div className="w-full py-4 bg-green-50 text-green-600 text-2xl font-black text-center rounded-2xl border border-green-100">
                                        {d.cantidadContada}
                                    </div>
                                ) : (
                                    <input
                                        type="number"
                                        className={`w-full py-5 border-2 border-transparent rounded-2xl text-center font-black text-3xl outline-none transition-all shadow-inner ${loading ? 'bg-gray-200 text-gray-400 cursor-not-allowed' : 'bg-gray-50 focus:border-orange-500 text-orange-600'}`}
                                        placeholder="0"
                                        value={d.inputValue}
                                        onChange={(e) => handleInputValueChange(d.id || d.tempId!, e.target.value)}
                                        disabled={loading}
                                    />
                                )}
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {/* Notificación de Lista Vacía / Completada */}
            {filteredDetalles.length === 0 && !loading && (
                <div className="bg-white rounded-3xl sm:rounded-[2.5rem] p-12 text-center border-2 border-dashed border-gray-100 italic shadow-sm">
                    {detalles.length > 0 && detalles.every(d => d.cantidadContada !== null) ? (
                        <div className="flex flex-col items-center gap-4">
                            <div className="w-16 h-16 bg-green-50 text-green-500 rounded-full flex items-center justify-center shadow-inner">
                                <IconCheck size={32} stroke={3} />
                            </div>
                            <p className="text-green-600 font-black uppercase text-sm tracking-widest">¡Excelente trabajo!</p>
                            <p className="text-gray-400 font-bold uppercase text-[10px] tracking-tighter italic">Has completado todos los conteos asignados para este lote.</p>
                        </div>
                    ) : (
                        <p className="text-gray-400 font-black uppercase text-xs tracking-widest">No hay medicamentos para mostrar.</p>
                    )}
                </div>
            )}

            {/* Botón de Guardado Flotante */}
            <div className="fixed bottom-8 left-0 right-0 px-6 z-40 flex justify-center pointer-events-none">
                <div className="max-w-md w-full pointer-events-auto">
                    <button
                        onClick={handleSavePartial}
                        disabled={loading || detailsToSaveCount === 0}
                        className="w-full flex items-center justify-center gap-4 px-8 py-6 bg-gray-900 shadow-2xl text-white font-black rounded-[2.5rem] hover:bg-orange-600 transition-all active:scale-95 disabled:opacity-20 disabled:grayscale"
                    >
                        <IconDeviceFloppy size={24} />
                        <span className="text-lg uppercase tracking-tight">Sincronizar {detailsToSaveCount > 0 ? `(${detailsToSaveCount})` : ''} Reportes</span>
                    </button>
                </div>
            </div>
        </div>
    );
};

export default DetalleConteoTable;
