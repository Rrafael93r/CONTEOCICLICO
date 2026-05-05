import axios from './axiosConfig';

const API_URL = '/api/detalle-conteo';

export interface DetalleConteo {
    id: number;
    idMedicamento: number;
    idUsuario: number;
    medicamento?: {
        id: number;
        plu: string;
        descripcion: string;
        estadoDelConteo?: string;
        tipomolecula?: string;
        costo?: number;
    };
    usuario?: {
        usuario: string;
        sede: string;
    };
    cantidadContada: number | null;
    cantidadActual: number;
    fechaRegistro: string;
    horaRegistro: string | null;
    tipoConteo: string;
    idPersonalizado?: number;
    lote?: string;
    fechaVencimiento?: string | null;
}

export const getAllDetalles = async (idUsuario?: number, fecha?: string, startDate?: string, endDate?: string, sede?: string): Promise<DetalleConteo[]> => {
    try {
        const response = await axios.get(API_URL, {
            params: { idUsuario, fecha, startDate, endDate, sede }
        });
        return response.data;
    } catch (error) {
        throw error;
    }
};

export const createDetalle = async (detalle: Omit<DetalleConteo, 'id'>): Promise<DetalleConteo> => {
    try {
        const response = await axios.post(API_URL, detalle);
        return response.data;
    } catch (error) {
        throw error;
    }
};
export const updateDetalle = async (id: number, detalle: Partial<DetalleConteo>): Promise<DetalleConteo> => {
    try {
        const response = await axios.put(`${API_URL}/${id}`, detalle);
        return response.data;
    } catch (error) {
        throw error;
    }
};

export const bulkCreateDetalles = async (detalles: Omit<DetalleConteo, 'id'>[]): Promise<DetalleConteo[]> => {
    try {
        const response = await axios.post(`${API_URL}/bulk`, detalles);
        return response.data;
    } catch (error) {
        throw error;
    }
};

export const bulkUpdateDetalles = async (detalles: Partial<DetalleConteo>[]): Promise<DetalleConteo[]> => {
    try {
        const response = await axios.put(`${API_URL}/bulk`, detalles);
        return response.data;
    } catch (error) {
        throw error;
    }
};
