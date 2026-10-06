package io.github.mahdibohloul.spring.setting.mongodb

import com.mongodb.MongoException
import io.github.mahdibohloul.spring.setting.admin.transaction.SettingTransactionRetryPolicy

/**
 * Accepts an error when MongoDB labels it `TransientTransactionError`, which means the whole transaction can run
 * again. Spring wraps the driver error (for example in `TransactionSystemException` on commit), so the label is
 * looked for in the cause chain.
 *
 * An `UnknownTransactionCommitResult` is not accepted: that commit can be applied, and a new transaction then
 * writes the audit entry two times.
 */
class MongoTransientTransactionRetryPolicy : SettingTransactionRetryPolicy {
  override fun isRetryable(error: Throwable): Boolean = generateSequence(error) { cause -> cause.cause }
    .take(MAX_CAUSE_DEPTH)
    .any { cause ->
      cause is MongoException && cause.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
    }

  private companion object {
    const val MAX_CAUSE_DEPTH = 16
  }
}
