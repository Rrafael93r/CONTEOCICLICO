import axios from './axiosConfig';

export interface MaestraMedicamento {
    id?: number;
    item?: number;
    plu: string;
    codigogenerico?: string;
    nombre: string;
    nombreComercial?: string;
    laboratorio?: string;
    formaFarmaceutica?: string;
    concentracion?: string;
    concentracion2?: string;
    unidadConcentracion?: string;
    unidadContenido?: string;
    concentracionAgrupada?: string;
    registroSanitario?: string;
    cum?: string;
    contrato?: string;
    unidadMedida?: string;
    fechaInactivacion?: string;
    usuarioInactivacion?: string;
    ripsCodigo?: string;
    ripsUnidad?: string;
    fechaCarga?: string;
}

export const getAllMaestra = async (): Promise<MaestraMedicamento[]> => {
    const response = await axios.get('/api/maestra');
    return response.data;
};

export const getMaestraById = async (id: number): Promise<MaestraMedicamento> => {
    const response = await axios.get(`/api/maestra/${id}`);
    return response.data;
};

export const getMaestraByPlu = async (plu: string): Promise<MaestraMedicamento> => {
    const response = await axios.get(`/api/maestra/plu/${plu}`);
    return response.data;
};

export const createMaestra = async (data: MaestraMedicamento): Promise<MaestraMedicamento> => {
    const response = await axios.post('/api/maestra', data);
    return response.data;
};

export const updateMaestra = async (id: number, data: MaestraMedicamento): Promise<MaestraMedicamento> => {
    const response = await axios.put(`/api/maestra/${id}`, data);
    return response.data;
};

export const deleteMaestra = async (id: number): Promise<void> => {
    await axios.delete(`/api/maestra/${id}`);
};

export const bulkCreateMaestra = async (data: MaestraMedicamento[]): Promise<MaestraMedicamento[]> => {
    const response = await axios.post('/api/maestra/bulk', data);
    return response.data;
};

export const importMaestra = async (file: File): Promise<any> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await axios.post('/api/maestra/importar', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    });
    return response.data;
};
