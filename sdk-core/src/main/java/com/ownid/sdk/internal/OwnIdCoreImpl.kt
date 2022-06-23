package com.ownid.sdk.internal

import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.ownid.sdk.Configuration
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCore
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.events.EventsNetworkService
import com.ownid.sdk.internal.events.LogService
import com.ownid.sdk.internal.events.MetricService
import org.json.JSONException
import java.util.*
import kotlin.random.Random

/**
 * Class implements the part of [OwnIdCore] that is independent of specific integration.
 */
public abstract class OwnIdCoreImpl(
    public final override val instanceName: InstanceName,
    public final override val configuration: Configuration
) : OwnIdCore {

    private val correlationId: String = UUID.randomUUID().toString()

    @InternalOwnIdAPI
    private val eventsNetworkService: EventsNetworkService = EventsNetworkService(configuration)

    @InternalOwnIdAPI
    override val logService: LogService = LogService(configuration, correlationId, eventsNetworkService)

    @InternalOwnIdAPI
    override val metricService: MetricService = MetricService(configuration, correlationId, eventsNetworkService)

    @InternalOwnIdAPI
    @Throws(OwnIdException::class)
    override fun createRegisterIntent(context: Context, languageTags: String, email: String): Intent = try {
        val request = OwnIdRequest(this, OwnIdRequest.Type.REGISTER, languageTags, email)
        OwnIdActivity.createBaseIntent(context).putExtra(OwnIdActivity.KEY_REQUEST, request.toJsonString())
    } catch (cause: JSONException) {
        throw OwnIdException("Error in createRegisterIntent", cause)
    }

    @InternalOwnIdAPI
    @Throws(OwnIdException::class)
    override fun createLoginIntent(context: Context, languageTags: String, email: String): Intent = try {
        val request = OwnIdRequest(this, OwnIdRequest.Type.LOGIN, languageTags, email)
        OwnIdActivity.createBaseIntent(context).putExtra(OwnIdActivity.KEY_REQUEST, request.toJsonString())
    } catch (cause: JSONException) {
        throw OwnIdException("Error in createLoginIntent", cause)
    }

    @Throws(IllegalArgumentException::class)
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public fun generatePassword(
        length: Int, numberCapitalised: Int = 2, numberNumbers: Int = 2, numberSpecial: Int = 2
    ): String {
        require(numberCapitalised + numberNumbers + numberSpecial < length) {
            "numberCapitalised + numberNumbers + numberSpecial is >= length"
        }

        val possibleRegularChars = "abcdefghijklmnopqrstuvwxyz".toCharArray()
        val possibleCapitalChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()
        val possibleNumberChars = "0123456789".toCharArray()
        val possibleSpecialChars = "@$%*&^-+!#_=".toCharArray()

        val passwordRegular = CharArray((length - numberCapitalised - numberNumbers - numberSpecial)) {
            possibleRegularChars[Random.nextInt(possibleRegularChars.size)]
        }

        val passwordCapitalised = CharArray(numberCapitalised) {
            possibleCapitalChars[Random.nextInt(possibleCapitalChars.size)]
        }

        val passwordNumbers = CharArray(numberNumbers) {
            possibleNumberChars[Random.nextInt(possibleNumberChars.size)]
        }

        val passwordSpecial = CharArray(numberSpecial) {
            possibleSpecialChars[Random.nextInt(possibleSpecialChars.size)]
        }

        val password = passwordRegular.plus(passwordCapitalised).plus(passwordNumbers).plus(passwordSpecial)

        return password.apply { shuffle() }.concatToString()
    }
}