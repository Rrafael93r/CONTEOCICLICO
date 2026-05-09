import axios from './axiosConfig';

export const importCostosSede = async (file: File): Promise<any> => {
    const formData = new FormData();
    formData.append('file', file);

    try {
        const response = await axios.post('/api/costos-sede/importar', formData, {
            headers: {
                'Content-Type': 'multipart/form-data',
            },
        });
        return response.data;
    } catch (error) {
        console.error("Error al importar costos por sede:", error);
        throw error;
    }
};
