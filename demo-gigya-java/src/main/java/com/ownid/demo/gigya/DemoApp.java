package com.ownid.demo.gigya;

import android.app.Application;

import com.gigya.android.sdk.Gigya;
import com.ownid.sdk.OwnIdGigyaFactory;

public class DemoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Gigya.setApplication(this);

        OwnIdGigyaFactory.createInstanceFromFile(this);
    }
}
