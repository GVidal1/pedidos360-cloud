import React, { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { isAwsAuthConfigured } from '../awsConfig.js'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const from = location.state?.from?.pathname || '/dashboard'

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await login(username, password)
      navigate(from, { replace: true })
    } catch (err) {
      const message = err.response?.data?.message || 'No fue posible iniciar sesión'
      setError(message)
    } finally {
      setLoading(false)
    }
  }

  function fillDemo(user, pass) {
    setUsername(user)
    setPassword(pass)
  }

  return (
    <div className="login-screen">
      <div className="login-card">
        <div className="login-mark">Pedidos<span>360</span></div>
        <p className="tagline">Inicia sesión para gestionar pedidos y catálogo.</p>

        {error && <div className="error-banner">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="username">Usuario</label>
            <input
              id="username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              required
            />
          </div>
          <div className="field">
            <label htmlFor="password">Contraseña</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </div>
          <button className="btn-primary" type="submit" disabled={loading}>
            {loading ? 'Ingresando…' : 'Iniciar sesión'}
          </button>
        </form>

        <div className="demo-users">
          {isAwsAuthConfigured ? (
            <p style={{ margin: 0 }}>
              Ingresa con un usuario del User Pool de Cognito (grupos: ADMIN, OPERADOR o CLIENTE).
            </p>
          ) : (
            <>
              <div className="row">
                <span>Admin</span>
                <code onClick={() => fillDemo('admin', 'admin123')} style={{ cursor: 'pointer' }}>
                  admin / admin123
                </code>
              </div>
              <div className="row">
                <span>Operador</span>
                <code onClick={() => fillDemo('operador', 'operador123')} style={{ cursor: 'pointer' }}>
                  operador / operador123
                </code>
              </div>
              <div className="row">
                <span>Cliente</span>
                <code onClick={() => fillDemo('cliente', 'cliente123')} style={{ cursor: 'pointer' }}>
                  cliente / cliente123
                </code>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
