package com.example.labpay.transaction;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class TransactionManagerFacade {

    private final PlatformTransactionManager platformTransactionManager;

    public <T> T execute(
            TransactionOptions options,
            Supplier<T> action,
            Consumer<T> onCommit,
            Consumer<RuntimeException> onRollback
    ) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setName(options.name());
        definition.setPropagationBehavior(options.propagationBehavior());
        definition.setTimeout(options.timeout());

        TransactionStatus status = platformTransactionManager.getTransaction(definition);

        try {
            T result = action.get();
            platformTransactionManager.commit(status);

            if (onCommit != null) {
                onCommit.accept(result);
            }

            return result;
        } catch (RuntimeException ex) {
            if (!status.isCompleted()) {
                platformTransactionManager.rollback(status);
            }
            if (onRollback != null) {
                onRollback.accept(ex);
            }
            throw ex;
        }
    }
}