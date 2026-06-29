package pe.com.bootcamp.creditservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.com.bootcamp.creditservice.dto.*;
import pe.com.bootcamp.creditservice.exceptions.BusinessValidationException;
import pe.com.bootcamp.creditservice.exceptions.PersonalCreditAlreadyExistsException;
import pe.com.bootcamp.creditservice.exceptions.ResourceNotFoundException;
import pe.com.bootcamp.creditservice.generator.CreditNumberGenerator;
import pe.com.bootcamp.creditservice.model.Credit;
import pe.com.bootcamp.creditservice.model.CreditConsumption;
import pe.com.bootcamp.creditservice.model.CreditPayment;
import pe.com.bootcamp.creditservice.model.CurrencyName;
import pe.com.bootcamp.creditservice.repository.CreditConsumptionRepository;
import pe.com.bootcamp.creditservice.repository.CreditPaymentRepository;
import pe.com.bootcamp.creditservice.repository.CreditRepository;
import pe.com.bootcamp.creditservice.service.CreditService;
import pe.com.bootcamp.creditservice.service.rest.CustomerResponseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static pe.com.bootcamp.creditservice.constants.CreditConstants.*;

@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private final CreditRepository creditRepository;
    private final CreditConsumptionRepository creditConsumptionRepository;
    private final CreditPaymentRepository creditPaymentRepository;
    private final CustomerResponseClient client;
    private final CreditNumberGenerator creditNumberGenerator;

    @Override
    public Flux<CreditResponse> getAllCreditsByCustomerId(String customerId) {

        if (customerId == null || customerId.isBlank()) {
            return Flux.error(new RuntimeException("Customer id is required"));
        }

        return creditRepository.findByCustomerIdAndStatus(customerId, true)
                .switchIfEmpty(Flux.error(new ResourceNotFoundException(
                        "Credit",
                        "customerId",
                        customerId
                )))
                .map(this::toCreditResponse);
    }

    @Override
    public Mono<CreditResponse> createCreditRequest(CreditRequest creditRequest) {

        return validateCreditRequest(creditRequest)
                .then(findCustomer(
                        creditRequest.documentNumber(),
                        creditRequest.documentType()
                ))
                .flatMap(customerResponse ->
                        validateCustomerCanCreateCreditCard(customerResponse)
                                .thenReturn(customerResponse)
                )
                .flatMap(customerResponse ->
                        createCreditCard(creditRequest, customerResponse)
                );
    }

    @Override
    public Mono<CreditConsumptionResponse> registerConsumption(
            CreditConsumptionRequest consumptionRequest
    ) {

        return validateConsumptionRequest(consumptionRequest)
                .then(findCustomer(
                        consumptionRequest.documentNumber(),
                        consumptionRequest.documentType()
                ))
                .flatMap(customerResponse ->
                        findActiveCreditByNumber(consumptionRequest.creditNumber())
                                .flatMap(credit ->
                                        consumeCreditCard(
                                                credit,
                                                customerResponse.id(),
                                                consumptionRequest
                                        )
                                )
                );
    }

    @Override
    public Mono<CreditPaymentResponse> payCredit(
            CreditPaymentRequest creditPaymentRequest
    ) {

        return validatePaymentRequest(creditPaymentRequest)
                .then(findCustomer(
                        creditPaymentRequest.documentNumber(),
                        creditPaymentRequest.documentType()
                ))
                .flatMap(customerResponse ->
                        findActiveCreditByNumber(creditPaymentRequest.creditNumber())
                                .flatMap(credit ->
                                        payCreditCard(
                                                credit,
                                                customerResponse.id(),
                                                creditPaymentRequest
                                        )
                                )
                );
    }

    @Override
    public Mono<CreditBalancesResponse> getCreditBalances(BalanceRequest request) {

        return validateBalanceRequest(request)
                .then(findCustomer(
                        request.documentNumber(),
                        request.documentType()
                ))
                .flatMap(customerResponse ->
                        creditRepository.findByCustomerIdAndStatus(
                                        customerResponse.id(),
                                        true
                                )
                                .map(this::toCreditCardBalanceResponse)
                                .collectList()
                                .map(creditCards -> new CreditBalancesResponse(
                                        customerResponse.id(),
                                        request.documentType(),
                                        request.documentNumber(),
                                        creditCards
                                ))
                );
    }

    @Override
    public Mono<CreditMovementsResponse> getCreditMovements(CreditMovementsRequest request) {
        return validateCreditMovementsRequest(request)
                .then(findCustomer(
                        request.documentNumber(),
                        request.documentType()
                ))
                .flatMap(customerResponse ->
                        findActiveCreditByNumber(request.creditNumber())
                                .flatMap(credit ->
                                        validateCreditBelongsToCustomer(
                                                credit,
                                                customerResponse.id()
                                        ).thenReturn(credit)
                                )
                                .flatMap(credit ->
                                        buildCreditMovementsResponse(
                                                credit,
                                                request
                                        )
                                )
                );
    }

    private Mono<CreditMovementsResponse> buildCreditMovementsResponse(
            Credit credit,
            CreditMovementsRequest request
    ) {

        BigDecimal creditLimit = getOrZero(credit.getCreditLimit());
        BigDecimal usedAmount = getOrZero(credit.getUsedAmount());
        BigDecimal availableBalance = creditLimit.subtract(usedAmount);

        Flux<CreditMovementResponse> consumptions =
                creditConsumptionRepository
                        .findByCreditIdAndStatusOrderByConsumptionDateDesc(
                                credit.getCreditId(),
                                true
                        )
                        .map(consumption -> toCreditMovementResponse(
                                consumption,
                                credit,
                                request.documentNumber()
                        ));

        Flux<CreditMovementResponse> payments =
                creditPaymentRepository
                        .findByCreditIdAndStatusOrderByPaymentDateDesc(
                                credit.getCreditId(),
                                true
                        )
                        .map(payment -> toCreditMovementResponse(
                                payment,
                                credit,
                                request.documentNumber()
                        ));

        return Flux.merge(consumptions, payments)
                .sort(Comparator.comparing(
                        CreditMovementResponse::movementDate
                ).reversed())
                .collectList()
                .map(movements -> new CreditMovementsResponse(
                        request.documentType(),
                        request.documentNumber(),
                        credit.getCreditNumber(),
                        credit.getCardType(),
                        creditLimit,
                        usedAmount,
                        availableBalance,
                        movements
                ));
    }

    private CreditMovementResponse toCreditMovementResponse(
            CreditConsumption consumption,
            Credit credit,
            String documentNumber
    ) {

        return new CreditMovementResponse(
                consumption.getConsumptionId(),
                credit.getCreditNumber(),
                documentNumber,
                "CONSUMPTION",
                consumption.getAmount(),
                consumption.getMerchantName(),
                null,
                consumption.getDescription(),
                consumption.getConsumptionDate(),
                consumption.getStatus()
        );
    }

    private CreditMovementResponse toCreditMovementResponse(
            CreditPayment payment,
            Credit credit,
            String documentNumber
    ) {

        return new CreditMovementResponse(
                payment.getPaymentId(),
                credit.getCreditNumber(),
                documentNumber,
                "PAYMENT",
                payment.getAmount(),
                null,
                payment.getPaymentMethod(),
                payment.getDescription(),
                payment.getPaymentDate(),
                payment.getStatus()
        );
    }

    private Mono<Void> validateCreditMovementsRequest(
            CreditMovementsRequest request
    ) {

        Map<String, String> errors = new HashMap<>();

        if (request == null) {
            errors.put("request", "Credit movements request is required");
            return Mono.error(new BusinessValidationException(errors));
        }

        validateDocument(
                errors,
                request.documentType(),
                request.documentNumber()
        );



        return validateErrors(errors);
    }

    private Mono<CustomerResponse> findCustomer(
            String documentNumber,
            String documentType
    ) {

        return client.getCustomerResponseByCustomer(documentNumber, documentType);
    }

    private Mono<Credit> findActiveCreditByNumber(String creditNumber) {

        return creditRepository.findByCreditNumberAndStatus(creditNumber, true)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        "Credit",
                        "creditNumber",
                        creditNumber
                )));
    }

    private Mono<CreditResponse> createCreditCard(
            CreditRequest creditRequest,
            CustomerResponse customerResponse
    ) {

        Credit credit = buildCreditCard(creditRequest, customerResponse);

        return creditRepository.save(credit)
                .map(this::toCreditResponse);
    }

    private Credit buildCreditCard(
            CreditRequest creditRequest,
            CustomerResponse customerResponse
    ) {

        BigDecimal creditLimit = creditRequest.amount();

        Credit credit = new Credit();

        credit.setCustomerId(customerResponse.id());
        credit.setCreditNumber(creditNumberGenerator.generate());


        credit.setCardType(resolveCardType(customerResponse));

        credit.setCreditLimit(creditLimit);
        credit.setUsedAmount(BigDecimal.ZERO);
        credit.setBalance(creditLimit);

        credit.setCurrencyType(normalize(creditRequest.currencyType()));
        credit.setCurrencyName(CurrencyName.getNameByCode(
                normalize(creditRequest.currencyType())
        ));

        credit.setExchangeRate(resolveExchangeRate(credit.getCurrencyName()));
        credit.setAnnualInterestRate(DEFAULT_ANNUAL_INTEREST_RATE);

        credit.setOpeningDate(LocalDateTime.now());
        credit.setStatus(true);

        return credit;
    }

    private Mono<CreditConsumptionResponse> consumeCreditCard(
            Credit credit,
            String customerId,
            CreditConsumptionRequest request
    ) {

        return validateCreditBelongsToCustomer(credit, customerId)
                .then(validateCreditLimit(credit))
                .then(Mono.defer(() -> {

                    BigDecimal previousUsedAmount = getOrZero(credit.getUsedAmount());
                    BigDecimal availableAmount = credit.getCreditLimit()
                            .subtract(previousUsedAmount);

                    if (request.amount().compareTo(availableAmount) > 0) {
                        return Mono.error(new RuntimeException(
                                "Insufficient credit limit"
                        ));
                    }

                    BigDecimal newUsedAmount = previousUsedAmount.add(request.amount());
                    BigDecimal newBalance = credit.getCreditLimit().subtract(newUsedAmount);

                    credit.setUsedAmount(newUsedAmount);
                    credit.setBalance(newBalance);

                    CreditConsumption consumption = buildCreditConsumption(
                            credit,
                            customerId,
                            request
                    );

                    return creditRepository.save(credit)
                            .then(creditConsumptionRepository.save(consumption))
                            .thenReturn(new CreditConsumptionResponse(
                                    credit.getCreditNumber(),
                                    request.amount(),
                                    credit.getCreditLimit(),
                                    newUsedAmount,
                                    newBalance,
                                    STATUS_COMPLETED,
                                    MESSAGE_CONSUMPTION_COMPLETED
                            ));
                }));
    }

    private Mono<CreditPaymentResponse> payCreditCard(
            Credit credit,
            String customerId,
            CreditPaymentRequest request
    ) {

        return validateCreditBelongsToCustomer(credit, customerId)
                .then(validateCreditLimit(credit))
                .then(Mono.defer(() -> {

                    BigDecimal currentDebt = getOrZero(credit.getUsedAmount());

                    if (currentDebt.compareTo(BigDecimal.ZERO) <= 0) {
                        return Mono.error(new RuntimeException(
                                "Credit product has no pending debt"
                        ));
                    }

                    if (request.amount().compareTo(currentDebt) > 0) {
                        return Mono.error(new RuntimeException(
                                "Payment amount cannot be greater than current debt"
                        ));
                    }

                    BigDecimal newUsedAmount = currentDebt.subtract(request.amount());
                    BigDecimal newBalance = credit.getCreditLimit().subtract(newUsedAmount);

                    credit.setUsedAmount(newUsedAmount);
                    credit.setBalance(newBalance);

                    CreditPayment payment = buildCreditPayment(credit, request);

                    return creditRepository.save(credit)
                            .then(creditPaymentRepository.save(payment))
                            .thenReturn(new CreditPaymentResponse(
                                    credit.getCreditNumber(),
                                    request.amount(),
                                    currentDebt,
                                    newUsedAmount,
                                    STATUS_COMPLETED,
                                    MESSAGE_PAYMENT_COMPLETED
                            ));
                }));
    }

    private CreditConsumption buildCreditConsumption(
            Credit credit,
            String customerId,
            CreditConsumptionRequest request
    ) {

        CreditConsumption consumption = new CreditConsumption();
        consumption.setCreditId(credit.getCreditId());
        consumption.setCustomerId(customerId);
        consumption.setAmount(request.amount());
        consumption.setMerchantName(request.merchantName());
        consumption.setDescription(request.description());
        consumption.setConsumptionDate(LocalDateTime.now());
        consumption.setStatus(true);

        return consumption;
    }

    private CreditPayment buildCreditPayment(
            Credit credit,
            CreditPaymentRequest request
    ) {

        CreditPayment payment = new CreditPayment();
        payment.setCreditId(credit.getCreditId());
        payment.setPaymentMethod(normalize(request.paymentMethod()));
        payment.setAmount(request.amount());
        payment.setDescription(request.description());
        payment.setPaymentDate(LocalDateTime.now());
        payment.setStatus(true);

        return payment;
    }

    private Mono<Void> validateCreditBelongsToCustomer(
            Credit credit,
            String customerId
    ) {

        if (!customerId.equals(credit.getCustomerId())) {
            return Mono.error(new RuntimeException(
                    "Credit card does not belong to customer"
            ));
        }

        return Mono.empty();
    }

    private Mono<Void> validateCreditLimit(Credit credit) {

        if (credit.getCreditLimit() == null) {
            return Mono.error(new RuntimeException(
                    "Credit limit is required"
            ));
        }

        return Mono.empty();
    }

    private Mono<Void> validateCustomerCanCreateCreditCard(
            CustomerResponse customerResponse
    ) {

        /*
         * Regla asumida:
         * Cliente personal -> máximo una tarjeta de crédito activa.
         * Cliente empresarial -> puede tener varias tarjetas de crédito activas.
         */
        if (DOCUMENT_TYPE_BUSINESS.equals(customerResponse.documentType())) {
            return Mono.empty();
        }

        return creditRepository.existsByCustomerIdAndStatus(
                        customerResponse.id(),
                        true
                )
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(
                                new PersonalCreditAlreadyExistsException(
                                        customerResponse.id()
                                )
                        );
                    }

                    return Mono.empty();
                });
    }

    private CreditCardBalanceResponse toCreditCardBalanceResponse(Credit credit) {

        BigDecimal creditLimit = getOrZero(credit.getCreditLimit());
        BigDecimal usedAmount = getOrZero(credit.getUsedAmount());
        BigDecimal availableBalance = creditLimit.subtract(usedAmount);

        return new CreditCardBalanceResponse(
                credit.getCreditNumber(),
                creditLimit,
                usedAmount,
                availableBalance,
                credit.getCurrencyType(),
                credit.getCurrencyName(),
                credit.getStatus()
        );
    }

    private CreditResponse toCreditResponse(Credit credit) {

        return CreditResponse.builder()
                .creditId(credit.getCreditId())
                .customerId(credit.getCustomerId())
                .creditNumber(credit.getCreditNumber())
                .balance(credit.getBalance())
                .creditLimit(credit.getCreditLimit())
                .usedAmount(credit.getUsedAmount())
                .currencyName(credit.getCurrencyName())
                .exchangeRate(credit.getExchangeRate())
                .annualInterestRate(credit.getAnnualInterestRate())
                .build();
    }

    private String resolveCardType(CustomerResponse customerResponse) {

        return switch (customerResponse.documentType()) {
            case DOCUMENT_TYPE_PERSONAL -> CARD_TYPE_PERSONAL;
            case DOCUMENT_TYPE_BUSINESS -> CARD_TYPE_BUSINESS;
            default -> throw new RuntimeException(
                    "Invalid customer document type: "
                            + customerResponse.documentType()
            );
        };
    }

    private BigDecimal resolveExchangeRate(String currencyName) {

        if (CurrencyName.SOLES.name().equals(currencyName)) {
            return PEN_EXCHANGE_RATE;
        }

        if (CurrencyName.DOLAR.name().equals(currencyName)) {
            return USD_EXCHANGE_RATE;
        }

        return BigDecimal.ONE;
    }

    private Mono<Void> validateCreditRequest(CreditRequest request) {

        Map<String, String> errors = new HashMap<>();

        if (request == null) {
            errors.put("request", "Credit request is required");
            return Mono.error(new BusinessValidationException(errors));
        }

        validateDocument(
                errors,
                request.documentType(),
                request.documentNumber()
        );


        return validateErrors(errors);
    }

    private Mono<Void> validateConsumptionRequest(
            CreditConsumptionRequest request
    ) {

        Map<String, String> errors = new HashMap<>();

        if (request == null) {
            errors.put("request", "Credit consumption request is required");
            return Mono.error(new BusinessValidationException(errors));
        }

        validateDocument(
                errors,
                request.documentType(),
                request.documentNumber()
        );



        return validateErrors(errors);
    }

    private Mono<Void> validatePaymentRequest(
            CreditPaymentRequest request
    ) {

        Map<String, String> errors = new HashMap<>();

        if (request == null) {
            errors.put("request", "Credit payment request is required");
            return Mono.error(new BusinessValidationException(errors));
        }

        validateDocument(
                errors,
                request.documentType(),
                request.documentNumber()
        );

        return validateErrors(errors);
    }

    private Mono<Void> validateBalanceRequest(BalanceRequest request) {

        Map<String, String> errors = new HashMap<>();

        if (request == null) {
            errors.put("request", "Balance request is required");
            return Mono.error(new BusinessValidationException(errors));
        }

        validateDocument(
                errors,
                request.documentType(),
                request.documentNumber()
        );

        return validateErrors(errors);
    }

    private void validateDocument(
            Map<String, String> errors,
            String documentType,
            String documentNumber
    ) {

        if (documentType == null || documentType.isBlank()) {
            errors.put("documentType", "Document type is required");
            return;
        }

        String cleanDocumentType = normalize(documentType);

        if (!List.of(
                DOCUMENT_TYPE_PERSONAL,
                DOCUMENT_TYPE_BUSINESS
        ).contains(cleanDocumentType)) {
            errors.put(
                    "documentType",
                    "Document type must be 01 for PERSONAL or 02 for BUSINESS"
            );
            return;
        }

        if (documentNumber == null || documentNumber.isBlank()) {
            errors.put("documentNumber", "Document number is required");
            return;
        }

        String cleanDocumentNumber = normalizeText(documentNumber);

        if (DOCUMENT_TYPE_PERSONAL.equals(cleanDocumentType)
                && !cleanDocumentNumber.matches("^[0-9]{8}$")) {
            errors.put(
                    "documentNumber",
                    "Personal document number must contain exactly 8 digits"
            );
        }

        if (DOCUMENT_TYPE_BUSINESS.equals(cleanDocumentType)
                && !cleanDocumentNumber.matches("^[0-9]{11}$")) {
            errors.put(
                    "documentNumber",
                    "Business document number must contain exactly 11 digits"
            );
        }
    }



    private Mono<Void> validateErrors(Map<String, String> errors) {

        if (!errors.isEmpty()) {
            return Mono.error(new BusinessValidationException(errors));
        }

        return Mono.empty();
    }

    private BigDecimal getOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
