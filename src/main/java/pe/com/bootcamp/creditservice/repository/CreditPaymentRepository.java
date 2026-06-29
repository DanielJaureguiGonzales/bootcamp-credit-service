package pe.com.bootcamp.creditservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import pe.com.bootcamp.creditservice.model.CreditPayment;
import reactor.core.publisher.Flux;

@Repository
public interface CreditPaymentRepository extends ReactiveMongoRepository<CreditPayment, String> {
    Flux<CreditPayment> findByCreditIdAndStatusOrderByPaymentDateDesc(
            String creditId,
            Boolean status
    );
}
