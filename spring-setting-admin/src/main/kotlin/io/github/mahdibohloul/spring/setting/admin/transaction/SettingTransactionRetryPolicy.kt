package io.github.mahdibohloul.spring.setting.admin.transaction

/**
 * Decides if a failed admin transaction can run again as a new transaction.
 *
 * The admin service asks this policy only when a `TransactionalOperator` wraps the write. The storage module
 * supplies the policy, because only the storage knows which errors are transient. For example,
 * `spring-setting-mongodb` accepts the errors that MongoDB labels `TransientTransactionError`.
 */
fun interface SettingTransactionRetryPolicy {
  fun isRetryable(error: Throwable): Boolean
}
