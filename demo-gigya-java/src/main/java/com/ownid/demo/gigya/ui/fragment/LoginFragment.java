package com.ownid.demo.gigya.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import com.gigya.android.sdk.Gigya;
import com.gigya.android.sdk.GigyaLoginCallback;
import com.gigya.android.sdk.account.models.GigyaAccount;
import com.gigya.android.sdk.network.GigyaError;
import com.ownid.demo.gigya.R;
import com.ownid.demo.gigya.ui.activity.UserActivity;
import com.ownid.demo.ui.activity.BaseMainActivity;
import com.ownid.sdk.OwnIdGigyaFactory;
import com.ownid.sdk.OwnIdViewModelFactory;
import com.ownid.sdk.event.OwnIdLoginEvent;
import com.ownid.sdk.exception.GigyaException;
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel;

public class LoginFragment extends Fragment {

    private OwnIdLoginViewModel ownIdViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ownIdViewModel = OwnIdViewModelFactory.getOwnIdViewModel(this, OwnIdLoginViewModel.class, OwnIdGigyaFactory.getDefault());

        ownIdViewModel.attachToView(view.findViewById(R.id.own_id_login));

        ownIdViewModel.getIntegrationEvents().observe(getViewLifecycleOwner(), (Observer<OwnIdLoginEvent>) ownIdEvent -> {
            if (ownIdEvent instanceof OwnIdLoginEvent.Busy) {
            }

            if (ownIdEvent instanceof OwnIdLoginEvent.LoggedIn) {
                startUserActivity();
            }

            if (ownIdEvent instanceof OwnIdLoginEvent.Error) {
                final Throwable cause = ((OwnIdLoginEvent.Error) ownIdEvent).getCause();
                if (cause instanceof GigyaException) {
                    final GigyaError gigyaError = ((GigyaException) cause).getGigyaError();
                    showError(gigyaError.getLocalizedMessage());
                } else {
                    showError(cause);
                }
            }
        });

        view.findViewById(R.id.b_fragment_login_login).setOnClickListener(v -> {
            final String email = ((EditText) view.findViewById(R.id.et_fragment_login_email)).getText().toString();
            final String password = ((EditText) view.findViewById(R.id.et_fragment_login_password)).getText().toString();

            Gigya<GigyaAccount> gigya = Gigya.getInstance(GigyaAccount.class);
            gigya.login(email, password, new GigyaLoginCallback<GigyaAccount>() {
                @Override
                public void onSuccess(GigyaAccount ownIdGigyaAccount) {
                    startUserActivity();
                }

                @Override
                public void onError(GigyaError error) {
                    showError(error.getLocalizedMessage());
                }
            });
        });
    }

    private void startUserActivity() {
        startActivity(new Intent(requireActivity(), UserActivity.class));
        requireActivity().finish();
    }

    private void showError(Throwable throwable) {
        ((BaseMainActivity) requireActivity()).showError(throwable);
    }

    private void showError(String message) {
        ((BaseMainActivity) requireActivity()).showError(message);
    }
}
