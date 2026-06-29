package pe.com.bootcamp.creditservice.dto;

import jakarta.validation.constraints.NotBlank;

public record CreditDeleteRequest(

        String documentType,


        String documentNumber,

        @NotBlank(message = "Credit number is required")
        String creditNumber

) {
}
