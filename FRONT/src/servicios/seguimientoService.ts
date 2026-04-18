import axios from './axiosConfig';

export interface SeguimientoSede {
    sede: string;
    asignados: number;
    contados: number;
    porcentaje: number;
    tipoConteo: string;
}

export interface SeguimientoMensualDTO {
    idUsuario: number;
    fechaBloqueExtra: string | null;
    sede: string;
    usuario: string;
    totalA: number;
    totalB: number;
    totalC: number;
    contadasA: number;
    contadasB: number;
    contadasC: number;
    noContadasA: number;
    noContadasB: number;
    noContadasC: number;
    aContadasUnaVez: number;
    aContadasDosVeces: number;
    coberturaSede: number;
}

export interface GlobalSeguimientoMensualDTO {
    reportePorSedes: SeguimientoMensualDTO[];
    consolidadoGlobal: SeguimientoMensualDTO;
}

export const getSeguimientoDiario = async (): Promise<SeguimientoSede[]> => {
    const response = await axios.get('/api/seguimiento/diario');
    return response.data;
};

export const getSeguimientoMensual = async (): Promise<GlobalSeguimientoMensualDTO> => {
    const response = await axios.get('/api/seguimiento/mensual');
    return response.data;
};


