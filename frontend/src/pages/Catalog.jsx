import React, { useEffect, useState } from 'react'
import client from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'

const PRODUCTO_VACIO = { nombre: '', descripcion: '', precio: '', stock: '' }

export default function Catalog() {
  const { hasRole } = useAuth()
  const esAdmin = hasRole('ADMIN')

  const [productos, setProductos] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [nuevo, setNuevo] = useState(PRODUCTO_VACIO)
  const [stockDraft, setStockDraft] = useState({})

  async function load() {
    setLoading(true)
    try {
      const { data } = await client.get('/catalog/products')
      setProductos(data)
    } catch (err) {
      setError('No fue posible cargar el catálogo')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  async function crearProducto(e) {
    e.preventDefault()
    setError(null)
    try {
      await client.post('/catalog/products', {
        nombre: nuevo.nombre,
        descripcion: nuevo.descripcion,
        precio: Number(nuevo.precio),
        stock: Number(nuevo.stock),
        activo: true,
      })
      setNuevo(PRODUCTO_VACIO)
      await load()
    } catch (err) {
      setError(err.response?.data?.message || 'No fue posible crear el producto')
    }
  }

  async function guardarStock(producto) {
    setError(null)
    const nuevoStock = stockDraft[producto.id]
    if (nuevoStock === undefined || nuevoStock === '') return
    try {
      await client.patch(`/catalog/products/${producto.id}/stock`, { stock: Number(nuevoStock) })
      setStockDraft(prev => { const copy = { ...prev }; delete copy[producto.id]; return copy })
      await load()
    } catch (err) {
      setError(err.response?.data?.message || 'No fue posible actualizar el stock')
    }
  }

  async function eliminarProducto(id) {
    setError(null)
    try {
      await client.delete(`/catalog/products/${id}`)
      await load()
    } catch (err) {
      setError(err.response?.data?.message || 'No fue posible eliminar el producto')
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>Catálogo</h1>
        <p>{esAdmin ? 'Administra productos, precios y stock.' : 'Actualiza el stock disponible de cada producto.'}</p>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {esAdmin && (
        <div className="card">
          <h3>Nuevo producto</h3>
          <form className="inline-form" onSubmit={crearProducto}>
            <div className="field">
              <label>Nombre</label>
              <input required value={nuevo.nombre} onChange={e => setNuevo({ ...nuevo, nombre: e.target.value })} />
            </div>
            <div className="field">
              <label>Descripción</label>
              <input value={nuevo.descripcion} onChange={e => setNuevo({ ...nuevo, descripcion: e.target.value })} />
            </div>
            <div className="field">
              <label>Precio</label>
              <input required type="number" min="0" value={nuevo.precio} onChange={e => setNuevo({ ...nuevo, precio: e.target.value })} />
            </div>
            <div className="field">
              <label>Stock</label>
              <input required type="number" min="0" value={nuevo.stock} onChange={e => setNuevo({ ...nuevo, stock: e.target.value })} />
            </div>
            <button className="btn-primary" style={{ width: 'auto' }} type="submit">Agregar</button>
          </form>
        </div>
      )}

      <div className="card">
        <h3>Productos</h3>
        {loading ? (
          <p className="loading-text">Cargando…</p>
        ) : productos.length === 0 ? (
          <p className="empty-state">No hay productos en el catálogo.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Descripción</th>
                <th>Precio</th>
                <th>Stock</th>
                {esAdmin && <th>Acciones</th>}
              </tr>
            </thead>
            <tbody>
              {productos.map(p => (
                <tr key={p.id}>
                  <td>{p.nombre}</td>
                  <td>{p.descripcion}</td>
                  <td>${Number(p.precio).toLocaleString('es-CL')}</td>
                  <td>
                    <div className="stock-cell">
                      <input
                        type="number"
                        min="0"
                        className="stock-input"
                        value={stockDraft[p.id] ?? p.stock}
                        onChange={e => setStockDraft(prev => ({ ...prev, [p.id]: e.target.value }))}
                      />
                      <button className="action-btn" onClick={() => guardarStock(p)}>Guardar</button>
                    </div>
                  </td>
                  {esAdmin && (
                    <td>
                      <button className="action-btn danger" onClick={() => eliminarProducto(p.id)}>Eliminar</button>
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
