package com.example.payments.api;

import com.example.payments.application.AuthorizeCommand;
import com.example.payments.application.PaymentService;
import com.example.payments.domain.Money;
import com.example.payments.domain.PaymentId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                  @Valid @RequestBody CreatePaymentRequest body) {
        var money = Money.of(body.amount().toPlainString(), body.currency());
        return PaymentResponse.from(service.authorize(new AuthorizeCommand(idempotencyKey, body.merchantId(), money)));
    }

    @PostMapping("/{id}/capture")
    public PaymentResponse capture(@PathVariable String id) {
        return PaymentResponse.from(service.capture(PaymentId.of(id)));
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable String id) {
        return PaymentResponse.from(service.get(PaymentId.of(id)));
    }
}
