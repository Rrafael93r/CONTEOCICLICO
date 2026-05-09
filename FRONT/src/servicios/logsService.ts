import axios from './axiosConfig';

export interface LogCargaAutomatica {
    id: number;
    fechaInicio: string;
    fechaFin: string;
    nombreArchivo: string;
    registrosLeidos: number;
    registrosProcesados: number;
    estado: string;
    detalle: string;
}

export const getLogsCarga = async (): Promise<LogCargaAutomatica[]> => {
    try {
        const response = await axios.get('/api/logs-carga');
        return response.data;
    } catch (error) {
        console.error('Error fetching logs:', error);
        throw error;
    }
};

export const triggerManualSync = async (): Promise<any> => {
    try {
        const response = await axios.post('/api/logs-carga/trigger-sync');
        return response.data;
    } catch (error) {
        console.error('Error triggering sync:', error);
        throw error;
    }
};
