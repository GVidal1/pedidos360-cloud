import React, { useEffect, useState } from 'react'
import client from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import StatusPill from '../components/StatusPill.jsx'

const TRANSICIONES = {
  CREADO: ['ACEPTADO', 'CANCELADO'],
  ACEPTADO: ['EN_PREPARACION'],
  EN_PREPARACION: ['DESPACHADO'],
  DESPACHADO: ['ENTREGADO'],
  ENTREGADO: [],
  CANCELADO: [],
}

const LABELS = {
  ACEPTADO: 'Aceptar',
  EN_PREPARACION: 'Preparar',
  DESPACHADO: 'Despachar',
  ENTREGADO: 'Marcar entregado',
  CANCELADO: 'Cancelar',
}

export default function Orders() {
  const { hasRole } = useAuth()
  const puedeGestionar = hasRole('ADMIN', 'OPERADOR')

  const [pedidos, setPedidos] = useState([])
  const [productos, setProductos] = useState([])
  const [cart, setCart] = useState({}) // { productoId: cantidad }
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [actionError, setActionError] = useState(null)

  async function loadAll() {
    setLoading(true)
    try {
      const [ordersRes, catalogRes] = await Promise.all([
        client.get('/orders'),
        client.get('/catalog/products'),
      ])
      setPedidos(ordersRes.data)
      setProductos(catalogRes.data.filter(p => p.activo))
    } catch (err) {
      setError('No fue posible cargar los pedidos')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadAll() }, [])

  function updateCart(productoId, cantidad, stockDisponible) {
    const cantidadValida = Math.max(0, Math.min(cantidad, stockDisponible))
    setCart(prev => ({ ...prev, [productoId]: cantidadValida }))
  }

  const cartItems = Object.entries(cart)
    .map(([productoId, cantidad]) => {
      const producto = productos.find(p => String(p.id) === String(productoId))
      return producto && cantidad > 0 ? { producto, cantidad } : null
    })
    .filter(Boolean)

  const cartTotal = cartItems.reduce((sum, i) => sum + i.producto.precio * i.cantidad, 0)

  async function crearPedido() {
    setActionError(null)
    if (cartItems.length === 0) {
      setActionError('Agrega al menos un producto con cantidad mayor a 0')
      return
    }
    try {
      await client.post('/orders', {
        items: cartItems.map(i => ({ productoId: i.producto.id, cantidad: i.cantidad })),
      })
      setCart({})
      await loadAll()
    } catch (err) {
      setActionError(err.response?.data?.message || 'No fue posible crear el pedido')
    }
  }

  async function cambiarEstado(pedidoId, nuevoEstado) {
    setActionError(null)
    try {
      await client.patch(`/orders/${pedidoId}/estado`, { estado: nuevoEstado })
      await loadAll()
    } catch (err) {
      setActionError(err.response?.data?.message || 'No fue posible cambiar el estado')
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>Pedidos</h1>
        <p>
          {hasRole('CLIENTE')
            ? 'Crea un nuevo pedido y revisa el estado de los anteriores.'
            : 'Gestiona el flujo operativo de todos los pedidos.'}
        </p>
      </div>

      {actionError && <div className="error-banner">{actionError}</div>}

      {hasRole('CLIENTE') && (
        <div className="card cart-panel">
          <h3>Nuevo pedido</h3>
          <table className="data-table">
            <thead>
              <tr>
                <th>Producto</th>
                <th>Precio</th>
                <th>Stock</th>
                <th>Cantidad</th>
              </tr>
            </thead>
            <tbody>
              {productos.map(p => (
                <tr key={p.id}>
                  <td>{p.nombre}</td>
                  <td>${Number(p.precio).toLocaleString('es-CL')}</td>
                  <td>{p.stock === 0 ? <span className="out-of-stock">Sin stock</span> : p.stock}</td>
                  <td>
                    <input
                      type="number"
                      min="0"
                      max={p.stock}
                      disabled={p.stock === 0}
                      className="stock-input"
                      value={cart[p.id] || ''}
                      onChange={(e) => updateCart(p.id, Number(e.target.value), p.stock)}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {cartItems.length > 0 && (
            <div style={{ marginTop: 16 }}>
              {cartItems.map(i => (
                <div className="cart-line" key={i.producto.id}>
                  <span>{i.cantidad} × {i.producto.nombre}</span>
                  <span>${(i.producto.precio * i.cantidad).toLocaleString('es-CL')}</span>
                </div>
              ))}
              <div className="cart-total">
                <span>Total</span>
                <span>${cartTotal.toLocaleString('es-CL')}</span>
              </div>
            </div>
          )}

          <button className="btn-primary" style={{ marginTop: 16, width: 'auto', padding: '10px 20px' }} onClick={crearPedido}>
            Confirmar pedido
          </button>
        </div>
      )}

      <div className="card">
        <h3>{hasRole('CLIENTE') ? 'Tus pedidos' : 'Todos los pedidos'}</h3>
        {loading ? (
          <p className="loading-text">Cargando…</p>
        ) : error ? (
          <p className="error-banner">{error}</p>
        ) : pedidos.length === 0 ? (
          <p className="empty-state">No hay pedidos registrados todavía.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                {puedeGestionar && <th>Cliente</th>}
                <th>Ítems</th>
                <th>Total</th>
                <th>Estado</th>
                {puedeGestionar && <th>Acciones</th>}
              </tr>
            </thead>
            <tbody>
              {pedidos.map(p => (
                <tr key={p.id}>
                  <td>#{p.id}</td>
                  {puedeGestionar && <td>{p.clienteUsername}</td>}
                  <td>{p.items.map(i => `${i.cantidad}× ${i.productoNombre}`).join(', ')}</td>
                  <td>${Number(p.total).toLocaleString('es-CL')}</td>
                  <td><StatusPill estado={p.estado} /></td>
                  {puedeGestionar && (
                    <td>
                      {TRANSICIONES[p.estado].length === 0 ? (
                        <span className="loading-text">—</span>
                      ) : (
                        TRANSICIONES[p.estado].map(next => (
                          <button
                            key={next}
                            className={`action-btn${next === 'CANCELADO' ? ' danger' : ''}`}
                            onClick={() => cambiarEstado(p.id, next)}
                          >
                            {LABELS[next]}
                          </button>
                        ))
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
