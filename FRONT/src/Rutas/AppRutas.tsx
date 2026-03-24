import { Route, Routes, Navigate } from "react-router-dom"
import Login from "../components/Login/Login"
import ProtectedRoute from "../Rutas/ProtectedRoute"
import Inicio from "../Paginas/Inicio"
import AdminPage from "../Paginas/AdminPage"

export const AppRutas = () => {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route path="/Inicio"
        element={
          <ProtectedRoute allowedRoles={[1, 3]}>
            < Inicio />
          </ProtectedRoute>
        }
      />

      <Route path="/Admin"
        element={
          <ProtectedRoute allowedRoles={[2, 3]}>
            < AdminPage />
          </ProtectedRoute>
        }
      />

      {/* Redirección por defecto basada en rol */}
      <Route path="/*" element={<Navigate to="/login" />} />
    </Routes>
  )
}
