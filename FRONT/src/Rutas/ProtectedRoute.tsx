import type React from "react"
import { Navigate } from "react-router-dom"
import { getCurrentUser } from "../servicios/authServices"

interface ProtectedRouteProps {
  children: React.ReactNode
  allowedRoles?: number[]
}

const ProtectedRoute = ({ children, allowedRoles }: ProtectedRouteProps) => {
  const user = getCurrentUser()

  if (!user) {
    return <Navigate to="/login" />
  }

  if (allowedRoles && !allowedRoles.includes(user.roleId)) {
    // Redirección basada en rol para evitar bucles o páginas inexistentes
    if (user.roleId === 1) {
      return <Navigate to="/Inicio" />
    } else if (user.roleId === 2 || user.roleId === 3) {
      return <Navigate to="/Admin" />
    } else {
      return <Navigate to="/login" />
    }
  }

  return <>{children}</>
}

export default ProtectedRoute
