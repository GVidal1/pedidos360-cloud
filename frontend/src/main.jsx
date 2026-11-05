import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { Amplify } from 'aws-amplify'
import App from './App.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import { amplifyConfig, isAwsAuthConfigured } from './awsConfig.js'
import './index.css'

// Si no hay variables VITE_COGNITO_* definidas, se omite a propósito: el
// AuthContext detecta esto (isAwsAuthConfigured) y usa el login local del backend.
if (isAwsAuthConfigured) {
  Amplify.configure(amplifyConfig)
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
