package com.pedidos360.orders.service;

import com.pedidos360.orders.client.CatalogClient;
import com.pedidos360.orders.client.ProductoRemoto;
import com.pedidos360.orders.dto.CrearPedidoRequest;
import com.pedidos360.orders.dto.PedidoItemRequest;
import com.pedidos360.orders.dto.PedidoResponse;
import com.pedidos360.orders.events.OutboxService;
import com.pedidos360.orders.exception.ApiException;
import com.pedidos360.orders.model.EstadoPedido;
import com.pedidos360.orders.model.Pedido;
import com.pedidos360.orders.model.PedidoItem;
import com.pedidos360.orders.repository.PedidoItemRepository;
import com.pedidos360.orders.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class OrderService {

    private final PedidoRepository pedidoRepository;
    private final PedidoItemRepository pedidoItemRepository;
    private final CatalogClient catalogClient;
    private final OutboxService outboxService;

    public OrderService(PedidoRepository pedidoRepository, PedidoItemRepository pedidoItemRepository,
                         CatalogClient catalogClient, OutboxService outboxService) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoItemRepository = pedidoItemRepository;
        this.catalogClient = catalogClient;
        this.outboxService = outboxService;
    }

    /** Cliente: solo ve sus propios pedidos. Admin/Operador: ven todos. */
    public List<PedidoResponse> listarPara(String username, boolean verTodos) {
        List<Pedido> pedidos = verTodos
                ? pedidoRepository.findAllByOrderByCreadoEnDesc()
                : pedidoRepository.findByClienteUsernameOrderByCreadoEnDesc(username);
        return pedidos.stream().map(PedidoResponse::from).toList();
    }

    public PedidoResponse obtenerPara(Long id, String username, boolean verTodos) {
        Pedido pedido = obtener(id);
        if (!verTodos && !pedido.getClienteUsername().equals(username)) {
            throw ApiException.forbidden("No puede ver pedidos de otro cliente");
        }
        return PedidoResponse.from(pedido);
    }

    @Transactional
    public PedidoResponse crear(String clienteUsername, CrearPedidoRequest request) {
        Instant ahora = Instant.now();
        Pedido pedido = Pedido.builder()
                .clienteUsername(clienteUsername)
                .estado(EstadoPedido.CREADO)
                .total(BigDecimal.ZERO)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (PedidoItemRequest itemReq : request.items()) {
            // El producto vive en ms-catalog: se consulta su precio, estado y stock actual.
            // Si no existe, el catálogo responde 404 y se propaga igual que antes.
            ProductoRemoto producto = catalogClient.obtenerProducto(itemReq.productoId());

            if (Boolean.FALSE.equals(producto.activo())) {
                throw ApiException.badRequest("El producto '" + producto.nombre() + "' no está disponible");
            }

            // El stock del producto todavía no se descuenta al crear el pedido (eso pasa recién
            // al ACEPTAR, y ahora de forma asíncrona), así que hay que restar aparte lo que ya está
            // comprometido en otros pedidos pendientes (CREADO) o aceptados sin confirmación de
            // stock, o varios pedidos podrían "pedir" el mismo stock físico.
            int reservadoEnPendientes = pedidoItemRepository.sumarCantidadReservada(producto.id(), EstadoPedido.CREADO);
            int disponibleReal = producto.stock() - reservadoEnPendientes;
            if (itemReq.cantidad() > disponibleReal) {
                throw ApiException.badRequest("Stock insuficiente para '" + producto.nombre() + "': disponible "
                        + disponibleReal + " (" + producto.stock() + " en catálogo, " + reservadoEnPendientes
                        + " ya reservado en pedidos pendientes), solicitado " + itemReq.cantidad());
            }

            BigDecimal subtotal = producto.precio().multiply(BigDecimal.valueOf(itemReq.cantidad()));
            total = total.add(subtotal);

            pedido.agregarItem(PedidoItem.builder()
                    .productoId(producto.id())
                    .productoNombre(producto.nombre())
                    .cantidad(itemReq.cantidad())
                    .precioUnitario(producto.precio())
                    .build());
        }

        pedido.setTotal(total);
        return PedidoResponse.from(pedidoRepository.save(pedido));
    }

    /**
     * Cambia el estado del pedido validando la máquina de estados definida en la guía:
     * CREADO -> ACEPTADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO (CANCELADO solo desde CREADO).
     * Regla clave: "no se puede DESPACHAR sin ACEPTAR" (no se permiten saltos).
     * Regla de stock: "el stock disminuye al aceptar el pedido". Ya no es una llamada HTTP: al aceptar se
     * guarda el evento PedidoAceptado (outbox) en esta misma transacción y, tras el commit, se publica en RabbitMQ (y de ahí viaja por SNS/SQS);
     * ms-catalog descuenta por SQS y responde con StockDescontado / StockRechazado (ver StockResultProcessor).
     */
    @Transactional
    public PedidoResponse cambiarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = obtener(id);
        EstadoPedido actual = pedido.getEstado();

        if (actual.esFinal()) {
            throw ApiException.conflict("El pedido ya está en un estado final (" + actual + ") y no puede modificarse");
        }

        if (!actual.puedeTransicionarA(nuevoEstado)) {
            throw ApiException.conflict("Transición inválida: no se puede pasar de " + actual + " a " + nuevoEstado);
        }

        if (nuevoEstado == EstadoPedido.ACEPTADO) {
            pedido.setStockConfirmado(false);            // hasta que ms-catalog confirme el descuento
            outboxService.registrarPedidoAceptado(pedido); // se publica en RabbitMQ cuando esta transacción confirme
        }

        pedido.setEstado(nuevoEstado);
        pedido.setActualizadoEn(Instant.now());
        return PedidoResponse.from(pedidoRepository.save(pedido));
    }

    /**
     * Reconciliación (solo Admin): vuelve a emitir PedidoAceptado de los pedidos aceptados cuyo stock aún no
     * fue confirmado (por ejemplo, si el evento se publicó antes de existir la suscripción o la cola).
     * Es seguro repetirla: el eventId es fijo por pedido y ms-catalog no descuenta dos veces.
     * Úsala solo para pedidos cuyo stock NO se descontó en el catálogo.
     */
    @Transactional
    public int reencolarStockPendiente() {
        List<Pedido> pendientes = pedidoRepository.findByStockConfirmadoFalse();
        for (Pedido pedido : pendientes) {
            outboxService.encolarPedidoAceptado(pedido);
        }
        if (!pendientes.isEmpty()) {
            outboxService.publicarTrasCommit();
        }
        return pendientes.size();
    }

    private Pedido obtener(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Pedido no encontrado: " + id));
    }
}
