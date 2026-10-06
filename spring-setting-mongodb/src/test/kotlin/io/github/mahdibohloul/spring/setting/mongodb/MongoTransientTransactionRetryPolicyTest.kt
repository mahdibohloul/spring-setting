package io.github.mahdibohloul.spring.setting.mongodb

import com.mongodb.MongoException
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionSystemException

class MongoTransientTransactionRetryPolicyTest {
  private val policy = MongoTransientTransactionRetryPolicy()

  @Test
  fun `isRetryable accepts a transient transaction error in the cause chain`() {
    // given
    val mongoError = MongoException(112, "WriteConflict")
    mongoError.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
    val error = TransactionSystemException("Could not commit Mongo transaction", mongoError)

    // when
    val retryable = policy.isRetryable(error)

    // verify
    check(retryable)
  }

  @Test
  fun `isRetryable rejects an unknown commit result`() {
    // given
    val mongoError = MongoException(50, "MaxTimeMSExpired")
    mongoError.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)
    val error = TransactionSystemException("Could not commit Mongo transaction", mongoError)

    // when
    val retryable = policy.isRetryable(error)

    // verify
    check(!retryable)
  }

  @Test
  fun `isRetryable rejects an error that is not from MongoDB`() {
    // given
    val error = IllegalStateException("audit down")

    // when
    val retryable = policy.isRetryable(error)

    // verify
    check(!retryable)
  }
}
