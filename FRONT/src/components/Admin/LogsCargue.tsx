import React, { useEffect, useState } from 'react';
import { getLogsCarga, triggerManualSync, LogCargaAutomatica } from '../../servicios/logsService';
import Swal from 'sweetalert2';
import { IconRefresh, IconFileDescription, IconCheck, IconX, IconAlertTriangle } from '@tabler/icons-react';

const LogsCargue: React.FC = () => {
    const [logs, setLogs] = useState<LogCargaAutomatica[]>([]);
    const [loading, setLoading] = useState(false);
    const [selectedLog, setSelectedLog] = useState<LogCargaAutomatica | null>(null);

    useEffect(() => {
        loadLogs();
    }, []);

    const loadLogs = async () => {
        setLoading(true);
        try {
            const data = await getLogsCarga();
            setLogs(data);
        } catch (error) {
            console.error(error);
            Swal.fire('Error', 'No se pudieron cargar los logs', 'error');
        } finally {
            setLoading(false);
        }
    };

    const handleTriggerSync = async () => {
        Swal.fire({
            title: '¿Sincronizar ahora?',
            text: "Esto buscará archivos en el SFTP y los importará.",
            icon: 'question',
            showCancelButton: true,
            confirmButtonColor: '#f6952c',
            cancelButtonColor: '#d33',
            confirmButtonText: 'Sí, sincronizar'
        }).then(async (result) => {
            if (result.isConfirmed) {
                try {
                    await triggerManualSync();
                    Swal.fire(
                        'Iniciado!',
                        'La sincronización manual ha comenzado. Actualiza los logs en unos minutos.',
                        'success'
                    );
                    loadLogs();
                } catch (error) {
                    Swal.fire('Error', 'No se pudo iniciar la sincronización', 'error');
                }
            }
        });
    };

    const formatDate = (dateString: string) => {
        if (!dateString) return 'N/A';
        const date = new Date(dateString);
        return date.toLocaleString();
    };

    const getStatusIcon = (estado: string) => {
        switch (estado) {
            case 'EXITOSO': return <IconCheck size={20} className="text-emerald-500" />;
            case 'ERROR': return <IconAlertTriangle size={20} className="text-orange-500" />;
            case 'FALLIDO': return <IconX size={20} className="text-red-500" />;
            default: return <div className="w-5 h-5 rounded-full border-2 border-t-blue-500 animate-spin"></div>;
        }
    };

    const getStatusStyle = (estado: string) => {
        switch (estado) {
            case 'EXITOSO': return 'bg-emerald-50 text-emerald-700 border-emerald-200';
            case 'ERROR': return 'bg-orange-50 text-orange-700 border-orange-200';
            case 'FALLIDO': return 'bg-red-50 text-red-700 border-red-200';
            default: return 'bg-blue-50 text-blue-700 border-blue-200';
        }
    };

    return (
        <div className="p-4 sm:p-8">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-8">
                <div>
                    <h3 className="text-2xl font-black text-gray-900 uppercase">Monitor de Sincronización SFTP</h3>
                    <p className="text-[10px] text-gray-400 font-bold uppercase tracking-widest mt-1">Registros de cargue automático y manual.</p>
                </div>
                <div className="flex gap-4">
                    <button
                        onClick={loadLogs}
                        disabled={loading}
                        className="flex items-center gap-2 px-6 py-3 bg-gray-100 text-gray-600 rounded-xl font-black text-xs uppercase hover:bg-gray-200 transition-all"
                    >
                        <IconRefresh size={18} className={loading ? 'animate-spin' : ''} /> Actualizar
                    </button>
                    <button
                        onClick={handleTriggerSync}
                        className="flex items-center gap-2 px-6 py-3 bg-orange-500 text-white rounded-xl font-black text-xs uppercase shadow-xl shadow-orange-500/20 hover:bg-orange-600 transition-all"
                    >
                        <IconFileDescription size={18} /> Forzar Sincronización SFTP
                    </button>
                </div>
            </div>

            <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full">
                        <thead className="bg-gray-50 border-b border-gray-100">
                            <tr>
                                <th className="px-6 py-4 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Estado</th>
                                <th className="px-6 py-4 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Archivo</th>
                                <th className="px-6 py-4 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Fecha Inicio</th>
                                <th className="px-6 py-4 text-left text-[10px] font-black text-gray-400 uppercase tracking-widest">Fecha Fin</th>
                                <th className="px-6 py-4 text-center text-[10px] font-black text-gray-400 uppercase tracking-widest">Líneas</th>
                                <th className="px-6 py-4 text-center text-[10px] font-black text-gray-400 uppercase tracking-widest">Acciones</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-50">
                            {logs.length === 0 ? (
                                <tr>
                                    <td colSpan={6} className="px-6 py-8 text-center text-sm font-bold text-gray-400">
                                        No hay registros de cargas automáticas.
                                    </td>
                                </tr>
                            ) : (
                                logs.map((log) => (
                                    <tr key={log.id} className="hover:bg-gray-50/50 transition-colors">
                                        <td className="px-6 py-4">
                                            <div className={`inline-flex items-center gap-2 px-3 py-1 rounded-full border text-[10px] font-black uppercase ${getStatusStyle(log.estado)}`}>
                                                {getStatusIcon(log.estado)}
                                                {log.estado}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 text-sm font-black text-gray-800">{log.nombreArchivo || 'Sincronización'}</td>
                                        <td className="px-6 py-4 text-xs font-bold text-gray-500">{formatDate(log.fechaInicio)}</td>
                                        <td className="px-6 py-4 text-xs font-bold text-gray-500">{formatDate(log.fechaFin)}</td>
                                        <td className="px-6 py-4 text-center">
                                            <span className="text-sm font-black bg-gray-100 px-3 py-1 rounded-lg text-gray-600">
                                                {log.registrosLeidos || 0}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 text-center">
                                            <button
                                                onClick={() => setSelectedLog(log)}
                                                className="text-orange-500 hover:text-orange-600 text-xs font-black uppercase tracking-widest"
                                            >
                                                Ver Detalles
                                            </button>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </div>

            {selectedLog && (
                <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-gray-900/50 backdrop-blur-sm">
                    <div className="bg-white rounded-[2rem] shadow-2xl w-full max-w-3xl max-h-[80vh] flex flex-col overflow-hidden">
                        <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-gray-50">
                            <div>
                                <h3 className="text-xl font-black text-gray-900 uppercase">Detalles del Cargue</h3>
                                <p className="text-xs font-bold text-gray-500">{selectedLog.nombreArchivo}</p>
                            </div>
                            <button
                                onClick={() => setSelectedLog(null)}
                                className="w-10 h-10 bg-white rounded-xl shadow-sm flex items-center justify-center hover:bg-red-50 text-gray-400 hover:text-red-500 transition-colors"
                            >
                                <IconX size={20} />
                            </button>
                        </div>
                        <div className="p-6 overflow-y-auto flex-1">
                            <pre className="text-[10px] sm:text-xs font-mono text-gray-600 whitespace-pre-wrap bg-gray-900 p-4 rounded-2xl text-gray-300">
                                {selectedLog.detalle || 'No hay detalles disponibles para este proceso.'}
                            </pre>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default LogsCargue;
