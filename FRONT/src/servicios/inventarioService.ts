import axios from './axiosConfig';

const API_URL = '/api/inventario';

export interface Inventario {
    id: number;
    idMedicamento: number;
    idUsuario: number;
    cantidadActual: number;
    medicamento?: any;
    usuario?: any;
}

export const getAllInventario = async (): Promise<Inventario[]> => {
    try {
        const response = await axios.get(API_URL);
        return response.data;
    } catch (error) {
        throw error;
    }
};
export const bulkImportInventario = async (items: any[]) => {
    try {
        const response = await axios.post(`${API_URL}/bulk`, items);
        return response.data;
    } catch (error) {
        throw error;
    }
};
