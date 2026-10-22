package com.easylife.app.core.cloud

import com.easylife.app.core.ai.providers.CloudAiException
import com.google.firebase.Firebase
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One price the `shopSearch` function found on a retailer's own page (checked and dated by the server). */
data class OnlinePrice(
    val item: String,
    val retailer: String,
    val productTitle: String,
    val packSize: Double?,
    val unit: String?,
    val pricePkr: Double,
    val unitPrice: Double?,
    val url: String,
    val seenAt: String,
)

data class OnlinePrices(val results: List<OnlinePrice>, val notFound: List<String>, val checkedAt: String)

/** Online price search through the Cloud Function `shopSearch` (Smart AI, after funding). */
interface ShopSearchClient {
    suspend fun search(items: List<Pair<String, String>>, city: String): OnlinePrices
}

@Singleton
class FunctionsShopSearchClient @Inject constructor(private val firebase: FirebaseGateway) : ShopSearchClient {
    override suspend fun search(items: List<Pair<String, String>>, city: String): OnlinePrices {
        firebase.ensureSignedIn() ?: throw CloudAiException("unauthenticated", "not signed in")
        val data = hashMapOf(
            "items" to items.take(8).map { (key, name) -> hashMapOf("itemKey" to key, "name" to name) },
            "city" to city,
        )
        try {
            // Web search takes longer than other Smart AI calls.
            val reply = Firebase.functions.getHttpsCallable("shopSearch").withTimeout(180, TimeUnit.SECONDS)
                .call(data).await().getData() as? Map<*, *> ?: throw CloudAiException("invalid", "unexpected reply")
            return parse(reply)
        } catch (e: FirebaseFunctionsException) {
            throw CloudAiException(e.code.name, e.message ?: e.code.name)
        }
    }

    companion object {
        /** Reads the callable's reply defensively: anything malformed is dropped, never guessed. */
        fun parse(reply: Map<*, *>): OnlinePrices {
            fun Map<*, *>.str(k: String) = this[k] as? String
            fun Map<*, *>.num(k: String) = (this[k] as? Number)?.toDouble()
            val results = (reply["results"] as? List<*>).orEmpty().mapNotNull { r ->
                val m = r as? Map<*, *> ?: return@mapNotNull null
                OnlinePrice(
                    item = m.str("item") ?: return@mapNotNull null,
                    retailer = m.str("retailer") ?: return@mapNotNull null,
                    productTitle = m.str("productTitle").orEmpty(),
                    packSize = m.num("packSize"),
                    unit = m.str("unit"),
                    pricePkr = m.num("pricePkr")?.takeIf { it > 0 } ?: return@mapNotNull null,
                    unitPrice = m.num("unitPrice"),
                    url = m.str("url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return@mapNotNull null,
                    seenAt = m.str("seenAt").orEmpty(),
                )
            }
            val notFound = (reply["notFound"] as? List<*>).orEmpty().filterIsInstance<String>()
            return OnlinePrices(results, notFound, reply["checkedAt"] as? String ?: "")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ShopSearchModule {
    @Binds abstract fun client(impl: FunctionsShopSearchClient): ShopSearchClient
}
