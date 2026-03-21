import Layout from "../components/Layout/Layout"
import PersonalizadoTable from "../components/Personalizado/PersonalizadoTable"

const ConteoPersonalizadoPage = () => {
    return (
        <Layout>
            <div className="p-2 md:p-6">
                <PersonalizadoTable />
            </div>
        </Layout>
    )
}

export default ConteoPersonalizadoPage;
