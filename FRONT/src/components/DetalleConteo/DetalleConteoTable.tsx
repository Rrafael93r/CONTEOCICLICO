import React, { useEffect, useState } from 'react';
import { getAllMedicamentos, updateMedicamento, Medicamento } from '../../servicios/medicamentoService';
import { createDetalle, getAllDetalles, updateDetalle, DetalleConteo } from '../../servicios/detalleConteoService';
import { getAllPersonalizados } from '../../servicios/personalizadoService';
import { getCurrentUser } from '../../servicios/authServices';
import Swal from 'sweetalert2';
import {
    IconSearch,
    IconPlus,
    IconCheck,
    IconEdit,
    IconDeviceFloppy,
    IconRefresh
} from '@tabler/icons-react';

interface DetalleConteoEditable extends DetalleConteo {
    inputValue: number | '';
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
    const [isFetching, setIsFetching] = useState(false);
    const [searchTerm, setSearchTerm] = useState('');
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
            const allMedsForLookup = await getAllMedicamentos();
            
            // Filtro local estricto adicional al remoto
            let rawDetalles = await getAllDetalles(currentUser.id, fechaHoy);
            let hoyUserDetalles = rawDetalles.filter(d => normalizeDate(d.fechaRegistro) === fechaHoy);
            
            let rawPersonalizados = await getAllPersonalizados(currentUser.id, fechaHoy);
            const hoyPersonalizados = rawPersonalizados.filter(p => normalizeDate(p.fechaProgramacion) === fechaHoy);

