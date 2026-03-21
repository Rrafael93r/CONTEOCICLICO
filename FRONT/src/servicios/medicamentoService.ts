import axios from './axiosConfig';

const API_URL = 'http://localhost:8080/api/medicamento';

export interface Medicamento {
    id: number;
    plu: string;
    descripcion: string;
    codigoGenerico: string;
    laboratorio: string;
    estadoDelConteo: string;
    idUsuario?: number;
}

export const getAllMedicamentos = async (): Promise<Medicamento[]> => {
    try {
        const response = await axios.get(API_URL);
        return response.data;
    } catch (error) {
        throw error;
    }
};

export const updateMedicamento = async (id: number, medicamento: Partial<Medicamento>): Promise<Medicamento> => {
    try {
        const response = await axios.put(`${API_URL}/${id}`, medicamento);
        return response.data;
    } catch (error) {
        throw error;
    }
};
