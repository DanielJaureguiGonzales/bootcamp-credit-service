package pe.com.bootcamp.creditservice.dto;

import java.math.BigDecimal;

public record CreditCardBalanceResponse(String creditNumber,
                                        BigDecimal creditLimit,
                                        BigDecimal usedAmount,
                                        BigDecimal availableBalance,
                                        String currencyType,
                                        String currencyName,
                                        Boolean status) {
}
