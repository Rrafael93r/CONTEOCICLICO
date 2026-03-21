import axios from './axiosConfig';

const API_URL = 'http://localhost:8080/api';

export const getFarmacias = async () => {
  try {
    const response = await axios.get(`${API_URL}/farmacia`);
    return response.data;
  } catch (error) {
    console.error('Error fetching farmacias:', error);
    throw error;
  }
};

export const getCiudades = async () => {
  try {
    const response = await axios.get(`${API_URL}/ciudades`);
    return response.data;
  } catch (error) {
    console.error('Error fetching ciudades:', error);
    throw error;
  }
};

export const getProveedores = async () => {
  try {
    const response = await axios.get(`${API_URL}/proveedorinternet`);
    return response.data;
  } catch (error) {
    console.error('Error fetching proveedores:', error);
    throw error;
  }
};

export const getCanalesTransmision = async () => {
  try {
    const response = await axios.get(`${API_URL}/canaltransmision`);
    return response.data;
  } catch (error) {
    console.error('Error fetching canales de transmisión:', error);
    throw error;
  }
};

export const deleteFarmacia = async (id: number) => {
  try {
    const response = await axios.put(`${API_URL}/farmacia/softDelete/${id}`);
    return response.data;
  } catch (error) {
    console.error('Error al eliminar la farmacia:', error);
    throw error;
  }
};

export const createFarmacia = async (data: any) => {
  try {
    const response = await axios.post(`${API_URL}/farmacia`, data);
    return response.data;
  } catch (error) {
    console.error('Error al crear la farmacia:', error);
    throw new Error('Error al crear la farmacia');
  }
};

export const getFarmaciaById = async (id: number) => {
  const response = await axios.get(`${API_URL}/farmacia/${id}`);
  return response.data;
};

export const updateFarmacia = async (id: number, farmaciaData: any) => {
  try {
    const response = await axios.put(`${API_URL}/farmacia/${id}`, farmaciaData);
    return response.data;
  } catch (error) {
    console.error('Error al actualizar Farmacia:', error);
    throw error; 
  }
};
