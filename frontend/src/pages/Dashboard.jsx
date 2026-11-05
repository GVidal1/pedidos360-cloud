import React, { useEffect, useState } from 'react'
import client from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import StatusPill from '../components/StatusPill.jsx'

export default function Dashboard() {
  const { user, hasRole } = useAuth()
  const [pedidos, setPedidos] = useState([])
  const [productos, setProductos] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      try {
        const [ordersRes, catalogRes] = await Promise.all([
          client.get('/orders'),
          client.get('/catalog/products'),
        ])
        if (!cancelled) {
          setPedidos(ordersRes.data)
          setProductos(catalogRes.data)
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => { cancelled = true }
  }, [])

  const activos = pedidos.filter(p => !['ENTREGADO', 'CANCELADO'].includes(p.estado))
  const entregados = pedidos.filter(p => p.estado === 'ENTREGADO')
  const sinStock = productos.filter(p => p.stock === 0)

  return (
    <div>
      <div className="page-header">
        <h1>Hola, {user?.nombreCompleto?.split(' ')[0]}</h1>
        <p>
          {hasRole('ADMIN') && 'Vista global de la operación de Pedidos360.'}
          {hasRole('OPERADOR') && 'Pendientes del día y estado del catálogo.'}
          {hasRole('CLIENTE') && 'Seguimiento de tus pedidos recientes.'}
        </p>
      </div>

      {loading ? (
        <p className="loading-text">Cargando resumen…</p>
      ) : (
        <>
          <div className="stat-grid">
            <div className="stat-card">
              <div className="value">{pedidos.length}</div>
              <div className="label">{hasRole('CLIENTE') ? 'Tus pedidos' : 'Pedidos totales'}</div>
            </div>
            <div className="stat-card">
              <div className="value">{activos.length}</div>
              <div className="label">En curso</div>
            </div>
            <div className="stat-card">
              <div className="value">{entregados.length}</div>
              <div className="label">Entregados</div>
            </div>
            {hasRole('ADMIN', 'OPERADOR') && (
              <div className="stat-card">
                <div className="value">{sinStock.length}</div>
                <div className="label">Productos sin stock</div>
              </div>
            )}
          </div>

          <div className="card">
            <h3>{hasRole('CLIENTE') ? 'Tus últimos pedidos' : 'Últimos pedidos registrados'}</h3>
            {pedidos.length === 0 ? (
              <p className="empty-state">Aún no hay pedidos que mostrar.</p>
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    {!hasRole('CLIENTE') && <th>Cliente</th>}
                    <th>Estado</th>
                    <th>Total</th>
                  </tr>
                </thead>
                <tbody>
                  {pedidos.slice(0, 5).map((p) => (
                    <tr key={p.id}>
                      <td>#{p.id}</td>
                      {!hasRole('CLIENTE') && <td>{p.clienteUsername}</td>}
                      <td><StatusPill estado={p.estado} /></td>
                      <td>${Number(p.total).toLocaleString('es-CL')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}
    </div>
  )
}
