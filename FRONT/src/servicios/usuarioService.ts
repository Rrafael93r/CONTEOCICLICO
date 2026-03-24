import axios from './axiosConfig';

const API_URL = '/api/usuario';

export interface Usuario {
    id: number;
    usuario: string;
    sede: string;
    numeroConteo?: number;
    rol?: {
        id: number;
        nombre: string;
    };
}

export const getAllUsuarios = async (): Promise<Usuario[]> => {
    try {
        const response = await axios.get(API_URL);
        return response.data;
    } catch (error) {
        throw error;
    }
};

export const updateUsuario = async (id: number, usuario: Partial<Usuario>): Promise<Usuario> => {
    try {
        const response = await axios.put(`${API_URL}/${id}`, usuario);
        return response.data;
    } catch (error) {
        throw error;
    }
};
