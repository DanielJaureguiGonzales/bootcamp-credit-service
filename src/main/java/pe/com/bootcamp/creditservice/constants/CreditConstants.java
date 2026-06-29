package pe.com.bootcamp.creditservice.constants;

import java.math.BigDecimal;

public class CreditConstants {

    private CreditConstants() {
    }

    public static final String DOCUMENT_TYPE_PERSONAL = "01";
    public static final String DOCUMENT_TYPE_BUSINESS = "02";

    public static final String CARD_TYPE_PERSONAL = "PERSONAL";
    public static final String CARD_TYPE_BUSINESS = "BUSINESS";

    public static final String STATUS_COMPLETED = "COMPLETED";

    public static final BigDecimal PEN_EXCHANGE_RATE = BigDecimal.ONE;
    public static final BigDecimal USD_EXCHANGE_RATE = new BigDecimal("3.38");
    public static final BigDecimal DEFAULT_ANNUAL_INTEREST_RATE = new BigDecimal("87.76");

    public static final String MESSAGE_CONSUMPTION_COMPLETED =
            "Credit card consumption completed successfully";

    public static final String MESSAGE_PAYMENT_COMPLETED =
            "Credit payment completed successfully";
}
