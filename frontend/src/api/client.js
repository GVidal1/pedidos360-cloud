import axios from 'axios'
import { fetchAuthSession } from 'aws-amplify/auth'
import { isAwsAuthConfigured } from '../awsConfig.js'

// En local, apunta al monolito. En AWS, esta variable debe apuntar al invoke URL
// de tu API Gateway (ver VITE_API_BASE_URL en .env).
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

const client = axios.create({
  baseURL: API_BASE_URL,
})

// Equivalente a MsalInterceptor: adjunta "Authorization: Bearer <access_token>"
// a toda llamada saliente. Si Cognito está configurado, el token sale de la
// sesión de Amplify (y se refresca solo); si no, del JWT propio guardado en
// localStorage por el login local.
client.interceptors.request.use(async (config) => {
  let token = null

  if (isAwsAuthConfigured) {
    try {
      const session = await fetchAuthSession()
      token = session.tokens?.idToken?.toString() ?? null
    } catch {
      token = null
    }
  } else {
    token = localStorage.getItem('pedidos360_token')
  }

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Si el backend (o el JWT Authorizer de API Gateway) responde 401, se limpia la
// sesión local y se fuerza vuelta al login, igual que haría un Guard de rutas.
client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('pedidos360_token')
      localStorage.removeItem('pedidos360_user')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export default client
