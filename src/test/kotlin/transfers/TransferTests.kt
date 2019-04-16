package transfers

import io.kotlintest.matchers.collections.shouldBeEmpty
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.equality.shouldBeEqualToUsingFields
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import transfers.memstore.Accounts
import transfers.memstore.Transfers
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadLocalRandom

class TransferTests : StringSpec({

    "transfer with illegal amount is rejected" {
        val service = Service(Accounts(), Transfers())
        service.transfer(TransferRequest("src", "dst", BigDecimal.valueOf(-1))).shouldBe(InvalidAmount)
        service.transfers().shouldBeEmpty()
        service.transfer(TransferRequest("src", "dst", BigDecimal.valueOf(0))).shouldBe(InvalidAmount)
        service.transfers().shouldBeEmpty()
    }

    "transfer with same account IDs is rejected" {
        val service = Service(Accounts(), Transfers())
        service.transfer(TransferRequest("same", "same", BigDecimal.valueOf(1))).shouldBe(SameAccount)
        service.transfers().shouldBeEmpty()
    }

    "transfer with invalid source account ID is rejected" {
        val accounts = Accounts()
        val service = Service(accounts, Transfers())
        val noSrcId = "noSrc"
        val noDstId = "noDst"
        val amount = BigDecimal.valueOf(1)

        service.transfers().shouldBeEmpty()
        service.transfer(TransferRequest(noSrcId, noDstId, BigDecimal.valueOf(1)))
            .shouldBe(AccountNotFound(noSrcId))
        val transfers = service.transfers()
        transfers.shouldHaveSize(1)
        val transfer = transfers[0]
        transfer.sourceAccountId.shouldBe(noSrcId)
        transfer.destinationAccountId.shouldBe(noDstId)
        transfer.amount.shouldBe(amount)
        transfer.status.shouldBe(TransferStatus.REJECTED)
    }

    "transfer with invalid destination account ID is rejected" {
        val accounts = Accounts()
        val service = Service(accounts, Transfers())
        val srcInitialBalance = BigDecimal.valueOf(2)
        val srcId = accounts.create(srcInitialBalance)
        val noDstId = "noDst"
        val amount = BigDecimal.valueOf(1)

        service.transfers().shouldBeEmpty()
        service.transfer(TransferRequest(srcId, noDstId, amount))
            .shouldBe(AccountNotFound(noDstId))
        val transfers = service.transfers()
        transfers.shouldHaveSize(1)
        val transfer = transfers[0]
        transfer.sourceAccountId.shouldBe(srcId)
        transfer.destinationAccountId.shouldBe(noDstId)
        transfer.amount.shouldBe(amount)
        transfer.status.shouldBe(TransferStatus.REJECTED)
        accounts.find(srcId)!!.balance.shouldBe(srcInitialBalance)
    }

    "transfer with insufficient funds is rejected" {
        val accounts = Accounts()
        val service = Service(accounts, Transfers())
        val srcInitialBalance = BigDecimal.ZERO
        val srcId = accounts.create(srcInitialBalance)
        val dstInitialBalance = BigDecimal.valueOf(2)
        val dstId = accounts.create(dstInitialBalance)
        val amount = BigDecimal.valueOf(1)

        service.transfers().shouldBeEmpty()
        service.transfer(TransferRequest(srcId, dstId, amount))
            .shouldBe(InsufficientFunds)
        val transfers = service.transfers()
        transfers.shouldHaveSize(1)
        val transfer = transfers[0]
        transfer.sourceAccountId.shouldBe(srcId)
        transfer.destinationAccountId.shouldBe(dstId)
        transfer.amount.shouldBe(amount)
        transfer.status.shouldBe(TransferStatus.REJECTED)
        accounts.find(srcId)!!.balance.shouldBe(srcInitialBalance)
        accounts.find(dstId)!!.balance.shouldBe(dstInitialBalance)
    }

    "correct transfer should be accepted and applied" {
        val accounts = Accounts()
        val service = Service(accounts, Transfers())
        val srcInitialBalance = BigDecimal.valueOf(3)
        val srcId = accounts.create(srcInitialBalance)
        val dstInitialBalance = BigDecimal.ZERO
        val dstId = accounts.create(dstInitialBalance)
        val amount = BigDecimal.valueOf(1)

        service.transfers().shouldBeEmpty()
        service.transfer(TransferRequest(srcId, dstId, amount)).shouldBe(null)
        val transfers = service.transfers()
        transfers.shouldHaveSize(1)
        val transfer = transfers[0]
        transfer.sourceAccountId.shouldBe(srcId)
        transfer.destinationAccountId.shouldBe(dstId)
        transfer.amount.shouldBe(amount)
        transfer.status.shouldBe(TransferStatus.ACCEPTED)
        accounts.find(srcId)!!.balance.shouldBe(srcInitialBalance - amount)
        accounts.find(dstId)!!.balance.shouldBe(dstInitialBalance + amount)
    }

    "random transfers - sequential and concurrent processing cause same final balances" {
        val accounts = Accounts()
        val transfers = Transfers()
        val service = Service(accounts, transfers)
        val accs = randomAccounts(Range(0, 100), 10)
        val requests = randomTransferRequests(accs, Range(1, 100), 100)
        accs.forEach { accounts.create(it.balance) }
        applyConcurrently(service, requests)()

        val result = accounts.all()
        val ts = transfers.all().sortedBy { it.timestamp }
        val expected = applyLogSequentially(accs, ts)

        result.shouldBeEqualToUsingFields(expected)
    }

    "random transfers - all balances are positive or zero" {
        val accounts = Accounts()
        val transfers = Transfers()
        val service = Service(accounts, transfers)
        val accs = randomAccounts(Range(0, 100), 10)
        val requests = randomTransferRequests(accs, Range(1, 100), 100)
        accs.forEach { accounts.create(it.balance) }
        applyConcurrently(service, requests)()

        val result = accounts.all()
        result.all {
            it.balance >= BigDecimal.ZERO
        }.shouldBe(true)
    }
})

