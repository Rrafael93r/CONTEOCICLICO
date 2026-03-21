import { Route, Routes, Navigate } from "react-router-dom"
import Login from "../components/Login/Login"
import ProtectedRoute from "../Rutas/ProtectedRoute"
import Inicio from "../Paginas/Inicio"

export const AppRutas = () => {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route path="/inicio"
        element={
          <ProtectedRoute allowedRoles={[1, 2, 3]}>
            < Inicio />
          </ProtectedRoute>
        }
      />

      {/* Redirección por defecto basada en rol */}
      <Route path="/*" element={<Navigate to="/login" />} />
    </Routes>
  )
}
