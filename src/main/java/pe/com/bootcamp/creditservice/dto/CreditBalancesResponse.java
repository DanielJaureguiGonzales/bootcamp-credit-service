package pe.com.bootcamp.creditservice.dto;

import java.util.List;

public record CreditBalancesResponse(String customerId,
                                     String documentType,
                                     String documentNumber,
                                     List<CreditCardBalanceResponse> creditCards) {
}
