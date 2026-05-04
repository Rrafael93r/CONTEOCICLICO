import { Medicamento } from '../servicios/medicamentoService';
import axios from '../servicios/axiosConfig';

export const generateABCBlock = async (
    currentUser: any,
    fechaHoy: string
): Promise<Medicamento[]> => {
    try {
        const response = await axios.post(`/api/detalle-conteo/generar-bloque/${currentUser.id}?fechaHoy=${fechaHoy}`);
        return response.data;
    } catch (error) {
        return [];
    }
};
