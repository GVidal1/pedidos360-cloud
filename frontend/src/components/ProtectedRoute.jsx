import React from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

/**
 * Equivalente al "Guard" de Angular de la guía original: evita el acceso a
 * rutas que requieren autenticación, y opcionalmente exige un rol específico.
 */
export default function ProtectedRoute({ children, roles }) {
  const { isAuthenticated, hasRole, loading } = useAuth()
  const location = useLocation()

  // Con Cognito, restaurar la sesión al refrescar la página es asíncrono
  // (fetchAuthSession); mientras tanto no se debe redirigir a /login todavía.
  if (loading) {
    return null
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (roles && roles.length > 0 && !hasRole(...roles)) {
    return <Navigate to="/dashboard" replace />
  }

  return children
}
