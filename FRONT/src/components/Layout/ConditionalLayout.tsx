"use client"

import type React from "react"
import { getCurrentUser } from "../../servicios/authServices"
import Layout from "./Layout"
import SimpleLayout from "./SimpleLayout"

interface ConditionalLayoutProps {
  children: React.ReactNode
}

const ConditionalLayout: React.FC<ConditionalLayoutProps> = ({ children }) => {
  const user = getCurrentUser()


  if (!user) {
    return <Layout>{children}</Layout>
  }


  if (user.roleId === 3) {
    return <SimpleLayout>{children}</SimpleLayout>
  }


  return <Layout>{children}</Layout>
}

export default ConditionalLayout
