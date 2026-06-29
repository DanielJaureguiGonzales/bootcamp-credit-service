package pe.com.bootcamp.creditservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreditMovementResponse(
        String movementId,
        String creditNumber,
        String documentNumber,
        String movementType,
        BigDecimal amount,
        String merchantName,
        String paymentMethod,
        String description,
        LocalDateTime movementDate,
        Boolean status
) {
}
