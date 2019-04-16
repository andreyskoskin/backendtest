import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import spark.Spark
import transfers.*

val mapper = jacksonObjectMapper()
val toJson = mapper::writeValueAsString

val service = Service(
    transfers.memstore.Accounts(),
    transfers.memstore.Transfers()
)

fun main(args: Array<String>) {
    initPort(args)
    Spark.before("*") { _, res -> res.header("Content-Type", "application/json") }
    Spark.get( "/accounts", { _, _ -> service.accounts() }, toJson)
    Spark.post("/accounts", ::newAccount, toJson)
    Spark.get( "/transfers", { _, _ -> service.transfers() }, toJson)
    Spark.post("/transfers", ::newTransfer, toJson)
}

fun initPort(args: Array<String>) {
    if (args.size == 1) {
        try {
            Spark.port(args[0].toInt())
        } catch (e: Exception) {
            println("Invalid port number: ${args[0]}, starting with default port ${Spark.port()}")
        }
    }
}

fun newTransfer(request: spark.Request, response: spark.Response): Any = service
    .transfer(mapper.readValue(request.body()))
    .respond(response)

fun newAccount(request: spark.Request, response: spark.Response): Any = service
    .createAccount(mapper.readValue(request.body()))
    .respond(response)

object OK

fun Error?.respond(response: spark.Response): Any =
    if (this == null) OK
    else {
        response.status(this.statusCode())
        this.description()
    }

fun Error.statusCode(): Int = when (this) {
    is AccountNotFound   -> 404
    is InsufficientFunds -> 460
    else                 -> 400
}