fun randomAccounts(balanceRange: Range<Long>, count: Int): List<Account> = (1..count).map {
    val id = UUID.randomUUID().toString()
    val balance = ThreadLocalRandom.current().nextLong(balanceRange.min, balanceRange.max)
    Account(id, BigDecimal.valueOf(balance))
}

fun randomTransferRequests(accounts: List<Account>, amountRange: Range<Long>, count: Int): List<TransferRequest> = (1..count).map {
    randomTransferRequest(accounts, amountRange)
}

fun randomTransferRequest(accounts: List<Account>, amountRange: Range<Long>): TransferRequest {
    val sourceIndex = ThreadLocalRandom.current().nextInt(0, accounts.size)
    val source = accounts[sourceIndex]
    val destinations = accounts.filter { it.id != source.id }
    val destinationIndex = ThreadLocalRandom.current().nextInt(0, destinations.size)
    val destination = destinations[destinationIndex]
    val amount = BigDecimal.valueOf(ThreadLocalRandom.current().nextLong(amountRange.min, amountRange.max))
    return TransferRequest(source.id, destination.id, amount)
}

fun applyConcurrently(service: Service, requests: List<TransferRequest>): () -> Unit {
    val done = ArrayBlockingQueue<Boolean>(requests.size)
    requests.forEach {
        spawn {
            service.transfer(it)
            done.put(true)
        }
    }
    return { (0 until requests.size).forEach { _ -> done.poll() } }
}

data class Range<T>(val min: T, val max: T)

fun spawn(block: () -> Unit) {
    Thread {
        Thread.sleep(ThreadLocalRandom.current().nextLong(10))
        block()
    }.start()
}

fun applyLogSequentially(accounts: List<Account>, log: List<Transfer>): List<Account> {
    val accDB = mutableListOf<Account>()
    accDB.addAll(accounts)
    log.filter {
        it.status == TransferStatus.ACCEPTED
    }.sortedBy {
        it.timestamp
    }.forEach { transfer ->
        val srcIndex = accounts.indexOfFirst { it.id == transfer.sourceAccountId }
        val dstIndex = accounts.indexOfFirst { it.id == transfer.destinationAccountId }
        try {
            val src = accDB[srcIndex]
            val dst = accDB[dstIndex]
            accDB[srcIndex] = src.copy(balance = src.balance.subtract(transfer.amount))
            accDB[dstIndex] = dst.copy(balance = dst.balance.add(transfer.amount))
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw e
        }
    }
    return accounts
}