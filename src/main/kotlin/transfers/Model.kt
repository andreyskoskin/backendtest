package transfers

import java.math.BigDecimal
import java.time.Instant

sealed class Error
data class AccountNotFound(val id: String): Error()
object InvalidAmount: Error()
object SameAccount: Error()
object InsufficientFunds: Error()
object InvalidBalance: Error()

fun Error.description(): String = this.javaClass.simpleName + when (this) {
    is AccountNotFound -> " ${this.id}"
    else               -> ""
}

class TransferNotFound(id: String): Exception(id)

data class TransferRequest(
    val sourceAccountId: String,
    val destinationAccountId: String,
    val amount: BigDecimal
) {
    fun validate(): Error? = when {
        amount.signum() <= 0                    -> InvalidAmount
        sourceAccountId == destinationAccountId -> SameAccount
        else                                    -> null
    }
}

enum class TransferStatus {
    NEW, ACCEPTED, REJECTED
}

data class Transfer(
    val id: String,
    val timestamp: Instant,
    val sourceAccountId: String,
    val destinationAccountId: String,
    val amount: BigDecimal,
    val status: TransferStatus,
    val comment: String = ""
)

data class CreateAccountRequest(
    val initialBalance: BigDecimal
) {
    fun validate(): Error? = if (initialBalance.signum() < 0) InvalidBalance else null
}

data class Account(
    val id: String,
    val balance: BigDecimal
)
