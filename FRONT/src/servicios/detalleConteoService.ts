import axios from './axiosConfig';

const API_URL = 'http://localhost:8080/api/detalle-conteo';

export interface DetalleConteo {
    id: number;
    idMedicamento: number;
    idUsuario: number;
    medicamento?: {
        codigoGenerico: string;
        descripcion: string;
    };
    usuario?: {
        usuario: string;
    };
    cantidadContada: number;
    cantidadActual: number;
    fechaRegistro: string;
    horaRegistro: string;
    tipoConteo: string;
}

export const getAllDetalles = async (): Promise<DetalleConteo[]> => {
    try {
        const response = await axios.get(API_URL);
        return response.data;
    } catch (error) {
        console.error('Error fetching DetalleConteo:', error);
        throw error;
    }
};

export const createDetalle = async (detalle: Omit<DetalleConteo, 'id'>): Promise<DetalleConteo> => {
    try {
        const response = await axios.post(API_URL, detalle);
        return response.data;
    } catch (error) {
        console.error('Error creating DetalleConteo:', error);
        throw error;
    }
};
