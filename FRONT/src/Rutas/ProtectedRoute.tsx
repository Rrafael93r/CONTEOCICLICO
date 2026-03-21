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
    console.error("ProtectedRoute: Redirecting to /login because user is null");
    return <Navigate to="/login" />
  }

  if (allowedRoles && !allowedRoles.includes(user.roleId)) {
    console.error(`ProtectedRoute: Redirecting because roleId ${user.roleId} is not in allowedRoles [${allowedRoles.join(', ')}]`);
    // Redirección basada en rol
    if (user.roleId === 3) {
      return <Navigate to="/mi-reporte" />
    } else {
      return <Navigate to="/reportes" />
    }
  }

  return <>{children}</>
}

export default ProtectedRoute
