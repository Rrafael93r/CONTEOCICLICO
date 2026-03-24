"use client"

import type React from "react";
import {
  IconLayoutDashboard,
  IconUser,
  IconListCheck,
  IconSettings,
  IconReportAnalytics,
  IconTruck,
  IconChevronLeft,
  IconChevronRight,
  IconLogout,
} from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { getCurrentUser } from "../../servicios/authServices";
import { logout } from "../../servicios/authServices";
import logoph from "../../assets/inner.png";

const Sidebar: React.FC<{ isOpenMobile?: boolean; onCloseMobile?: () => void }> = ({ isOpenMobile, onCloseMobile }) => {
  const location = useLocation();
  const [user, setUser] = useState({ usuario: "", roleId: 0 });
  const [isCollapsed, setIsCollapsed] = useState(false);

  useEffect(() => {
    const currentUser = getCurrentUser();
    if (currentUser) {
      setUser(currentUser);
    }
  }, []);

  const getRoleName = (roleId: number) => {
    const roles: Record<number, string> = {
      1: "Farmacia",
      2: "Control de Inventario",
      3: "Administrador",
    };
    return roles[roleId] || "Desconocido";
  };

  const sections = [
    {
      heading: "INICIO",
      items: [
        {
          label: "Conteo ciclico",
          icon: IconListCheck,
          path: "/Inicio",
          rolesAllowed: [1, 3], // Farmacia y Administrador
        },
      ],
    },
    {
      heading: "ADMINISTRACIÓN",
      items: [
        {
          label: "Panel de Gestión",
          icon: IconSettings,
          path: "/Admin",
          rolesAllowed: [2, 3], // Control de Inventario y Administrador
        },
      ],
    },
  ];

  const filteredSections = sections
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => !item.rolesAllowed || item.rolesAllowed.includes(user.roleId)),
    }))
    .filter((section) => section.items.length > 0);

  return (
    <>
      {/* Overlay para móvil */}
      {isOpenMobile && (
        <div 
          className="fixed inset-0 bg-gray-900/50 backdrop-blur-sm z-40 md:hidden transition-opacity duration-300"
          onClick={onCloseMobile}
        />
      )}

      <aside className={`
        fixed inset-y-0 left-0 z-50 transform transition-transform duration-300 ease-in-out
        ${isOpenMobile ? "translate-x-0" : "-translate-x-full"}
        md:relative md:translate-x-0
        ${isCollapsed ? "md:w-20" : "md:w-72"} 
        w-72 bg-white border-r border-gray-100 flex flex-col h-screen md:sticky md:top-0 flex-shrink-0 transition-all duration-500 ease-in-out shadow-sm
      `}>
        {/* Botón Toggle Flotante (Solo Desktop) */}
        <button
          onClick={() => setIsCollapsed(!isCollapsed)}
          className="absolute -right-3 top-20 bg-white border border-gray-100 rounded-full p-1 shadow-md hover:text-orange-500 hover:border-orange-500 transition-all z-10 hidden md:block"
        >
          {isCollapsed ? <IconChevronRight size={16} /> : <IconChevronLeft size={16} />}
        </button>

        <header className={`flex items-center ${isCollapsed ? "md:justify-center" : "justify-start"} p-6 mb-2`}>
          <img src={logoph || "/placeholder.svg"} alt="Logo" className="w-10 h-10 object-contain flex-shrink-0" />
          {(!isCollapsed || isOpenMobile) && (
            <div className="ml-3 transition-opacity duration-300 overflow-hidden whitespace-nowrap">
              <h2 className="font-black text-lg tracking-tighter m-0 text-gray-900">PHARMASER</h2>
              <p className="text-[9px] font-black text-orange-500 uppercase tracking-[0.2em] leading-none mt-1">Conteo Cíclico</p>
            </div>
          )}
        </header>

        <div className="flex-1 flex flex-col overflow-hidden px-4">
          <div className="flex-1 overflow-y-auto space-y-8 custom-scrollbar py-4">
            {filteredSections.map((section, index) => (
              <div key={index} className="space-y-3">
                <h6 className={`text-gray-400 uppercase text-[10px] font-black tracking-widest px-2 transition-all duration-300 ${isCollapsed && !isOpenMobile ? "md:opacity-0 md:h-0" : "opacity-100"}`}>
                  {section.heading}
                </h6>
                <ul className="space-y-2 p-0 m-0 list-none">
                  {section.items.map((item, itemIndex) => {
                    const isActive = location.pathname === item.path;
                    return (
                      <li key={itemIndex}>
                        <Link
                          to={item.path}
                          onClick={onCloseMobile}
                          title={isCollapsed && !isOpenMobile ? item.label : ""}
                          className={`flex items-center ${isCollapsed && !isOpenMobile ? "md:justify-center" : "justify-start"} gap-3 py-3 px-3 rounded-2xl text-sm font-bold transition-all duration-300 group
                            ${isActive
                              ? "bg-gray-900 text-white shadow-xl shadow-gray-200"
                              : "text-gray-500 hover:bg-orange-50 hover:text-orange-600"}`}
                          style={{ textDecoration: "none" }}
                        >
                          <item.icon
                            size={22}
                            stroke={isActive ? 2.5 : 2}
                            className={`flex-shrink-0 transition-transform duration-300 ${!isActive && "group-hover:scale-110"}`}
                          />
                          {(!isCollapsed || isOpenMobile) && (
                            <span className="transition-opacity duration-300 whitespace-nowrap overflow-hidden">
                              {item.label}
                            </span>
                          )}
                          {isActive && (!isCollapsed || isOpenMobile) && (
                            <div className="ml-auto w-1.5 h-1.5 bg-orange-500 rounded-full"></div>
                          )}
                        </Link>
                      </li>
                    );
                  })}
                </ul>
              </div>
            ))}
          </div>
        </div>

        <div className={`p-4 transition-all duration-300 ${isCollapsed && !isOpenMobile ? "md:items-center" : "items-stretch"}`}>
          <div className={`bg-gray-50/50 rounded-3xl p-3 flex items-center shadow-sm border border-gray-100 group transition-all duration-300 ${isCollapsed && !isOpenMobile ? "md:justify-center" : "justify-between"}`}>
            <div className="flex items-center min-w-0">
              <div className="w-10 h-10 bg-white rounded-xl shadow-sm flex items-center justify-center text-orange-500 flex-shrink-0 group-hover:bg-orange-500 group-hover:text-white transition-colors">
                <IconUser size={20} />
              </div>
              {(!isCollapsed || isOpenMobile) && (
                <div className="ml-3 flex flex-col min-w-0 transition-opacity duration-300">
                  <span className="font-black text-xs text-gray-900 truncate uppercase">{user?.usuario || "Usuario"}</span>
                </div>
              )}
            </div>
            {(!isCollapsed || isOpenMobile) && (
              <Link
                to="/login"
                onClick={logout}
                className="ml-2 p-2 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-xl transition-all"
                title="Cerrar Sesión"
              >
                <IconLogout size={18} />
              </Link>
            )}
          </div>
        </div>
      </aside>
    </>
  );
};

export default Sidebar;
