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

package net.openid.appauth;

import android.net.Uri;

/**
 * Contains common test values which are useful across all tests.
 */
class TestValues {

    public static final String TEST_CLIENT_ID = "test_client_id";
    public static final String TEST_STATE = "$TAT3";
    public static final String TEST_APP_SCHEME = "com.test.app";
    public static final Uri TEST_APP_REDIRECT_URI = Uri.parse(TEST_APP_SCHEME + ":/oidc_callback");
    public static final String TEST_SCOPE = "openid email";
    public static final Uri TEST_IDP_AUTH_ENDPOINT =
            Uri.parse("https://testidp.example.com/authorize");
    public static final Uri TEST_IDP_TOKEN_ENDPOINT =
            Uri.parse("https://testidp.example.com/token");

    public static final String TEST_CODE_VERIFIER = "0123456789_0123456789_0123456789_0123456789";
    public static final String TEST_AUTH_CODE = "zxcvbnmjk";
    public static final String TEST_ACCESS_TOKEN = "aaabbbccc";
    public static final String TEST_ID_TOKEN = "abc.def.ghi";
    public static final String TEST_REFRESH_TOKEN = "asdfghjkl";

    public static AuthorizationServiceConfiguration getTestServiceConfig() {
        return new AuthorizationServiceConfiguration(
                TEST_IDP_AUTH_ENDPOINT,
                TEST_IDP_TOKEN_ENDPOINT);
    }

    public static AuthorizationRequest.Builder getTestAuthRequestBuilder() {
        return new AuthorizationRequest.Builder(
                getTestServiceConfig(),
                TEST_CLIENT_ID,
                AuthorizationRequest.RESPONSE_TYPE_CODE,
                TEST_APP_REDIRECT_URI)
                .setScopes(AuthorizationRequest.SCOPE_OPENID, AuthorizationRequest.SCOPE_EMAIL)
                .setCodeVerifier(TEST_CODE_VERIFIER);
    }

    public static AuthorizationRequest getTestAuthRequest() {
        return getTestAuthRequestBuilder().build();
    }

    public static TokenRequest.Builder getTestAuthCodeExchangeRequestBuilder() {
        return new TokenRequest.Builder(getTestServiceConfig(), TEST_CLIENT_ID)
                .setAuthorizationCode(TEST_AUTH_CODE)
                .setCodeVerifier(TEST_CODE_VERIFIER)
                .setGrantType(TokenRequest.GRANT_TYPE_AUTHORIZATION_CODE)
                .setRedirectUri(TEST_APP_REDIRECT_URI);
    }

    public static TokenRequest getTestAuthCodeExchangeRequest() {
        return getTestAuthCodeExchangeRequestBuilder().build();
    }
}
