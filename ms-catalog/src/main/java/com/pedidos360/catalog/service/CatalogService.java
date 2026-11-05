package com.pedidos360.catalog.service;

import com.pedidos360.catalog.dto.ProductoDTO;
import com.pedidos360.catalog.events.PedidoAceptadoEvent;
import com.pedidos360.catalog.exception.ApiException;
import com.pedidos360.catalog.model.Producto;
import com.pedidos360.catalog.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogService {

    private final ProductoRepository productoRepository;

    public CatalogService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    public List<ProductoDTO> listar() {
        return productoRepository.findAll().stream().map(this::toDto).toList();
    }

    public ProductoDTO obtenerPorId(Long id) {
        return toDto(obtener(id));
    }

    public ProductoDTO crear(ProductoDTO dto) {
        Producto producto = Producto.builder()
                .nombre(dto.nombre())
                .descripcion(dto.descripcion())
                .precio(dto.precio())
                .stock(dto.stock())
                .activo(dto.activo() == null || dto.activo())
                .build();
        return toDto(productoRepository.save(producto));
    }

    public ProductoDTO actualizar(Long id, ProductoDTO dto) {
        Producto producto = obtener(id);
        producto.setNombre(dto.nombre());
        producto.setDescripcion(dto.descripcion());
        producto.setPrecio(dto.precio());
        producto.setStock(dto.stock());
        if (dto.activo() != null) {
            producto.setActivo(dto.activo());
        }
        return toDto(productoRepository.save(producto));
    }

    /** Actualización parcial de stock, pensada para el rol Operador. */
    public ProductoDTO actualizarStock(Long id, Integer nuevoStock) {
        if (nuevoStock < 0) {
            throw ApiException.badRequest("El stock no puede ser negativo");
        }
        Producto producto = obtener(id);
        producto.setStock(nuevoStock);
        return toDto(productoRepository.save(producto));
    }

    /**
     * Regla de negocio "el stock disminuye al aceptar el pedido". Ya no la invoca ms-orders por HTTP:
     * la ejecuta el consumidor del evento PedidoAceptado (StockEventProcessor). Es atómico: si algún
     * producto no tiene stock suficiente se lanza la excepción y la transacción revierte los
     * descuentos anteriores del mismo pedido. Cada descuento es una sola sentencia SQL condicionada
     * al stock, así que consumidores concurrentes no se pisan.
     */
    @Transactional
    public void descontarStock(List<PedidoAceptadoEvent.Item> items) {
        for (PedidoAceptadoEvent.Item item : items) {
            int filas = productoRepository.descontarSiHayStock(item.productoId(), item.cantidad());
            if (filas == 0) {
                // O no existe (404) o no alcanza el stock (409): se consulta para dar el mensaje correcto.
                Producto producto = obtener(item.productoId());
                throw ApiException.conflict("Stock insuficiente para '" + producto.getNombre() + "': disponible "
                        + producto.getStock() + ", solicitado " + item.cantidad());
            }
        }
    }

    public void eliminar(Long id) {
        if (!productoRepository.existsById(id)) {
            throw ApiException.notFound("Producto no encontrado: " + id);
        }
        productoRepository.deleteById(id);
    }

    Producto obtener(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Producto no encontrado: " + id));
    }

    private ProductoDTO toDto(Producto producto) {
        return new ProductoDTO(producto.getId(), producto.getNombre(), producto.getDescripcion(),
                producto.getPrecio(), producto.getStock(), producto.getActivo());
    }
}
