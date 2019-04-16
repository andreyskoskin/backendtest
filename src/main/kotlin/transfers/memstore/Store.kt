package transfers.memstore

import transfers.*
import transfers.Accounts
import transfers.TransferRequest
import java.math.BigDecimal
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

class Accounts : Accounts {

    private val lock = ReentrantReadWriteLock()
    private val readLock = lock.readLock()
    private val writeLock = lock.writeLock()
    private val records: MutableMap<String, BigDecimal> = mutableMapOf()

    override fun all(): List<Account> = readLock.withLock {
        return records.map { Account(it.key, it.value) }
    }

    override fun find(id: String): Account? = readLock.withLock {
        return records[id] ?.let { Account(id, it) }
    }

    override fun create(initialBalance: BigDecimal): String = writeLock.withLock {
        val id = UUID.randomUUID().toString()
        records.putIfAbsent(id, initialBalance)
        return id
    }

    override fun updateBalance(accountId: String, delta: BigDecimal): Error? = writeLock.withLock {
        val balance = records[accountId] ?: return AccountNotFound(accountId)
        val newBalance = balance + delta
        if (newBalance < BigDecimal.ZERO) {
            return InsufficientFunds
        }
        records[accountId] = newBalance
        return null
    }
}

private data class TransferDescriptor(
    val sourceAccountId: String,
    val destinationAccountId: String,
    val amount: BigDecimal,
    val timestamp: Instant
)

private data class TransferRecord(
    val descriptor: TransferDescriptor,
    val status: TransferStatus = TransferStatus.NEW,
    val comment: String = ""
) {
    fun toTransfer(id: String): Transfer = Transfer(
        id = id,
        timestamp = descriptor.timestamp,
        sourceAccountId = descriptor.sourceAccountId,
        destinationAccountId = descriptor.destinationAccountId,
        amount = descriptor.amount,
        status = status,
        comment = comment
    )

    fun alteredStatus(status: TransferStatus, comment: String): TransferRecord =
            TransferRecord(descriptor, status, comment)
}

class Transfers : transfers.Transfers {

    private val records = ConcurrentHashMap<String, TransferRecord>()

    override fun all(): List<Transfer> = records.entries.map {
        it.value.toTransfer(it.key)
    }

    override fun append(request: TransferRequest): Transfer {
        val timestamp = Instant.now()
        val id = UUID.randomUUID().toString()
        val record = TransferRecord(
            descriptor = TransferDescriptor(
                timestamp = timestamp,
                sourceAccountId = request.sourceAccountId,
                destinationAccountId = request.destinationAccountId,
                amount = request.amount
            ),
            status = TransferStatus.NEW
        )
        records[id] = record
        return record.toTransfer(id)
    }

    override fun accept(transferId: String) {
        records.computeIfPresent(transferId) { _, record ->
            record.alteredStatus(TransferStatus.ACCEPTED, "")
        } ?: throw TransferNotFound(transferId)
    }

    override fun reject(transferId: String, reason: String) {
        records.computeIfPresent(transferId) { _, record ->
            record.alteredStatus(TransferStatus.REJECTED, reason)
        } ?: throw TransferNotFound(transferId)
    }
}