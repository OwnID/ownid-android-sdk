package com.ownid.demo.gigya.ui.fragment;

import static com.ownid.demo.ui.ExtKt.removeLinksUnderline;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

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
import com.ownid.sdk.GigyaRegistrationParameters;
import com.ownid.sdk.OwnIdGigyaFactory;
import com.ownid.sdk.OwnIdViewModelFactory;
import com.ownid.sdk.event.OwnIdRegisterEvent;
import com.ownid.sdk.exception.GigyaException;
import com.ownid.sdk.view.OwnIdButton;
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class CreateFragment extends Fragment {

    private OwnIdRegisterViewModel ownIdViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_create, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ownIdViewModel = OwnIdViewModelFactory.getOwnIdViewModel(this, OwnIdRegisterViewModel.class, OwnIdGigyaFactory.getDefault());

        ((OwnIdButton) view.findViewById(R.id.own_id_register)).setViewModel(ownIdViewModel, getViewLifecycleOwner());

        ownIdViewModel.getEvents().observe(getViewLifecycleOwner(), (Observer<OwnIdRegisterEvent>) ownIdEvent -> {
            if (ownIdEvent instanceof OwnIdRegisterEvent.Busy) {
                ((BaseMainActivity) requireActivity()).isBusy(((OwnIdRegisterEvent.Busy) ownIdEvent).isBusy());
            }

            if (ownIdEvent instanceof OwnIdRegisterEvent.ReadyToRegister) {
                final String loginId = ((OwnIdRegisterEvent.ReadyToRegister) ownIdEvent).getLoginId();
                if (!loginId.isEmpty())
                    ((EditText) view.findViewById(R.id.et_fragment_create_email)).setText(loginId);

                view.findViewById(R.id.et_fragment_create_password).setEnabled(false);

                view.findViewById(R.id.b_fragment_create_create).setOnClickListener(v -> {
                    final String name = ((EditText) view.findViewById(R.id.et_fragment_create_name)).getText().toString();
                    final String email = ((EditText) view.findViewById(R.id.et_fragment_create_email)).getText().toString();

                    final Map<String, Object> params = new HashMap<>();
                    try {
                        params.put("profile", new JSONObject().put("firstName", name).toString());
                    } catch (JSONException e) {
                        showError(e);
                    }

                    ownIdViewModel.register(email, new GigyaRegistrationParameters(params));
                });
            }

            if (ownIdEvent instanceof OwnIdRegisterEvent.Undo) {
                view.findViewById(R.id.et_fragment_create_password).setEnabled(true);
                view.findViewById(R.id.b_fragment_create_create).setOnClickListener(v -> createUserWithEmailAndPassword());
            }

            if (ownIdEvent instanceof OwnIdRegisterEvent.LoggedIn) {
                startUserActivity();
            }

            if (ownIdEvent instanceof OwnIdRegisterEvent.Error) {
                final Throwable cause = ((OwnIdRegisterEvent.Error) ownIdEvent).getCause();
                if (cause instanceof GigyaException) {
                    final GigyaError gigyaError = ((GigyaException) cause).getGigyaError();
                    showError(gigyaError.toString());
                } else {
                    showError(cause);
                }
            }
        });

        view.findViewById(R.id.b_fragment_create_create).setOnClickListener(v -> createUserWithEmailAndPassword());

        ((TextView) view.findViewById(R.id.tv_fragment_create_terms)).setMovementMethod(LinkMovementMethod.getInstance());
        removeLinksUnderline(view.findViewById(R.id.tv_fragment_create_terms));
    }

    private void createUserWithEmailAndPassword() {
        final Editable name = ((EditText) requireView().findViewById(R.id.et_fragment_create_name)).getText();
        final Editable email = ((EditText) requireView().findViewById(R.id.et_fragment_create_email)).getText();
        final Editable password = ((EditText) requireView().findViewById(R.id.et_fragment_create_password)).getText();

        if (password == null || password.toString().isEmpty()) {
            showError(new IllegalArgumentException("Password cannot be empty"));
        } else if (email == null || email.toString().isEmpty()) {
            showError(new IllegalArgumentException("Email cannot be empty"));
        } else {
            final Map<String, Object> params = new HashMap<>();

            if (name == null || name.toString().isEmpty()) {
                try {
                    params.put("profile", new JSONObject().put("firstName", name).toString());
                } catch (JSONException e) {
                    showError(e);
                }
            }

            final Gigya<GigyaAccount> gigya = Gigya.getInstance(GigyaAccount.class);
            gigya.register(email.toString(), password.toString(), params, new GigyaLoginCallback<GigyaAccount>() {
                @Override
                public void onSuccess(GigyaAccount ownIdGigyaAccount) {
                    startUserActivity();
                }

                @Override
                public void onError(GigyaError error) {
                    showError(error.toString());
                }
            });
        }
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