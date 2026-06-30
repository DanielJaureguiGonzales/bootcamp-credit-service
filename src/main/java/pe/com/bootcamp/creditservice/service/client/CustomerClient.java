package pe.com.bootcamp.creditservice.service.client;


import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

import pe.com.bootcamp.creditservice.dto.CustomerResponse;
import reactor.core.publisher.Mono;


public interface CustomerClient {


    @GetExchange("/internal/customer/document-number")
    Mono<CustomerResponse> getCustomerResponse(
            @RequestParam String documentNumber,
            @RequestParam String documentType
    );
}
