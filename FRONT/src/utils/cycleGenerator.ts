import { Medicamento } from '../servicios/medicamentoService';
import axios from 'axios';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

export const generateABCBlock = async (
    currentUser: any,
    allMedsForLookup: Medicamento[],
    currentIdsInTable: number[],
    fechaHoy: string
): Promise<Medicamento[]> => {
    try {
        const response = await axios.post(`${API_URL}/detalle-conteo/generar-bloque/${currentUser.id}?fechaHoy=${fechaHoy}`);
        return response.data;
    } catch (error) {
        console.error('Error generating block from backend', error);
        return [];
    }
};
