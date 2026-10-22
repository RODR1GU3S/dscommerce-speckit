package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.Payment;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Insert only, within the caller's transaction; no merge/save or transaction completion here. */
public interface PaymentInsertRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    void insert(Payment payment);
}
