package com.ownid.demo.gigya;

import com.gigya.android.sdk.Gigya;
import com.ownid.demo.ui.activity.BaseDemoApp;
import com.ownid.sdk.OwnIdGigyaFactory;

public class DemoApp extends BaseDemoApp {

    @Override
    public void onCreate() {
        super.onCreate();

        Gigya.setApplication(this);
        Gigya<OwnIdGigyaAccount> gigya = Gigya.getInstance(OwnIdGigyaAccount.class);

        OwnIdGigyaFactory.createInstance(this, gigya);
    }
}
