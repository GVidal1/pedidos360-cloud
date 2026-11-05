package com.pedidos360.catalog.config;

import com.pedidos360.catalog.model.Producto;
import com.pedidos360.catalog.repository.ProductoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Siembra el catálogo inicial si la tabla está vacía (la base también arranca vacía la
 * primera vez). Los usuarios ya no se siembran aquí: la identidad vive en Cognito.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final ProductoRepository productoRepository;

    public DataSeeder(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Override
    public void run(String... args) {
        if (productoRepository.count() > 0) {
            return;
        }

        productoRepository.save(Producto.builder()
                .nombre("Teclado mecánico")
                .descripcion("Teclado mecánico switch rojo, retroiluminado")
                .precio(new BigDecimal("39990"))
                .stock(25)
                .activo(true)
                .build());

        productoRepository.save(Producto.builder()
                .nombre("Mouse inalámbrico")
                .descripcion("Mouse ergonómico inalámbrico 2.4GHz")
                .precio(new BigDecimal("14990"))
                .stock(40)
                .activo(true)
                .build());

        productoRepository.save(Producto.builder()
                .nombre("Monitor 24\" Full HD")
                .descripcion("Monitor IPS 24 pulgadas, 75Hz")
                .precio(new BigDecimal("109990"))
                .stock(10)
                .activo(true)
                .build());

        productoRepository.save(Producto.builder()
                .nombre("Audífonos con micrófono")
                .descripcion("Audífonos over-ear con micrófono desmontable")
                .precio(new BigDecimal("24990"))
                .stock(0)
                .activo(true)
                .build());
    }
}
