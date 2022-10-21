package com.ownid.demo.gigya.ui.activity;

import android.content.Intent;

import com.gigya.android.sdk.Gigya;
import com.gigya.android.sdk.GigyaCallback;
import com.gigya.android.sdk.account.models.GigyaAccount;
import com.gigya.android.sdk.account.models.Profile;
import com.gigya.android.sdk.network.GigyaError;
import com.ownid.demo.ui.activity.BaseUserActivity;
import com.ownid.sdk.exception.OwnIdException;

public class UserActivity extends BaseUserActivity {

    @Override
    public void signOut() {
        Gigya.getInstance(GigyaAccount.class).logout();
    }

    @Override
    public void startMainActivity() {
        startActivity(new Intent(UserActivity.this, MainActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();

        Gigya.getInstance(GigyaAccount.class)
                .getAccount(true, new GigyaCallback<GigyaAccount>() {
                    @Override
                    public void onSuccess(GigyaAccount account) {
                        if (account == null) {
                            startMainActivity();
                            finish();
                        } else {
                            final Profile profile = account.getProfile();
                            if (profile != null) {
                                String displayName = profile.getFirstName();
                                if (displayName == null) displayName = "";

                                String email = profile.getEmail();
                                if (email == null) email = "";
                                showUser(displayName, email);
                            }
                        }
                    }

                    @Override
                    public void onError(GigyaError error) {
                        String message;
                        if (error != null) message = error.getLocalizedMessage();
                        else message = "Unknown error";
                        showError(new OwnIdException(message));
                    }
                });
    }
}
