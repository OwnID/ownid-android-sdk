package com.ownid.demo.gigya;

import com.gigya.android.sdk.account.models.GigyaAccount;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

/**
 * Gigya user account class used in this demo.
 */
public class OwnIdGigyaAccount extends GigyaAccount {

    /**
     * Contains OwnID data
     */
    @Nullable
    private JsonObject data;

    public OwnIdGigyaAccount(@Nullable JsonObject data) {
        this.data = data;
    }

    @Nullable
    public final JsonObject getData() {
        return this.data;
    }

    public final void setData(@Nullable JsonObject data) {
        this.data = data;
    }
}
