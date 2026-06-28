package pe.com.bootcamp.creditservice.dto;

public record BalanceRequest(
        String documentType,
        String documentNumber
) {
}
