import React from 'react'

const LABELS = {
  CREADO: 'Creado',
  ACEPTADO: 'Aceptado',
  EN_PREPARACION: 'En preparación',
  DESPACHADO: 'Despachado',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
}

export default function StatusPill({ estado }) {
  return <span className={`pill pill-${estado}`}>{LABELS[estado] ?? estado}</span>
}
