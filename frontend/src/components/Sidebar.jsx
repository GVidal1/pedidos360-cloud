import React from 'react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

const ROLE_LABEL = {
  ADMIN: 'admin',
  OPERADOR: 'operador',
  CLIENTE: 'cliente',
}

export default function Sidebar() {
  const { user, logout, hasRole } = useAuth()

  return (
    <aside className="sidebar">
      <div>
        <div className="sidebar-brand">Pedidos<span>360</span></div>
        <div className="sidebar-subtitle">Panel de operación</div>
      </div>

      <nav className="sidebar-nav">
        <NavLink to="/dashboard" className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}>
          Resumen
        </NavLink>
        <NavLink to="/orders" className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}>
          Pedidos
        </NavLink>
        {hasRole('ADMIN', 'OPERADOR') && (
          <NavLink to="/catalog" className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}>
            Catálogo
          </NavLink>
        )}
      </nav>

      <div className="sidebar-user">
        <div className="name">{user?.nombreCompleto}</div>
        <span className="role">{ROLE_LABEL[user?.rol] ?? user?.rol}</span>
        <button className="logout-btn" onClick={logout}>Cerrar sesión</button>
      </div>
    </aside>
  )
}
