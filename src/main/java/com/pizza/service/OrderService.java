package com.pizza.service;

import com.pizza.dto.*;
import com.pizza.exception.*;
import com.pizza.model.*;
import com.pizza.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final PizzaOrderRepository orderRepository;
    private final PizzaSizeRepository sizeRepository;
    private final ToppingRepository toppingRepository;
    private final PricingService pricingService;

    public OrderService(PizzaOrderRepository orderRepository,
                        PizzaSizeRepository sizeRepository,
                        ToppingRepository toppingRepository,
                        PricingService pricingService) {
        this.orderRepository = orderRepository;
        this.sizeRepository = sizeRepository;
        this.toppingRepository = toppingRepository;
        this.pricingService = pricingService;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // Idempotency: return existing order if key matches
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<PizzaOrder> existing = orderRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return toOrderResponse(existing.get());
            }
        }

        PizzaOrder order = new PizzaOrder(request.idempotencyKey());
        order.setPromoCode(request.promoCode());
        order.setTipMode(request.tipMode());
        order.setTipValue(request.tipValue());

        for (LineItemRequest itemReq : request.lineItems()) {
            order.addLineItem(buildLineItem(itemReq));
        }

        validatePromoIfPresent(request.promoCode(), order.getLineItems());

        order = orderRepository.save(order);
        return toOrderResponse(order);
    }

    @Transactional
    public OrderResponse updateOrder(Long orderId, UpdateOrderRequest request) {
        PizzaOrder order = findOrThrow(orderId);
        assertModifiable(order);

        // Clear and rebuild
        order.getLineItems().clear();
        orderRepository.flush(); // flush orphan removals before adding new items

        for (LineItemRequest itemReq : request.lineItems()) {
            order.addLineItem(buildLineItem(itemReq));
        }

        order.setPromoCode(request.promoCode());
        order.setTipMode(request.tipMode());
        order.setTipValue(request.tipValue());

        validatePromoIfPresent(request.promoCode(), order.getLineItems());

        order = orderRepository.save(order);
        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        return toOrderResponse(findOrThrow(orderId));
    }

    @Transactional
    public void cancelOrder(Long orderId) {
        PizzaOrder order = findOrThrow(orderId);
        assertModifiable(order);
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public OrderStatusResponse getOrderStatus(Long orderId) {
        PizzaOrder order = findOrThrow(orderId);
        return new OrderStatusResponse(order.getId(), order.getStatus().name());
    }

    @Transactional
    public OrderStatusResponse advanceOrderStatus(Long orderId) {
        PizzaOrder order = findOrThrow(orderId);
        try {
            order.setStatus(order.getStatus().next());
            order = orderRepository.save(order);
            return new OrderStatusResponse(order.getId(), order.getStatus().name());
        } catch (IllegalStateException e) {
            throw new OrderNotModifiableException(e.getMessage());
        }
    }

    /** Stateless pricing calculation — does not persist anything. */
    public PricingResponse calculatePricing(CreateOrderRequest request) {
        List<LineItem> items = request.lineItems().stream()
                .map(this::buildLineItem)
                .collect(Collectors.toList());
        PricingResult result = pricingService.calculatePricing(
                items, request.promoCode(), request.tipMode(), request.tipValue());
        return toPricingResponse(result);
    }

    // ---- helpers ----

    private LineItem buildLineItem(LineItemRequest req) {
        PizzaSize size = sizeRepository.findById(req.sizeId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid size ID: " + req.sizeId()));
        LineItem item = new LineItem(size, req.quantity());
        if (req.toppingIds() != null && !req.toppingIds().isEmpty()) {
            Set<Topping> toppings = toppingRepository.findByIdIn(req.toppingIds());
            if (toppings.size() != req.toppingIds().size()) {
                throw new IllegalArgumentException("One or more topping IDs are invalid");
            }
            toppings.forEach(item::addTopping);
        }
        return item;
    }

    private void validatePromoIfPresent(String promoCode, List<LineItem> items) {
        if (promoCode == null || promoCode.isBlank()) return;
        PricingResult tempPricing = pricingService.calculatePricing(items, null, null, null);
        pricingService.validatePromoCode(promoCode, tempPricing.subtotal())
                .ifPresent(msg -> { throw new InvalidPromoCodeException(msg); });
    }

    private PizzaOrder findOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));
    }

    private void assertModifiable(PizzaOrder order) {
        if (!order.getStatus().isModifiable()) {
            throw new OrderNotModifiableException(
                    "Order " + order.getId() + " cannot be modified (status: " + order.getStatus() + ")");
        }
    }

    private OrderResponse toOrderResponse(PizzaOrder order) {
        PricingResult pricing = pricingService.calculatePricing(
                order.getLineItems(), order.getPromoCode(),
                order.getTipMode(), order.getTipValue());

        List<LineItemResponse> itemResponses = new ArrayList<>();
        for (int i = 0; i < order.getLineItems().size(); i++) {
            LineItem item = order.getLineItems().get(i);
            PricingResult.LineItemPricing lp = pricing.lineItems().get(i);

            itemResponses.add(new LineItemResponse(
                    item.getId(),
                    item.getSize().getId(),
                    item.getSize().getName(),
                    item.getQuantity(),
                    item.getToppings().stream()
                            .map(t -> new LineItemResponse.ToppingInfo(t.getId(), t.getName()))
                            .collect(Collectors.toList()),
                    lp.lineTotal()
            ));
        }

        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                itemResponses,
                toPricingResponse(pricing),
                order.getPromoCode(),
                order.getTipMode() != null ? order.getTipMode().name() : null,
                order.getTipValue(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private PricingResponse toPricingResponse(PricingResult r) {
        return new PricingResponse(
                r.subtotal(), r.discount(), r.appliedPromoCode(),
                r.taxableAmount(), r.tax(), r.tip(), r.total());
    }
}