            // 1. PROCESAR PERSONALIZADOS (SIN DUPLICADOS)
            for (const p of hoyPersonalizados) {
                const pId = p.idMedicamento || p.medicamento?.id;
                if (!pId) continue;

                const yaEnDetalle = hoyUserDetalles.some(d => 
                    Number(d.idMedicamento || d.medicamento?.id) === Number(pId)
                );

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
                        tipoConteo: 'Personalizado'
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

            // 3. GENERAR TAREA CÍCLICA SI ES LA PRIMERA VEZ DE HOY
            const isFirstLoadOfToday = !hoyUserDetalles.some(d => d.tipoConteo === 'Cíclico');

            if (isFirstLoadOfToday) {
                const quota = currentUser.numeroConteo !== undefined ? currentUser.numeroConteo : 10;
                if (quota > 0) {
                    // EXCLUIR MEDICAMENTOS QUE YA ESTÁN EN LA TABLA DE HOY (COMO PERSONALIZADOS)
                    const currentIdsInTable = hoyUserDetalles.map(d => Number(d.idMedicamento || d.medicamento?.id));

                    const pendingMeds = allMedsForLookup.filter(m =>
                        m.idUsuario === currentUser.id && 
                        m.estadoDelConteo?.toLowerCase() === 'no' &&
                        !currentIdsInTable.includes(Number(m.id))
                    );

                    if (pendingMeds.length > 0) {
                        // PRIORIDAD: Los medicamentos con mayor COSTO TOTAL de primero
                        const sortedMeds = [...pendingMeds].sort((a, b) => 
                            (b.costoTotal || 0) - (a.costoTotal || 0)
                        );

                        const uniqueProductCodes: string[] = [];
                        const selectedMeds: Medicamento[] = [];

                        for (const med of sortedMeds) {
                            if (uniqueProductCodes.includes(med.codigoGenerico)) {
                                if (med.inventario > 0) selectedMeds.push(med);
                                continue;
                            }

                            if (uniqueProductCodes.length < quota) {
                                if (med.inventario > 0) {
                                    uniqueProductCodes.push(med.codigoGenerico);
                                    selectedMeds.push(med);
                                }
                            } else {
                                break;
                            }
                        }

                        if (selectedMeds.length > 0) {
                            await Promise.all(selectedMeds.map(async (m) => {
                                const cantAct = m.inventario || 0;
                                await createDetalle({
                                    idMedicamento: m.id,
                                    idUsuario: currentUser.id,
                                    cantidadContada: null,
                                    cantidadActual: cantAct,
                                    fechaRegistro: fechaHoy,
                                    horaRegistro: null,
                                    tipoConteo: 'Cíclico'
                                });
                                await updateMedicamento(m.id, { ...m, estadoDelConteo: 'sí' });
                            }));
                            
                            const updatedRaw = await getAllDetalles(currentUser.id, fechaHoy);
                            hoyUserDetalles = updatedRaw.filter(d => normalizeDate(d.fechaRegistro) === fechaHoy);
                        }
                    }
                }
            }

            setDetalles(hoyUserDetalles.map(d => ({
                ...d,
                inputValue: d.cantidadContada !== null ? d.cantidadContada : ''
            })));

        } catch (error) {
            Swal.fire({
                icon: 'error',
                title: 'Error de carga',
                text: 'No se pudieron procesar los medicamentos de hoy.'
            });
        } finally {
            setLoading(false);
            fetchingRef.current = false;
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    const handleInputValueChange = (id: number, newValue: string) => {
        if (newValue === '') {
            setDetalles(prev => prev.map(d => d.id === id ? { ...d, inputValue: '' } : d));
            return;
        }
        const numValue = Math.max(0, parseInt(newValue) || 0);
        setDetalles(prev => prev.map(d => d.id === id ? { ...d, inputValue: numValue } : d));
    };

    const handleSavePartial = async () => {
        const toSave = detalles.filter(d => d.inputValue !== '' && d.cantidadContada === null);
        if (toSave.length === 0) {
            Toast.fire({ icon: 'info', title: 'No hay cambios para guardar.' });
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
        try {
            await Promise.all(toSave.map(async (d) => {
                const now = new Date();
                const horaActual = now.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
                await updateDetalle(d.id, {
                    ...d,
                    cantidadContada: Number(d.inputValue),
                    horaRegistro: horaActual
                });
            }));

            Toast.fire({ icon: 'success', title: '¡Reportes sincronizados!' });
            fetchData();
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
            (d.medicamento?.plu || '').includes(searchTerm) ||
            (d.medicamento?.codigoGenerico.toLowerCase() || '').includes(searchTerm.toLowerCase())
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

    return (
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-4 sm:space-y-6 pb-24">
            {/* Header / Buscador Responsive */}
            <div className="bg-white rounded-3xl sm:rounded-[2.5rem] p-4 sm:p-8 shadow-sm border border-gray-100 mb-4 sm:mb-8">
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
                                <th className="px-8 py-6 text-center text-[10px] font-black text-gray-400 uppercase tracking-widest italic">Laboratorio</th>
                                <th className="px-8 py-6 text-center text-[10px] font-black text-gray-400 uppercase tracking-widest italic w-32">Código</th>
                                <th className="px-8 py-6 text-center text-[10px] font-black text-gray-400 uppercase tracking-widest italic w-48">Contado</th>
                                <th className="px-8 py-6 text-right text-[10px] font-black text-gray-400 uppercase tracking-widest italic">Estado</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-50">
                            {filteredDetalles.map((d) => (
                                <tr key={d.id} className="hover:bg-gray-50/30 transition-colors group">
                                    <td className="px-8 py-8">
                                        <div className="flex items-center gap-5">
                                            <div className="w-12 h-12 bg-white rounded-2xl shadow-sm border border-orange-100 flex items-center justify-center text-orange-500 font-black group-hover:bg-orange-500 group-hover:text-white transition-all transform group-hover:rotate-6">
                                                {d.medicamento?.descripcion.charAt(0).toUpperCase()}
                                            </div>
                                            <div>
                                                <div className="font-black text-gray-900 text-sm uppercase tracking-tight leading-tight">{d.medicamento?.descripcion}</div>
                                                <div className="flex flex-col gap-0.5 mt-1">
                                                    <div className="text-[10px] font-bold text-gray-400 tracking-widest uppercase">PLU: {d.medicamento?.plu}</div>
                                                    <div className="text-[10px] font-black text-orange-400 tracking-widest uppercase italic">Programado: {normalizeDate(d.fechaRegistro)}</div>
                                                </div>
                                            </div>
                                        </div>
                                    </td>
                                    <td className="px-8 py-8 text-center text-[10px] font-bold text-gray-400 uppercase tracking-tighter">
                                        {d.medicamento?.laboratorio}
                                    </td>
                                    <td className="px-8 py-8 text-center">
                                        <span className="text-xs font-black text-orange-500 italic bg-orange-50 px-3 py-1.5 rounded-lg border border-orange-100">{d.medicamento?.codigoGenerico}</span>
                                    </td>
                                    <td className="px-8 py-8">
                                        {d.cantidadContada !== null ? (
                                            <div className="text-center py-4 bg-green-50 rounded-2xl border border-green-100 text-xl font-black text-green-600 italic">
                                                {d.cantidadContada}
                                            </div>
                                        ) : (
                                            <input
                                                type="number"
                                                className="w-full text-center py-5 bg-gray-50 rounded-2xl border-2 border-transparent focus:border-orange-500 outline-none font-black text-orange-600 text-xl shadow-inner transition-all placeholder:text-gray-200"
                                                placeholder="0"
                                                value={d.inputValue}
                                                onChange={(e) => handleInputValueChange(d.id, e.target.value)}
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
                    <div key={d.id} className={`bg-white rounded-3xl p-4 shadow-sm border-2 transition-all ${d.cantidadContada !== null ? 'border-green-100' : 'border-transparent active:scale-[0.98]'}`}>
                        <div className="flex justify-between items-start mb-4">
                            <div className="flex-1">
                                <h4 className="text-base font-black text-gray-900 leading-tight mb-2 uppercase">{d.medicamento?.descripcion}</h4>
                                <div className="flex flex-wrap gap-2 items-center">
                                    <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider bg-gray-50 px-2 py-1 rounded-lg">PLU: {d.medicamento?.plu}</span>
                                    <span className="text-[10px] font-black text-orange-500 bg-orange-50 px-2 py-1 rounded-lg uppercase border border-orange-100">CÓD: {d.medicamento?.codigoGenerico}</span>
                                    <span className="text-[9px] font-bold text-gray-400 italic">Asignado: {normalizeDate(d.fechaRegistro)}</span>
                                </div>
                            </div>
                            <div className="ml-4">
                                {d.cantidadContada !== null ? (
                                    <div className="w-10 h-10 bg-green-500 text-white rounded-xl flex items-center justify-center shadow-lg shadow-green-500/20">
                                        <IconCheck size={20} stroke={3} />
                                    </div>
                                ) : (
                                    <div className="w-10 h-10 bg-orange-100 text-orange-600 rounded-xl flex items-center justify-center">
                                        <IconRefresh size={20} className="animate-spin-slow" />
                                    </div>
                                )}
                            </div>
                        </div>

                        <div className="space-y-4 border-t border-gray-50 pt-4">
                            <div className="flex items-center justify-between text-[11px] font-bold uppercase text-gray-400">
                                <span>Laboratorio:</span>
                                <span className="text-gray-600">{d.medicamento?.laboratorio}</span>
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
                                        className="w-full py-5 bg-gray-50 border-2 border-transparent focus:border-orange-500 rounded-2xl text-center font-black text-3xl text-orange-600 outline-none transition-all shadow-inner"
                                        placeholder="0"
                                        value={d.inputValue}
                                        onChange={(e) => handleInputValueChange(d.id, e.target.value)}
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
