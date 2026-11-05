import React from 'react'
import Sidebar from './Sidebar.jsx'

export default function AppLayout({ children }) {
  return (
    <div className="app-shell">
      <Sidebar />
      <main className="main-content">{children}</main>
    </div>
  )
}
