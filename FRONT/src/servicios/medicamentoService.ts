import axios from './axiosConfig';

const API_URL = '/api/medicamento';

export interface Medicamento {
    id: number;
    plu: string;
    descripcion: string;
    estadoDelConteo: string;
    tipomolecula?: string;
    idUsuario?: number;
    inventario?: number;
    costo?: number;
    costoTotal?: number;
    ciclosmes?: number;
    estadoConteoMensual?: number;
    contadoMesAnterior?: boolean;
}

export const getAllMedicamentos = async (idUsuario?: number): Promise<Medicamento[]> => {
    try {
        const response = await axios.get(API_URL, {
            params: { idUsuario }
        });
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
export const bulkImportMedicamentos = async (items: any[]) => {
    try {
        const response = await axios.post(`${API_URL}/bulk`, items);
        return response.data;
    } catch (error) {
        throw error;
    }
};
export const bulkUpdateInventory = async (items: any[]) => {
    try {
        const response = await axios.post(`${API_URL}/bulk-inventory`, items);
        return response.data;
    } catch (error) {
        throw error;
    }
};

export const syncInventoryData = async (items: any[]) => {
    try {
        const response = await axios.post(`${API_URL}/sync`, items);
        return response.data;
    } catch (error) {
        throw error;
    }
};

export const resetCycleByUsuario = async (idUsuario: number) => {
    try {
        const response = await axios.post(`${API_URL}/reset-cycle/${idUsuario}`);
        return response.data;
    } catch (error) {
        throw error;
    }
};

export const resetCycleByUsuarioAndTipo = async (idUsuario: number, tipo: string) => {
    try {
        const response = await axios.post(`${API_URL}/reset-cycle/${idUsuario}/${tipo}`);
        return response.data;
    } catch (error) {
        throw error;
    }
};

export const resetAllCyclesGlobal = async () => {
    try {
        const response = await axios.post(`${API_URL}/reset-all-cycles`);
        return response.data;
    } catch (error) {
        throw error;
    }
};

export const bulkUpdateMedicamentoStatus = async (ids: number[]) => {
    try {
        const response = await axios.put(`${API_URL}/bulk-status`, ids);
        return response.data;
    } catch (error) {
        throw error;
    }
};

export const getMedicamentoSummary = async (): Promise<any[]> => {
    const response = await axios.get(`${API_URL}/summary`);
    return response.data;
};

export const getDashboardStats = async (): Promise<any> => {
    const response = await axios.get(`${API_URL}/dashboard-stats`);
    return response.data;
};

export const searchMedicamentos = async (q: string, limit = 50, sede?: string): Promise<Medicamento[]> => {
    const response = await axios.get(`${API_URL}/search`, { params: { q, limit, sede } });
    return response.data;
};
