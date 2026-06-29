package pe.com.bootcamp.creditservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record CreditMovementsResponse(
        String documentType,
        String documentNumber,
        String creditNumber,
        String cardType,
        BigDecimal creditLimit,
        BigDecimal usedAmount,
        BigDecimal availableBalance,
        List<CreditMovementResponse> movements
) {
}
