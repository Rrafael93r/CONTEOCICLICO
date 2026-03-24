import { useState } from "react";
import Sidebar from "../Sidebar/Sidebar"
import { IconMenu2 } from "@tabler/icons-react";

interface LayoutProps {
  children: React.ReactNode
}

const Layout: React.FC<LayoutProps> = ({ children }) => {
  const [isSidebarOpenMobile, setIsSidebarOpenMobile] = useState(false);

  return (
    <div className="flex flex-col md:flex-row min-h-screen w-full bg-gray-100 font-sans">
      {/* Header Móvil */}
      <header className="flex md:hidden items-center justify-between p-4 bg-white border-b border-gray-100 shadow-sm z-30">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-orange-500 text-white rounded-xl shadow-lg shadow-orange-500/20">
            <IconMenu2 
              size={20} 
              className="cursor-pointer" 
              onClick={() => setIsSidebarOpenMobile(true)} 
            />
          </div>
          <span className="font-black text-sm tracking-tighter text-gray-900 uppercase">Pharmaser</span>
        </div>
        <div className="w-8 h-8 rounded-full bg-gray-100 border border-gray-200" />
      </header>

      <Sidebar 
        isOpenMobile={isSidebarOpenMobile} 
        onCloseMobile={() => setIsSidebarOpenMobile(false)} 
      />
      
      <main className="flex-1 p-2 sm:p-6 md:p-8 overflow-x-hidden">
        {children}
      </main>
    </div>
  )
}

export default Layout
