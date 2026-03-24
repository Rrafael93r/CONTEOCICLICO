import React, { useEffect, useState } from 'react';
import { getAllDetalles, createDetalle, updateDetalle, DetalleConteo } from '../../servicios/detalleConteoService';
import { getAllMedicamentos, updateMedicamento, Medicamento } from '../../servicios/medicamentoService';
import { getAllInventario } from '../../servicios/inventarioService';
import { getAllPersonalizados } from '../../servicios/personalizadoService';
import { getCurrentUser } from '../../servicios/authServices';
import Swal from 'sweetalert2';
import 'sweetalert2/dist/sweetalert2.min.css';

// Interfaz para la tabla, basada en el DetalleConteo ya creado en BD
interface DetalleConteoEditable extends DetalleConteo {
    inputValue: number | ''; // Valor temporal en el input antes de guardarse físicamente
}

const DetalleConteoTable: React.FC = () => {
    const [detalles, setDetalles] = useState<DetalleConteoEditable[]>([]);
    const [loading, setLoading] = useState(true);

    const Toast = Swal.mixin({
        toast: true,
        position: 'top-end',
        showConfirmButton: false,
        timer: 3000,
        timerProgressBar: true,
        didOpen: (toast) => {
            toast.addEventListener('mouseenter', Swal.stopTimer)
            toast.addEventListener('mouseleave', Swal.resumeTimer)
        }
    });

    const fetchData = async () => {
        setLoading(true);
        try {
            const currentUser = getCurrentUser();
            if (!currentUser) return;

            const fechaHoy = new Date().toLocaleDateString('en-CA'); // YYYY-MM-DD local

            // 1. Obtener registros de DetalleConteo ya existentes hoy
            let hoyUserDetalles = await getAllDetalles(currentUser.id, fechaHoy);

            // 2. Revisar si hay medicamentos "Personalizados" asignados para hoy
            const hoyPersonalizados = await getAllPersonalizados(currentUser.id, fechaHoy);
            const inventarioData = await getAllInventario();

            let newPersonalizedFound = false;
            for (const p of hoyPersonalizados) {
                // Verificar si este medicamento personalizado ya está en el detalle del día
                const yaEnDetalle = hoyUserDetalles.some(d => d.idMedicamento === p.idMedicamento);
                if (!yaEnDetalle) {
                    const invItem = inventarioData.find(inv => inv.idMedicamento === p.idMedicamento && inv.idUsuario === currentUser.id);
                    const cantAct = invItem ? invItem.cantidadActual : 0;

                    // Crear el registro de DetalleConteo como "Personalizado"
                    const newDetalle = await createDetalle({
                        idMedicamento: p.idMedicamento,
                        idUsuario: currentUser.id,
                        cantidadContada: null,
                        cantidadActual: cantAct,
                        fechaRegistro: fechaHoy,
                        horaRegistro: null,
                        tipoConteo: 'Personalizado'
                    });

                    // Marcar medicamento como contado (opcional, igual que en cíclico)
                    await updateMedicamento(p.idMedicamento, { id: p.idMedicamento, estadoDelConteo: 'sí' } as any);
                    
                    // IMPORTANTE: Unimos la información del medicamento para que se renderice en la tabla
                    hoyUserDetalles.push({
                        ...newDetalle,
                        medicamento: p.medicamento
                    });
                    newPersonalizedFound = true;
                }
            }

            // Fallback de seguridad: Si algún detalle no tiene el objeto medicamento, lo buscamos en la lista global
            const allMedsForLookup = await getAllMedicamentos();
            hoyUserDetalles = hoyUserDetalles.map(d => {
                if (!d.medicamento && d.idMedicamento) {
                    const found = allMedsForLookup.find(m => m.id === d.idMedicamento);
                    if (found) return { ...d, medicamento: found };
                }
                return d;
            });

            // Si encontramos personalizados nuevos, los recargamos o simplemente actualizamos la lista
            if (hoyUserDetalles.length > 0) {
                const prepared: DetalleConteoEditable[] = hoyUserDetalles.map(d => ({
                    ...d,
                    inputValue: d.cantidadContada !== null ? d.cantidadContada : ''
                }));
                setDetalles(prepared);
                setLoading(false);
                return;
            }

            // 3. Si no hay nada (ni previo ni personalizado), generamos el lote Cíclico
            const pendingMeds = allMedsForLookup.filter(m => 
                m.idUsuario === currentUser.id && m.estadoDelConteo?.toLowerCase() === 'no'
            );

            const sortedMeds = [...pendingMeds].sort((a, b) => 
                a.codigoGenerico.localeCompare(b.codigoGenerico, undefined, { numeric: true, sensitivity: 'base' })
            );

            // Agrupar por códigos genéricos únicos y validar inventario
            const quota = currentUser.numeroConteo || 10;
            const uniqueProductCodes: string[] = [];
            const selectedMeds: Medicamento[] = [];
            const skippedCodes: Set<string> = new Set();

            for (const med of sortedMeds) {
                if (uniqueProductCodes.includes(med.codigoGenerico)) {
                    const hasInv = inventarioData.some(inv => inv.idMedicamento === med.id && inv.idUsuario === currentUser.id);
                    if (hasInv) {
                        selectedMeds.push(med);
                    } else {
                        skippedCodes.add(med.codigoGenerico);
                    }
                    continue;
                }

                if (uniqueProductCodes.length < quota) {
                    const hasInv = inventarioData.some(inv => inv.idMedicamento === med.id && inv.idUsuario === currentUser.id);
                    if (hasInv) {
                        uniqueProductCodes.push(med.codigoGenerico);
                        selectedMeds.push(med);
                    } else {
                        skippedCodes.add(med.codigoGenerico);
                    }
                } else {
                    break;
                }
            }

            if (skippedCodes.size > 0) {
                Swal.fire({
                    icon: 'warning',
                    title: 'Productos Omitidos',
                    text: `Se omitieron ${skippedCodes.size} grupos de productos por no tener registro en la tabla de inventario.`,
                    confirmButtonColor: '#f6952c',
                    background: '#fff',
                    customClass: { popup: 'rounded-3xl' }
                });
            }

            if (selectedMeds.length === 0) {
                setDetalles([]);
                setLoading(false);
                return;
            }

            const createdDetalles: DetalleConteoEditable[] = [];

            await Promise.all(selectedMeds.map(async (m) => {
                const invItem = inventarioData.find(inv => inv.idMedicamento === m.id && inv.idUsuario === currentUser.id);
                const cantAct = invItem ? invItem.cantidadActual : 0;

                const newDetalle = await createDetalle({
                    idMedicamento: m.id,
                    idUsuario: currentUser.id,
                    cantidadContada: null,
                    cantidadActual: cantAct,
                    fechaRegistro: fechaHoy,
                    horaRegistro: null,
                    tipoConteo: 'Cíclico'
                });

                await updateMedicamento(m.id, { ...m, estadoDelConteo: 'sí' });

                createdDetalles.push({
                    ...newDetalle,
                    medicamento: m, // Attach medication info
                    inputValue: '' as number | ''
                });
            }));

            setDetalles(createdDetalles);

        } catch (error) {
            Swal.fire({
                icon: 'error',
                title: 'Error de Asignación',
                text: 'Ocurrió un error al asignar los medicamentos de hoy.',
                confirmButtonColor: '#d33',
                background: '#fff',
                customClass: { popup: 'rounded-3xl' }
            });
        } finally {
            setLoading(false);
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
            Toast.fire({
                icon: 'info',
                title: 'No hay cambios nuevos para guardar.'
            });
            return;
        }

        const result = await Swal.fire({
            title: '¿Enviar reportes?',
            text: `Vas a registrar ${toSave.length} cantidades en el sistema.`,
            icon: 'question',
            showCancelButton: true,
            confirmButtonColor: '#f6952c',
            cancelButtonColor: '#6e7881',
            confirmButtonText: 'Sí, enviar ahora',
            cancelButtonText: 'Cancelar',
            background: '#fff',
            customClass: {
                popup: 'rounded-3xl',
                confirmButton: 'rounded-2xl px-6 py-3',
                cancelButton: 'rounded-2xl px-6 py-3'
            }
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

            Toast.fire({
                icon: 'success',
                title: `¡Se han reportado ${toSave.length} registros exitosamente!`
            });
            fetchData();
        } catch (error) {
            Swal.fire({
                icon: 'error',
                title: 'Error al Guardar',
                text: 'No se pudieron enviar los reportes.',
                confirmButtonColor: '#d33',
                background: '#fff',
                customClass: { popup: 'rounded-3xl' }
            });
        } finally {
            setLoading(false);
        }
    };

    if (loading && detalles.length === 0) return (
        <div className="p-20 text-center">
            <div className="w-12 h-12 border-4 border-orange-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
            <p className="text-gray-500 font-bold animate-pulse">GENERANDO LOTE DE TRABAJO DIARIO...</p>
        </div>
    );

    return (
        <div className="max-w-7xl mx-auto space-y-6 pb-24">
            {/* Cabecera de la Página */}
            <div className="bg-white rounded-[2.5rem] shadow-xl shadow-orange-900/5 border border-orange-50/50 p-6 md:p-8 flex flex-col md:flex-row justify-between items-center gap-6">
                <div className="text-center md:text-left">
                    <div className="flex items-center justify-center md:justify-start gap-3 mb-2">
                        <div className="w-10 h-10 bg-orange-500 rounded-2xl flex items-center justify-center text-white shadow-lg shadow-orange-500/30">
                            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" /></svg>
                        </div>
                        <h3 className="text-2xl md:text-3xl font-black text-gray-900 tracking-tight">CONTEO CÍCLICO</h3>
                    </div>
                    <p className="text-xs md:text-sm text-gray-400 font-bold uppercase tracking-[0.2em] flex items-center justify-center md:justify-start gap-2">
                        <span className="w-2 h-2 bg-green-500 rounded-full"></span>
                        Sesión de: {new Date().toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' })}
                    </p>
                </div>
                
                <div className="flex flex-wrap justify-center items-center gap-3">
                    <div className="bg-orange-50 px-6 py-3 rounded-2xl border-2 border-orange-100/50 flex flex-col items-center">
                        <span className="text-[10px] font-black text-orange-400 uppercase tracking-widest leading-none mb-1">Pendientes</span>
                        <span className="text-xl font-black text-orange-600 leading-none">{detalles.filter(d => d.cantidadContada === null).length}</span>
                    </div>
                    <button 
                        onClick={fetchData}
                        className="p-4 text-orange-500 hover:bg-orange-500 hover:text-white transition-all bg-white rounded-2xl border-2 border-orange-100 shadow-sm active:scale-95 group"
                        title="Refrescar datos"
                    >
                        <svg className="w-6 h-6 group-hover:rotate-180 transition-transform duration-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 00-15.357-2m15.357 2H15" /></svg>
                    </button>
                </div>
            </div>

            {/* Vista Desktop: Tabla (Oculta en móviles < 1024px) */}
            <div className="hidden lg:block bg-white rounded-[2.5rem] shadow-2xl shadow-gray-200/50 border border-gray-100 overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="bg-gray-50/50 text-gray-400 text-[10px] font-black uppercase tracking-[0.2em]">
                                <th className="px-10 py-6">Medicamento</th>
                                <th className="px-10 py-6 text-center">Referencia</th>
                                <th className="px-10 py-6">Laboratorio</th>
                                <th className="px-10 py-6 text-center">Cantidad Contada</th>
                                <th className="px-10 py-6 text-center">Estado</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-50 text-sm">
                            {detalles.filter(d => d.cantidadContada === null).map((d) => (
                                <tr key={d.id} className="hover:bg-orange-50/10 transition-colors group">
                                    <td className="px-10 py-8">
                                        <div className="flex flex-col">
                                            <span className="text-gray-900 font-black text-lg group-hover:text-orange-600 transition-colors leading-tight">{d.medicamento?.descripcion}</span>
                                            <span className="text-[11px] text-gray-400 font-bold mt-1 uppercase tracking-wider">PLU: {d.medicamento?.plu}</span>
                                        </div>
                                    </td>
                                    <td className="px-10 py-8 text-center">
                                        <span className="bg-gray-100 text-gray-500 px-4 py-1.5 rounded-xl text-[11px] font-black tracking-wide border border-gray-200/50">
                                            {d.medicamento?.codigoGenerico}
                                        </span>
                                    </td>
                                    <td className="px-10 py-8 text-gray-500 font-bold italic">
                                        {d.medicamento?.laboratorio}
                                    </td>
                                    <td className="px-10 py-8">
                                        <div className="flex flex-col items-center gap-3">
                                            <input
                                                type="number"
                                                min="0"
                                                value={d.inputValue}
                                                disabled={d.cantidadContada !== null}
                                                onChange={(e) => handleInputValueChange(d.id, e.target.value)}
                                                className={`w-32 px-5 py-3 border-2 rounded-2xl outline-none transition-all font-black text-center text-xl shadow-sm ${
                                                    d.cantidadContada !== null 
                                                    ? "bg-gray-50 border-gray-100 text-gray-300 cursor-not-allowed opacity-60" 
                                                    : "border-gray-100 focus:border-orange-400 focus:ring-[12px] focus:ring-orange-500/5 text-orange-600 bg-gray-50/30 focus:bg-white"
                                                }`}
                                                placeholder="--"
                                            />
                                            {d.cantidadContada !== null && (
                                                <span className="text-[10px] text-green-500 font-black uppercase tracking-[0.1em] flex items-center gap-1.5">
                                                    <div className="w-1.5 h-1.5 bg-green-500 rounded-full"></div>
                                                    Sincronizado: {d.cantidadContada}
                                                </span>
                                            )}
                                        </div>
                                    </td>
                                    <td className="px-10 py-8">
                                        <div className="flex justify-center">
                                            {d.cantidadContada !== null ? (
                                                <span className="flex items-center gap-2 px-5 py-2 rounded-2xl bg-green-50 text-green-600 text-[10px] font-black uppercase tracking-widest border border-green-100">
                                                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" /></svg>
                                                    EXITOSO
                                                </span>
                                            ) : (
                                                <span className="flex items-center gap-2 px-5 py-2 rounded-2xl bg-amber-50 text-amber-600 text-[10px] font-black uppercase tracking-widest border border-amber-100 animate-pulse">
                                                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                                                    ESPERA
                                                </span>
                                            )}
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
                {detalles.filter(d => d.cantidadContada === null).map((d) => (
                    <div key={d.id} className={`bg-white rounded-3xl p-6 shadow-lg border-2 transition-all ${d.cantidadContada !== null ? 'border-green-100 opacity-80' : 'border-white hover:border-orange-200'}`}>
                        <div className="flex justify-between items-start mb-4">
                            <div className="flex-1">
                                <h4 className="text-lg font-black text-gray-900 leading-tight mb-1">{d.medicamento?.descripcion}</h4>
                                <div className="flex flex-wrap gap-2 items-center">
                                    <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">PLU: {d.medicamento?.plu}</span>
                                    <span className="w-1 h-1 bg-gray-300 rounded-full"></span>
                                    <span className="text-[10px] font-bold text-orange-400 bg-orange-50 px-2 py-0.5 rounded-lg">{d.medicamento?.codigoGenerico}</span>
                                </div>
                            </div>
                            {d.cantidadContada !== null && (
                                <div className="bg-green-100 p-2 rounded-xl text-green-600">
                                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" /></svg>
                                </div>
                            )}
                        </div>
                        
                        <div className="space-y-4">
                            <div className="flex items-center justify-between text-sm py-3 border-y border-gray-50 italic text-gray-500">
                                <span className="font-bold">Laboratorio:</span>
                                <span>{d.medicamento?.laboratorio}</span>
                            </div>
                            
                            <div className="flex items-center gap-4 pt-2">
                                <div className="flex-1">
                                    <label className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-2 block text-center">Cantidad física</label>
                                    <input
                                        type="number"
                                        min="0"
                                        value={d.inputValue}
                                        disabled={d.cantidadContada !== null}
                                        onChange={(e) => handleInputValueChange(d.id, e.target.value)}
                                        className={`w-full py-4 px-4 rounded-2xl text-center font-black text-2xl outline-none transition-all ${
                                            d.cantidadContada !== null
                                            ? 'bg-gray-100 text-gray-300 border-transparent shadow-none'
                                            : 'bg-orange-50/50 border-2 border-orange-100 focus:bg-white focus:border-orange-500 text-orange-600 shadow-sm'
                                        }`}
                                        placeholder="0"
                                    />
                                </div>
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {/* Empty State */}
            {detalles.filter(d => d.cantidadContada === null).length === 0 && !loading && (
                <div className="bg-white rounded-[2.5rem] p-20 text-center shadow-xl border border-gray-100">
                    <div className="flex flex-col items-center">
                        <div className="w-24 h-24 bg-orange-50 text-orange-200 rounded-[2rem] flex items-center justify-center mb-8 rotate-12">
                            <svg className="w-12 h-12" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 00-2 2H6a2 2 0 00-2 2V13m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" /></svg>
                        </div>
                        <h4 className="text-2xl font-black text-gray-800 tracking-tight">¡DÍA COMPLETADO!</h4>
                        <p className="text-gray-400 font-bold max-w-xs mt-3 uppercase text-xs tracking-[0.2em] leading-relaxed italic">No tienes más conteos pendientes asignados para hoy.</p>
                    </div>
                </div>
            )}

            {/* Botón de Acción Flotante (Permanente) */}
            <div className="fixed bottom-10 left-0 right-0 px-6 z-40 flex justify-center pointer-events-none">
                <div className="max-w-md w-full pointer-events-auto">
                    <button
                        onClick={handleSavePartial}
                        disabled={detalles.length === 0 || loading || !detalles.some(d => d.inputValue !== '' && d.cantidadContada === null)}
                        className="w-full flex items-center justify-center gap-4 px-8 py-6 bg-gray-900/90 backdrop-blur-md text-white font-black rounded-[2.5rem] shadow-[0_20px_50px_rgba(0,0,0,0.3)] hover:shadow-orange-500/40 hover:bg-orange-600 transition-all duration-500 group disabled:opacity-30 disabled:grayscale disabled:pointer-events-none transform hover:-translate-y-1 active:scale-95"
                    >
                        <div className="bg-orange-500 p-2 rounded-xl group-hover:bg-white group-hover:text-orange-600 transition-colors shadow-lg shadow-orange-500/20">
                            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M8 7H5a2 2 0 00-2 2v9a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-3m-1 4l-3 3m0 0l-3-3m3 3V4" /></svg>
                        </div>
                        <span className="text-lg tracking-tight uppercase">Sincronizar Reportes</span>
                    </button>
                </div>
            </div>
        </div>
    );
};

export default DetalleConteoTable;
