import axios from './axiosConfig';

const API_URL = '/api/personalizado';

export interface Personalizado {
    id: number;
    idMedicamento: number;
    idUsuario: number;
    medicamento?: {
        id: number;
        plu: string;
        descripcion: string;
        codigoGenerico: string;
        laboratorio: string;
        inventario: number;
    };
    usuario?: {
        usuario: string;
        sede: string;
    };
    fechaRegistro: string;
    horaRegistro: string;
    fechaProgramacion: string;
}

export const getAllPersonalizados = async (idUsuario?: number, fechaProgramacion?: string): Promise<Personalizado[]> => {
    try {
        const response = await axios.get(API_URL, {
            params: { idUsuario, fechaProgramacion }
        });
        return response.data;
    } catch (error) {
        throw error;
    }
};

export const createPersonalizado = async (personalizado: Omit<Personalizado, 'id'>): Promise<Personalizado> => {
    try {
        const response = await axios.post(API_URL, personalizado);
        return response.data;
    } catch (error) {
        throw error;
    }
};

export const deletePersonalizado = async (id: number): Promise<void> => {
    try {
        await axios.delete(`${API_URL}/${id}`);
    } catch (error) {
        throw error;
    }
};
