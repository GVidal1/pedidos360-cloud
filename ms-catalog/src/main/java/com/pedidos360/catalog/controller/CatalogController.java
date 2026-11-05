package com.pedidos360.catalog.controller;

import com.pedidos360.catalog.dto.ProductoDTO;
import com.pedidos360.catalog.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** Ver catálogo: todos los roles autenticados (Admin, Operador, Cliente). */
    @GetMapping("/products")
    public ResponseEntity<List<ProductoDTO>> listar() {
        return ResponseEntity.ok(catalogService.listar());
    }

    /** Consultar un producto: lo usa ms-orders (con el token del usuario) al crear un pedido. */
    @GetMapping("/products/{id}")
    public ResponseEntity<ProductoDTO> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.obtenerPorId(id));
    }

    /** Crear/editar producto completo: solo Admin. */
    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductoDTO> crear(@Valid @RequestBody ProductoDTO dto) {
        return ResponseEntity.ok(catalogService.crear(dto));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductoDTO> actualizar(@PathVariable Long id, @Valid @RequestBody ProductoDTO dto) {
        return ResponseEntity.ok(catalogService.actualizar(id, dto));
    }

    /** Administrar stock: Admin y Operador (operación diaria de bodega). */
    @PatchMapping("/products/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<ProductoDTO> actualizarStock(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer nuevoStock = body.get("stock");
        return ResponseEntity.ok(catalogService.actualizarStock(id, nuevoStock));
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Boolean>> eliminar(@PathVariable Long id) {
        catalogService.eliminar(id);
        return ResponseEntity.ok(Map.of("eliminado", true));
    }
}
