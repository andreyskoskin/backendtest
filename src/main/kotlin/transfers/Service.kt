package transfers

import transfers.memstore.Accounts
import transfers.memstore.Transfers
import java.math.BigDecimal

interface Accounts {

    fun all(): List<Account>

    fun find(id: String): Account?

    fun create(initialBalance: BigDecimal): String

    fun updateBalance(accountId: String, delta: BigDecimal): Error?
}

interface Transfers {

    fun all(): List<Transfer>

    fun append(request: TransferRequest): Transfer

    fun accept(transferId: String)

    fun reject(transferId: String, reason: String)
}

class Service(
    private val accounts: Accounts,
    private val transfers: Transfers
) {
    fun accounts(): List<Account> = accounts.all()

    fun createAccount(request: CreateAccountRequest): Error? {
        request.validate() ?.let { return it }
        accounts.create(request.initialBalance)
        return null
    }

    fun transfers(): List<Transfer> = transfers.all()

    fun transfer(request: TransferRequest): Error? {
        request.validate() ?.let { return it }
        val transfer = transfers.append(request)
        transferMoney(request) ?.let {
            transfers.reject(transfer.id, it.description())
            return it
        }
        transfers.accept(transfer.id)
        return null
    }

    private fun transferMoney(request: TransferRequest): Error? {
        accounts.updateBalance(request.sourceAccountId, request.amount.negate()) ?.let {
            return it
        }
        accounts.updateBalance(request.destinationAccountId, request.amount) ?.let {
            accounts.updateBalance(request.sourceAccountId, request.amount)
            return it
        }
        return null
    }
}