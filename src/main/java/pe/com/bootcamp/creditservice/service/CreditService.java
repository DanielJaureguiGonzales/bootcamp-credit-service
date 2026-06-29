package pe.com.bootcamp.creditservice.service;

import pe.com.bootcamp.creditservice.dto.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CreditService {

//    Flux<CreditResponse> getAllCreditsByCustomerId(String customerId);
    Flux<CreditResponse> getCreditsByCustomer(
            String documentNumber, String documentType
    );
    Mono<CreditResponse> createCreditRequest(CreditRequest creditRequest);
    Mono<CreditConsumptionResponse> registerConsumption(CreditConsumptionRequest consumptionRequest);
    Mono<CreditPaymentResponse> payCredit(CreditPaymentRequest creditPaymentRequest);
    Mono<CreditBalancesResponse> getCreditBalances(BalanceRequest request);
    Mono<CreditMovementsResponse> getCreditMovements(
            CreditMovementsRequest request
    );
    Mono<Void> deleteCredit(CreditDeleteRequest request);
}
