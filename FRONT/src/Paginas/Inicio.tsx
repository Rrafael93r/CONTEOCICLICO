import Layout from "../components/Layout/Layout"
import DetalleConteoTable from "../components/DetalleConteo/DetalleConteoTable"

const Inicio = () => {
    return (
        <Layout>
            <div className="p-2 md:p-6">
                <DetalleConteoTable />
            </div>
        </Layout>
    )
}

export default Inicio;