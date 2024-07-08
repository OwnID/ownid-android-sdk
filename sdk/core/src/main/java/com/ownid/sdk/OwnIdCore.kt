package com.ownid.sdk

import kotlin.random.Random

/**
 * Contains common functionality and key components of OwnID SDK that are independent of specific integration.
 */
public interface OwnIdCore {

    @InternalOwnIdAPI
    public companion object {
        @InternalOwnIdAPI
        @get:JvmName("getProductName")
        public val PRODUCT_NAME: ProductName = "OwnIDCore"
    }

    /**
     * Name of OwnID instance. Must be unique for Android application.
     */
    public val instanceName: InstanceName

    /**
     * Configuration of OwnID instance. See [Configuration]
     */
    public val configuration: Configuration

    /**
     * Creates new instance of [OwnIdWebViewBridge] that uses this instance of OwnID.
     */
    public fun createWebViewBridge(): OwnIdWebViewBridge

    /**
     * Generates random password.
     *
     * @param length            Total password length in characters
     * @param numberCapitalised Amount of capitalizes characters
     * @param numberNumbers     Amount of number characters
     * @param numberSpecial     Amount of special characters
     */
    @Throws(IllegalArgumentException::class)
    public fun generatePassword(
        length: Int, numberCapitalised: Int = 2, numberNumbers: Int = 2, numberSpecial: Int = 2
    ): String {
        require(numberCapitalised + numberNumbers + numberSpecial < length) { "numberCapitalised + numberNumbers + numberSpecial is >= length" }

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