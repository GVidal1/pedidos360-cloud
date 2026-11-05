import React, { createContext, useContext, useState, useCallback, useEffect } from 'react'
import { signIn, signOut, fetchAuthSession, getCurrentUser } from 'aws-amplify/auth'
import client from '../api/client.js'
import { isAwsAuthConfigured } from '../awsConfig.js'

const AuthContext = createContext(null)

/** Lee el rol (grupo de Cognito) y el nombre desde los claims del access/id token. */
function sesionDesdeAmplify(session, cognitoUser) {
  const accessClaims = session.tokens?.accessToken?.payload ?? {}
  const idClaims = session.tokens?.idToken?.payload ?? {}
  const grupos = accessClaims['cognito:groups'] || []

  return {
    username: accessClaims['username'] || cognitoUser?.username,
    nombreCompleto: idClaims['name'] || cognitoUser?.username,
    rol: grupos[0] || null, // se asume un rol principal por usuario, como en el resto del proyecto
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    if (isAwsAuthConfigured) return null // se resuelve async en el efecto de abajo
    const raw = localStorage.getItem('pedidos360_user')
    return raw ? JSON.parse(raw) : null
  })
  const [loading, setLoading] = useState(isAwsAuthConfigured)

  // Al montar, si Cognito está configurado, revisa si ya hay una sesión activa
  // (por ejemplo, tras refrescar la página) — Amplify guarda el token solo.
  useEffect(() => {
    if (!isAwsAuthConfigured) return
    let cancelled = false

    async function restaurarSesion() {
      try {
        const cognitoUser = await getCurrentUser()
        const session = await fetchAuthSession()
        if (!cancelled) setUser(sesionDesdeAmplify(session, cognitoUser))
      } catch {
        if (!cancelled) setUser(null)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    restaurarSesion()
    return () => { cancelled = true }
  }, [])

  const login = useCallback(async (username, password) => {
    if (isAwsAuthConfigured) {
      await signIn({ username, password })
      const cognitoUser = await getCurrentUser()
      const session = await fetchAuthSession()
      const sessionUser = sesionDesdeAmplify(session, cognitoUser)
      setUser(sessionUser)
      return sessionUser
    }

    // Modo local (sin AWS configurado): login propio del backend, igual que antes.
    const { data } = await client.post('/auth/login', { username, password })
    const sessionUser = {
      username: data.username,
      nombreCompleto: data.nombreCompleto,
      rol: data.rol,
    }
    localStorage.setItem('pedidos360_token', data.accessToken)
    localStorage.setItem('pedidos360_user', JSON.stringify(sessionUser))
    setUser(sessionUser)
    return sessionUser
  }, [])

  const logout = useCallback(async () => {
    if (isAwsAuthConfigured) {
      await signOut()
    } else {
      localStorage.removeItem('pedidos360_token')
      localStorage.removeItem('pedidos360_user')
    }
    setUser(null)
  }, [])

  const hasRole = useCallback((...roles) => {
    return !!user && roles.includes(user.rol)
  }, [user])

  return (
    <AuthContext.Provider value={{ user, login, logout, hasRole, isAuthenticated: !!user, loading }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth debe usarse dentro de AuthProvider')
  return ctx
}
