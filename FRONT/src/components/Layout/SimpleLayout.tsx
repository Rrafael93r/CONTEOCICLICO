"use client"

import type React from "react"
import { useState } from "react"
import { Link } from "react-router-dom"
import { getCurrentUser, logout } from "../../servicios/authServices"
import CoralLogo from "../../assets/icons8-controlar-100.png"

interface SimpleLayoutProps {
  children: React.ReactNode
}

const SimpleLayout: React.FC<SimpleLayoutProps> = ({ children }) => {
  const [user] = useState(getCurrentUser())

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col font-sans">
      {/* Header simple para móvil */}
      <header className="bg-white shadow-sm border-b border-gray-200">
        <div className="w-full px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center py-3">
            {/* Logo y título */}
            <div className="flex items-center">
              <img
                src={CoralLogo || "/placeholder.svg"}
                alt="CONTEOCICLICO Logo"
                className="w-10 h-10 object-contain"
              />
              <div className="ml-3">
                <h5 className="m-0 font-bold text-lg text-gray-800">
                  CONTEOCICLICO
                </h5>
                <span className="text-sm text-gray-500">Sistema de Reportes</span>
              </div>
            </div>

            {/* Información del usuario y logout */}
            <div className="flex items-center">
              <div className="mr-4 text-right hidden sm:block">
                <div className="font-semibold text-sm text-gray-800">{user?.username}</div>
                <div className="text-xs text-gray-500">
                  Reportador
                </div>
              </div>
              <div className="flex items-center space-x-2">
                <svg
                  className="mr-2 text-orange-500"
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  width="24"
                  height="24"
                  strokeWidth="2"
                >
                  <path d="M8 7a4 4 0 1 0 8 0a4 4 0 0 0 -8 0"></path>
                  <path d="M6 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2"></path>
                </svg>
                <Link to="/login" onClick={logout} className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg border border-orange-500 text-orange-500 hover:bg-orange-50 transition-colors bg-white font-medium text-sm no-underline">
                  <i className="bi bi-box-arrow-right"></i>
                  <span className="hidden sm:inline">Salir</span>
                </Link>
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* Contenido principal */}
      <main className="w-full flex-1 px-4 sm:px-6 lg:px-8 py-6">{children}</main>

      {/* Footer simple */}
      <footer className="bg-white border-t border-gray-200 mt-auto">
        <div className="w-full px-4 sm:px-6 lg:px-8">
          <div className="text-center py-4">
            <p className="text-sm text-gray-500 m-0 leading-relaxed">
              © 2026 CONTEOCICLICO - Sistema de Gestión de Inventarios
              <br />
              <span className="inline-block mt-1">
                <i className="bi bi-shield-check mr-1 text-orange-500"></i>
                Acceso Reportador
              </span>
            </p>
          </div>
        </div>
      </footer>
    </div>
  )
}

export default SimpleLayout
