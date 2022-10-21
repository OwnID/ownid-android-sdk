package com.ownid.demo.gigya.ui.activity;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.ownid.demo.gigya.ui.fragment.CreateFragment;
import com.ownid.demo.gigya.ui.fragment.LoginFragment;
import com.ownid.demo.ui.activity.BaseMainActivity;

public class MainActivity extends BaseMainActivity {

    @NonNull
    @Override
    public Fragment getLoginFragment() {
        return new LoginFragment();
    }

    @NonNull
    @Override
    public Fragment getCreateFragment() {
        return new CreateFragment();
    }
}
