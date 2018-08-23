package piuk.blockchain.androidcore.data.exchangerate.datastore

import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.prices.data.PriceDatum
import io.reactivex.Completable
import io.reactivex.Observable
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateService
import com.blockchain.utils.Optional
import piuk.blockchain.androidcore.utils.PrefsUtil
import piuk.blockchain.androidcore.utils.extensions.applySchedulers
import timber.log.Timber

class ExchangeRateDataStore(
    private val exchangeRateService: ExchangeRateService,
    private val prefsUtil: PrefsUtil
) {

    // Ticker data
    private var btcTickerData: Map<String, PriceDatum>? = null
    private var ethTickerData: Map<String, PriceDatum>? = null
    private var bchTickerData: Map<String, PriceDatum>? = null

    private fun btcExchangeRateObservable() =
        exchangeRateService.getBtcExchangeRateObservable()
            .doOnNext { btcTickerData = it.toMap() }

    private fun bchExchangeRateObservable() =
        exchangeRateService.getBchExchangeRateObservable()
            .doOnNext { bchTickerData = it.toMap() }

    private fun ethExchangeRateObservable() =
        exchangeRateService.getEthExchangeRateObservable()
            .doOnNext { ethTickerData = it.toMap() }

    fun updateExchangeRates(): Completable =
        Completable.fromObservable(
            Observable.merge(
                btcExchangeRateObservable(),
                bchExchangeRateObservable(),
                ethExchangeRateObservable()
            ).applySchedulers()
        )

    fun getCurrencyLabels(): Array<String> = btcTickerData!!.keys.toTypedArray()

    fun getLastPrice(cryptoCurrency: CryptoCurrency, currencyName: String): Double {
        val prefsKey: String
        val tickerData: Map<String, PriceDatum>?

        when (cryptoCurrency) {
            CryptoCurrency.BTC -> {
                prefsKey =
                    PREF_LAST_KNOWN_BTC_PRICE
                tickerData = btcTickerData
            }
            CryptoCurrency.ETHER -> {
                prefsKey =
                    PREF_LAST_KNOWN_ETH_PRICE
                tickerData = ethTickerData
            }
            CryptoCurrency.BCH -> {
                prefsKey =
                    PREF_LAST_KNOWN_BCH_PRICE
                tickerData = bchTickerData
            }
        }
        var currency = currencyName
        if (currency.isEmpty()) {
            currency = "USD"
        }

        var lastPrice: Double
        val lastKnown = try {
            prefsUtil.getValue("$prefsKey$currency", "0.0").toDouble()
        } catch (e: NumberFormatException) {
            Timber.e(e)
            prefsUtil.setValue("$prefsKey$currency", "0.0")
            0.0
        }

        if (tickerData == null) {
            lastPrice = lastKnown
        } else {
            val tickerItem = when (cryptoCurrency) {
                CryptoCurrency.BTC -> getTickerItem(currency, btcTickerData)
                CryptoCurrency.ETHER -> getTickerItem(currency, ethTickerData)
                CryptoCurrency.BCH -> getTickerItem(currency, bchTickerData)
            }

            lastPrice = when (tickerItem) {
                is Optional.Some -> tickerItem.element.price ?: 0.0
                else -> 0.0
            }

            if (lastPrice > 0.0) {
                prefsUtil.setValue("$prefsKey$currency", lastPrice.toString())
            } else {
                lastPrice = lastKnown
            }
        }

        return lastPrice
    }

    private fun getTickerItem(
        currencyName: String,
        tickerData: Map<String, PriceDatum>?
    ): Optional<PriceDatum> {
        val priceDatum = tickerData?.get(currencyName)
        return when {
            priceDatum != null -> Optional.Some(priceDatum)
            else -> Optional.None
        }
    }

    fun getBtcHistoricPrice(
        currency: String,
        timeInSeconds: Long
    ) = exchangeRateService.getBtcHistoricPrice(currency, timeInSeconds)
        .applySchedulers()

    fun getEthHistoricPrice(
        currency: String,
        timeInSeconds: Long
    ): Observable<Double> =
        exchangeRateService.getEthHistoricPrice(currency, timeInSeconds)
            .applySchedulers()

    fun getBchHistoricPrice(
        currency: String,
        timeInSeconds: Long
    ): Observable<Double> =
        exchangeRateService.getBchHistoricPrice(currency, timeInSeconds)
            .applySchedulers()

    companion object {
        private const val PREF_LAST_KNOWN_BTC_PRICE = "LAST_KNOWN_BTC_VALUE_FOR_CURRENCY_"
        private const val PREF_LAST_KNOWN_ETH_PRICE = "LAST_KNOWN_ETH_VALUE_FOR_CURRENCY_"
        private const val PREF_LAST_KNOWN_BCH_PRICE = "LAST_KNOWN_BCH_VALUE_FOR_CURRENCY_"
    }
}