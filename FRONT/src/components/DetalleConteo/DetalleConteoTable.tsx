import React, { useEffect, useState } from 'react';
import { getAllDetalles, DetalleConteo } from '../../servicios/detalleConteoService';

const DetalleConteoTable: React.FC = () => {
    const [detalles, setDetalles] = useState<DetalleConteo[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchData = async () => {
            try {
                const data = await getAllDetalles();
                setDetalles(data);
            } catch (error) {
                console.error("Error loading data", error);
            } finally {
                setLoading(false);
            }
        };
        fetchData();
    }, []);

    if (loading) return <div className="p-4 text-center">Cargando datos...</div>;

    return (
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
            <div className="p-6 border-b border-gray-50 flex justify-between items-center">
                <h3 className="text-xl font-bold text-gray-800">Detalle de Conteo</h3>
                <span className="bg-orange-100 text-orange-600 px-3 py-1 rounded-full text-xs font-semibold">
                    {detalles.length} registros
                </span>
            </div>
            <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                    <thead>
                        <tr className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wider">
                            <th className="px-6 py-4 font-semibold">CODIGO GENERICO</th>
                            <th className="px-6 py-4 font-semibold">Usuario</th>
                            <th className="px-6 py-4 font-semibold">Cant. Contada</th>
                            <th className="px-6 py-4 font-semibold">Cant. Actual</th>
                            <th className="px-6 py-4 font-semibold">Fecha</th>
                            <th className="px-6 py-4 font-semibold">Hora</th>
                            <th className="px-6 py-4 font-semibold">Tipo</th>
                            <th className="px-6 py-4 font-semibold">ID</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-50 text-sm text-gray-700">
                        {detalles.map((d) => (
                            <tr key={d.id} className="hover:bg-orange-50/30 transition-colors">
                                <td className="px-6 py-4 font-medium text-gray-900">{d.medicamento?.codigoGenerico || d.idMedicamento}</td>
                                <td className="px-6 py-4">{d.usuario?.usuario || d.idUsuario}</td>
                                <td className="px-6 py-4 font-semibold text-orange-600">{d.cantidadContada}</td>
                                <td className="px-6 py-4">{d.cantidadActual}</td>
                                <td className="px-6 py-4">{d.fechaRegistro}</td>
                                <td className="px-6 py-4 text-gray-500">{d.horaRegistro}</td>
                                <td className="px-6 py-4">
                                    <span className="px-2 py-1 rounded-md bg-gray-100 text-gray-600 text-[10px] font-bold">
                                        {d.tipoConteo}
                                    </span>
                                </td>
                                <td className="px-6 py-4 text-xs text-gray-400">#{d.id}</td>
                            </tr>
                        ))}
                        {detalles.length === 0 && (
                            <tr>
                                <td colSpan={8} className="px-6 py-10 text-center text-gray-400">
                                    No hay registros encontrados.
                                </td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>
        </div>
    );
};

export default DetalleConteoTable;
