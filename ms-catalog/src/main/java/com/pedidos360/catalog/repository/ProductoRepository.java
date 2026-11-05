package com.pedidos360.catalog.repository;

import com.pedidos360.catalog.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /**
     * Descuento atómico en una sola sentencia SQL: solo descuenta si queda stock suficiente.
     * Devuelve las filas afectadas (1 = descontado, 0 = no existe o stock insuficiente).
     * Al ser una sola sentencia, dos eventos concurrentes sobre el mismo producto no se pisan
     * (con SQS puede haber varias Lambdas consumidoras en paralelo).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Producto p SET p.stock = p.stock - :cantidad WHERE p.id = :id AND p.stock >= :cantidad")
    int descontarSiHayStock(@Param("id") Long id, @Param("cantidad") int cantidad);
}
