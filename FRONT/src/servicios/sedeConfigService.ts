import axios from './axiosConfig';

const API_URL = '/api/sede-config';

export interface SedeConfig {
    id?: number;
    codigoSede: string;
    nombre?: string;
    numeroConteo: number;
    tipoConteo: string;
    operacionInicio?: string;
    operacionFin?: string;
    activo?: number;
}

export const getAllSedeConfigs = async (): Promise<SedeConfig[]> => {
    const response = await axios.get(API_URL);
    return response.data;
};

export const createSedeConfig = async (config: SedeConfig): Promise<SedeConfig> => {
    const response = await axios.post(API_URL, config);
    return response.data;
};

export const updateSedeConfig = async (id: number, config: Partial<SedeConfig>): Promise<SedeConfig> => {
    const response = await axios.put(`${API_URL}/${id}`, config);
    return response.data;
};

export const syncSedes = async (): Promise<string> => {
    const response = await axios.post(`${API_URL}/sync`);
    return response.data;
};
