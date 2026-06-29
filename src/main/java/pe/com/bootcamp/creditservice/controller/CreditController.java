package pe.com.bootcamp.creditservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.com.bootcamp.creditservice.dto.*;
import pe.com.bootcamp.creditservice.service.CreditService;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("api/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    @PostMapping
    public Mono<ResponseEntity<CreditResponse>> createCredit(@Valid @RequestBody CreditRequest creditRequest) {

        return creditService.createCreditRequest(creditRequest)
                .map(creditResponse -> ResponseEntity.status(HttpStatus.CREATED).body(creditResponse));

    }

    @GetMapping("/document")
    public Mono<ResponseEntity<List<CreditResponse>>> findAllCreditsByCustomerId(@RequestParam("document-number") String documentNumber,@RequestParam("document-type") String documentType){
        return creditService.getCreditsByCustomer(documentNumber,documentType)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @PostMapping("/consumption")
    public Mono<ResponseEntity<CreditConsumptionResponse>> registerConsumption(@Valid @RequestBody
                                                                                   CreditConsumptionRequest
                                                                                           consumptionRequest) {

        return creditService.registerConsumption(consumptionRequest)
                .map(creditConsumptionResponse -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(creditConsumptionResponse));

    }

    @PostMapping("/payment")
    public Mono<ResponseEntity<CreditPaymentResponse>> payCredit(@Valid @RequestBody
                                                                     CreditPaymentRequest creditPaymentRequest) {

        return creditService.payCredit(creditPaymentRequest)
                .map(creditConsumptionResponse -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(creditConsumptionResponse));

    }

    @GetMapping("/balances")
    public Mono<ResponseEntity<CreditBalancesResponse>> getCreditBalances(
            @RequestBody BalanceRequest request
    ) {
        return creditService.getCreditBalances(request)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/movements")
    public Mono<ResponseEntity<CreditMovementsResponse>> getCreditMovements(
            @RequestBody @Valid CreditMovementsRequest request
    ) {
        return creditService.getCreditMovements(request)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping
    public Mono<ResponseEntity<Void>> deleteCredit(
            @RequestBody @Valid CreditDeleteRequest request
    ) {
        return creditService.deleteCredit(request)
                .then(Mono.fromSupplier(() -> ResponseEntity.noContent().build()));
    }

}
