/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openid.appauthdemo;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;

import java.util.List;

/**
 * Demonstrates the usage of the AppAuth library to connect to a set of pre-configured
 * OAuth2 providers.
 *
 * <p><em>NOTE</em>: From a clean checkout of this project, no IDPs are automatically configured.
 * Edit {@code res/values/idp_configs.xml} to specify the required configuration properties to
 * enable the IDPs you wish to test. If you wish to add additional IDPs for testing, please see
 * {@link IdentityProvider}.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private AuthorizationService mAuthService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAuthService = new AuthorizationService(this);
        ViewGroup idpButtonContainer = (ViewGroup) findViewById(R.id.idp_button_container);
        List<IdentityProvider> providers = IdentityProvider.getEnabledProviders(this);

        findViewById(R.id.sign_in_container).setVisibility(
                providers.isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.no_idps_configured).setVisibility(
                providers.isEmpty() ? View.VISIBLE : View.GONE);

        for (final IdentityProvider idp : providers) {
            final AuthorizationServiceConfiguration.RetrieveConfigurationCallback retrieveCallback =
                    new AuthorizationServiceConfiguration.RetrieveConfigurationCallback() {

                        @Override
                        public void onFetchConfigurationCompleted(
                                @Nullable AuthorizationServiceConfiguration serviceConfiguration,
                                @Nullable AuthorizationException ex) {
                            if (ex != null) {
                                Log.w(TAG, "Failed to retrieve configuration for " + idp.name, ex);
                            } else {
                                Log.d(TAG, "configuration retrieved for " + idp.name
                                        + ", proceeding");
                                makeAuthRequest(serviceConfiguration, idp);
                            }
                        }
                    };

            FrameLayout idpButton = new FrameLayout(this);
            idpButton.setBackgroundResource(idp.buttonImageRes);
            idpButton.setContentDescription(
                    getResources().getString(idp.buttonContentDescriptionRes));
            idpButton.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            idpButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d(TAG, "initiating auth for " + idp.name);
                    idp.retrieveConfig(MainActivity.this, retrieveCallback);
                }
            });

            TextView label = new TextView(this);
            label.setText(idp.name);
            label.setTextColor(getColorCompat(idp.buttonTextColorRes));
            label.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
            idpButton.addView(label);

            idpButtonContainer.addView(idpButton);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAuthService.dispose();
    }

    private void makeAuthRequest(
            @NonNull AuthorizationServiceConfiguration serviceConfig,
            @NonNull IdentityProvider idp) {

        AuthorizationRequest authRequest = new AuthorizationRequest.Builder(
                serviceConfig,
                idp.getClientId(),
                AuthorizationRequest.RESPONSE_TYPE_CODE,
                idp.getRedirectUri())
                .setScope(idp.getScope())
                .build();

        Log.d(TAG, "Making auth request to " + serviceConfig.authorizationEndpoint);
        mAuthService.performAuthorizationRequest(
                authRequest,
                TokenActivity.createPostAuthorizationIntent(
                        this,
                        authRequest,
                        serviceConfig.discoveryDoc),
                mAuthService.createCustomTabsIntentBuilder()
                        .setToolbarColor(getColorCompat(R.color.colorAccent))
                        .build());
    }

    @TargetApi(Build.VERSION_CODES.M)
    @SuppressWarnings("deprecation")
    private int getColorCompat(@ColorRes int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getColor(color);
        } else {
            return getResources().getColor(color);
        }
    }
}
