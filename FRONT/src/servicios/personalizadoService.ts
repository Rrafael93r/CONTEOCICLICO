import axios from './axiosConfig';

const API_URL = 'http://localhost:8080/api/personalizado';

export interface Personalizado {
    id: number;
    idMedicamento: number;
    idUsuario: number;
    medicamento?: {
        plu: string;
        descripcion: string;
        codigoGenerico: string;
        laboratorio: string;
    };
    usuario?: {
        usuario: string;
        sede: string;
    };
    fechaRegistro: string;
    horaRegistro: string;
    fechaProgramacion: string;
}

export const getAllPersonalizados = async (idUsuario?: number): Promise<Personalizado[]> => {
    try {
        const response = await axios.get(API_URL, {
            params: { idUsuario }
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
