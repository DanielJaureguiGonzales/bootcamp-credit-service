package pe.com.bootcamp.creditservice.model;

import lombok.Getter;

@Getter
public enum CurrencyName {

    SOLES("01"),
    DOLAR("02");

    private final String code;

    CurrencyName(String code) {
        this.code = code;
    }

    public static String getNameByCode(String code) {
        for (CurrencyName currency : CurrencyName.values()) {
            if (currency.getCode().equals(code)) {
                return currency.name();
            }
        }

        throw new RuntimeException("Invalid currency code: " + code);
    }

}