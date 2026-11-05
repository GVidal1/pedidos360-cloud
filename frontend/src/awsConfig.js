/**
 * Config de Amplify Auth (Cognito). Se completa con variables de entorno de Vite
 * (archivo .env, ver .env.example) que salen de la consola de Cognito:
 *
 *   VITE_COGNITO_USER_POOL_ID   -> User Pool > "User pool ID"
 *   VITE_COGNITO_CLIENT_ID      -> App integration > App client > "Client ID"
 *   VITE_AWS_REGION             -> región donde creaste el User Pool (ej: us-east-1)
 *
 * Si estas variables no están definidas (por ejemplo, en desarrollo local sin AWS),
 * `isAwsAuthConfigured` queda en false y AuthContext usa el login propio del
 * backend (perfil "local") en vez de Cognito. Así el proyecto sigue funcionando
 * igual que antes sin tener que tocar nada más.
 */
const userPoolId = import.meta.env.VITE_COGNITO_USER_POOL_ID
const userPoolClientId = import.meta.env.VITE_COGNITO_CLIENT_ID
const region = import.meta.env.VITE_AWS_REGION

export const isAwsAuthConfigured = Boolean(userPoolId && userPoolClientId && region)

export const amplifyConfig = {
  Auth: {
    Cognito: {
      userPoolId,
      userPoolClientId,
      region,
    },
  },
}
