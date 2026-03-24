import axios from './axiosConfig';

const API_URL = '/api';

export const getFarmacias = async () => {
  try {
    const response = await axios.get(`${API_URL}/farmacia`);
    return response.data;
  } catch (error) {
    throw error;
  }
};

export const getCiudades = async () => {
  try {
    const response = await axios.get(`${API_URL}/ciudad`);
    return response.data;
  } catch (error) {
    throw error;
  }
};

export const getProveedores = async () => {
  try {
    const response = await axios.get(`${API_URL}/proveedor`);
    return response.data;
  } catch (error) {
    throw error;
  }
};

export const getCanalesTransmision = async () => {
  try {
    const response = await axios.get(`${API_URL}/canal-transmision`);
    return response.data;
  } catch (error) {
    throw error;
  }
};

export const deleteFarmacia = async (id: number) => {
  try {
    await axios.delete(`${API_URL}/farmacia/${id}`);
  } catch (error) {
    throw error;
  }
};

export const createFarmacia = async (farmacia: any) => {
  try {
    const response = await axios.post(`${API_URL}/farmacia`, farmacia);
    return response.data;
  } catch (error) {
    throw error;
  }
};

export const updateFarmacia = async (id: number, farmacia: any) => {
  try {
    const response = await axios.put(`${API_URL}/farmacia/${id}`, farmacia);
    return response.data;
  } catch (error) {
    throw error;
  }
};
