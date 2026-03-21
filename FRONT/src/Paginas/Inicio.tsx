import Layout from "../components/Layout/Layout"
import DetalleConteoTable from "../components/DetalleConteo/DetalleConteoTable"

const Inicio = () => {
    return (
        <Layout>
            <div className="p-6 space-y-6">
                <div className="flex flex-col">
                    <h1 className="text-3xl font-extrabold text-gray-900 tracking-tight">CONTEOCICLICO</h1>
                    <p className="mt-1 text-gray-500">Gestión y seguimiento de conteos de inventario.</p>
                </div>
                
                <DetalleConteoTable />
            </div>
        </Layout>
    )
}

export default Inicio;