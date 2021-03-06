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

import static net.openid.appauth.Preconditions.checkArgument;
import static net.openid.appauth.Preconditions.checkNotEmpty;
import static net.openid.appauth.Preconditions.checkNotNull;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Collects authorization state from authorization requests and responses. This facilitates
 * the creation of subsequent requests based on this state, and allows for this state to be
 * persisted easily.
 */
public class AuthState {

    /**
     * Tokens which have less time than this value left before expiry will be considered to be
     * expired for the purposes of calls to
     * {@link #performActionWithFreshTokens(AuthorizationService, AuthStateAction)
     * performActionWithFreshTokens}.
     */
    public static final int EXPIRY_TIME_TOLERANCE_MS = 60000;

    private static final String KEY_REFRESH_TOKEN = "refreshToken";
    private static final String KEY_SCOPE = "scope";
    private static final String KEY_LAST_AUTHORIZATION_RESPONSE = "lastAuthorizationResponse";
    private static final String KEY_LAST_TOKEN_RESPONSE = "mLastTokenResponse";
    private static final String KEY_AUTHORIZATION_EXCEPTION = "mAuthorizationException";

    @Nullable
    private String mRefreshToken;

    @Nullable
    private String mScope;

    @Nullable
    private AuthorizationResponse mLastAuthorizationResponse;

    @Nullable
    private TokenResponse mLastTokenResponse;

    @Nullable
    private Long mAuthorizationExceptionCode;

    private boolean mNeedsTokenRefreshOverride;

    /**
     * Creates an empty, unauthenticated {@link AuthState}.
     */
    public AuthState() {}

    /**
     * Creates an {@link AuthState} based on an authorization exchange.
     */
    public AuthState(@Nullable AuthorizationResponse authResponse,
            @Nullable Exception authError) {
        checkArgument(authResponse != null ^ authError != null,
                "exactly one of authResponse or authError should be non-null");
        update(authResponse, authError);
    }

    /**
     * Creates an {@link AuthState} based on an authorization exchange and subsequent token
     * exchange.
     */
    public AuthState(
            @NonNull AuthorizationResponse authResponse,
            @NonNull TokenResponse tokenResponse) {
        this(authResponse, (Exception) null);
        update(tokenResponse, null);
    }

    /**
     * The most recent refresh token received from the server, if available. Rather than using
     * this property directly as part of any request depending on authorization state, it is
     * recommended to call {@link #performActionWithFreshTokens(AuthorizationService,
     * AuthStateAction) performActionWithFreshTokens} to ensure that fresh tokens are available.
     */
    @Nullable
    public String getRefreshToken() {
        return mRefreshToken;
    }

    /**
     * The scope of the current authorization grant. This represents the latest scope returned by
     * the server and may be a subset of the scope that was initially granted.
     */
    @Nullable
    public String getScope() {
        return mScope;
    }

    /**
     * A set representation of {@link #getScope()}, for convenience.
     */
    @Nullable
    public Set<String> getScopeSet() {
        return ScopeUtil.scopeStringToSet(mScope);
    }

    /**
     * The most recent authorization response used to update the authorization state. For the
     * implicit flow, this will contain the latest access token. It is rarely necessary to
     * directly use the response; instead convenience methods are provided to retrieve the
     * {@link #getAccessToken() access token},
     * {@link #getAccessTokenExpirationTime() access token expiration},
     * {@link #getIdToken() ID token}
     * and {@link #getScopeSet() scope} regardless of the flow used to retrieve them.
     */
    @Nullable
    public AuthorizationResponse getLastAuthorizationResponse() {
        return mLastAuthorizationResponse;
    }

    /**
     * The most recent token response used to update this authorization state. For the
     * authorization code flow, this will contain the latest access token. It is rarely necessary
     * to directly use the response; instead convenience methods are provided to retrieve the
     * {@link #getAccessToken() access token},
     * {@link #getAccessTokenExpirationTime() access token expiration},
     * {@link #getIdToken() ID token}
     * and {@link #getScopeSet() scope} regardless of the flow used to retrieve them.
     */
    @Nullable
    public TokenResponse getLastTokenResponse() {
        return mLastTokenResponse;
    }

    /**
     * The configuration of the authorization service associated with this authorization state.
     */
    @Nullable
    public AuthorizationServiceConfiguration getAuthorizationServiceConfiguration() {
        if (mLastAuthorizationResponse != null) {
            return mLastAuthorizationResponse.request.configuration;
        }
        return null;
    }

    /**
     * The current access token, if available. Rather than using
     * this property directly as part of any request depending on authorization state, it s
     * recommended to call {@link #performActionWithFreshTokens(AuthorizationService,
     * AuthStateAction) performActionWithFreshTokens} to ensure that fresh tokens are available.
     */
    @Nullable
    public String getAccessToken() {
        if (mAuthorizationExceptionCode != null) {
            return null;
        }

        if (mLastTokenResponse != null) {
            return mLastTokenResponse.accessToken;
        }

        if (mLastAuthorizationResponse != null) {
            return mLastAuthorizationResponse.accessToken;
        }

        return null;
    }

    /**
     * The expiration time of the current access token (if available), as milliseconds from the
     * UNIX epoch (consistent with {@link System#currentTimeMillis()}).
     */
    @Nullable
    public Long getAccessTokenExpirationTime() {
        if (mAuthorizationExceptionCode != null) {
            return null;
        }

        if (mLastTokenResponse != null) {
            return mLastTokenResponse.accessTokenExpirationTime;
        }

        if (mLastAuthorizationResponse != null) {
            return mLastAuthorizationResponse.accessTokenExpirationTime;
        }

        return null;
    }

    /**
     * The current ID token, if available.
     */
    @Nullable
    public String getIdToken() {
        if (mAuthorizationExceptionCode != null) {
            return null;
        }

        if (mLastTokenResponse != null) {
            return mLastTokenResponse.idToken;
        }

        if (mLastAuthorizationResponse != null) {
            return mLastAuthorizationResponse.idToken;
        }

        return null;
    }

    /**
     * Determines whether the current state represents a successful authorization,
     * from which at least either an access token or an ID token have been retrieved.
     */
    public boolean isAuthorized() {
        return mAuthorizationExceptionCode == null
                && (getAccessToken() != null || getIdToken() != null);
    }

    /**
     * Determines whether the access token is considered to have expired.
     */
    public boolean getNeedsTokenRefresh() {
        return getNeedsTokenRefresh(SystemClock.INSTANCE);
    }

    @VisibleForTesting
    boolean getNeedsTokenRefresh(Clock clock) {
        if (mNeedsTokenRefreshOverride) {
            return true;
        }

        if (getAccessTokenExpirationTime() != null) {
            return (getAccessTokenExpirationTime() + EXPIRY_TIME_TOLERANCE_MS)
                    <= clock.getCurrentTimeMillis();
        }

        return false;
    }

    /**
     * Sets whether to force an access token refresh, irrespective of the expiration time.
     */
    public void setNeedsTokenRefresh(boolean needsTokenRefresh) {
        mNeedsTokenRefreshOverride = needsTokenRefresh;
    }

    /**
     * Updates the authorization state based on a new authorization response.
     */
    public void update(
            @Nullable AuthorizationResponse authResponse,
            @Nullable Exception authError) {
        checkArgument(authResponse != null ^ authError != null,
                "exactly one of authResponse or authError should be non-null");
        if (authError != null) {
            // TODO
            return;
        }

        // the last token response and refresh token are now stale, as they are associated with
        // any previous authorization response
        mLastAuthorizationResponse = authResponse;
        mLastTokenResponse = null;
        mRefreshToken = null;
        mAuthorizationExceptionCode = null;

        // if the response's mScope is nil, it means that it equals that of the request
        // see: https://tools.ietf.org/html/rfc6749#section-5.1
        mScope = (authResponse.scope != null) ? authResponse.scope : authResponse.request.scope;
    }

    /**
     * Updates the authorization state based on a new token response.
     */
    public void update(
            @Nullable TokenResponse tokenResponse,
            @Nullable Exception authError) {
        checkArgument(tokenResponse != null ^ authError != null,
                "exactly one of authResponse or authError should be non-null");

        if (mAuthorizationExceptionCode != null) {
            // Calling updateFromTokenResponse while in an error state probably means the developer
            // obtained a new token and did the exchange without also calling
            // updateFromAuthorizationResponse. Attempt to handle this gracefully, but warn the
            // developer that this is unexpected.
            Logger.warn("AuthState.updateFromTokenResponse should not be called in an error state"
                    + "(%d), call updateFromAuthorizationResponse with the result of the fresh"
                    + "authorization response first",
                    mAuthorizationExceptionCode);
            mAuthorizationExceptionCode = null;
        }

        if (authError != null) {
            // TODO
            return;
        }

        mLastTokenResponse = tokenResponse;
        if (tokenResponse.scope != null) {
            mScope = tokenResponse.scope;
        }
        if (tokenResponse.refreshToken != null) {
            mRefreshToken = tokenResponse.refreshToken;
        }
    }

    /**
     * Ensures that a non-expired access token is available before invoking the provided action.
     */
    public void performActionWithFreshTokens(
            @NonNull AuthorizationService service,
            @NonNull AuthStateAction action) {
        performActionWithFreshTokens(service, Collections.<String, String>emptyMap(), action);
    }

    /**
     * Ensures that a non-expired access token is available before invoking the provided action.
     * If a token refresh is required, the provided additional parameters will be included in this
     * refresh request.
     */
    public void performActionWithFreshTokens(
            @NonNull AuthorizationService service,
            @NonNull Map<String, String> refreshTokenAdditionalParams,
            @NonNull AuthStateAction action) {
        performActionWithFreshTokens(
                service,
                refreshTokenAdditionalParams,
                SystemClock.INSTANCE,
                action);
    }

    @VisibleForTesting
    void performActionWithFreshTokens(
            @NonNull final AuthorizationService service,
            @NonNull final Map<String, String> refreshTokenAdditionalParams,
            @NonNull final Clock clock,
            @NonNull final AuthStateAction action) {
        checkNotNull(service, "service cannot be null");
        checkNotNull(refreshTokenAdditionalParams,
                "additional params cannot be null");
        checkNotNull(clock, "clock cannot be null");
        checkNotNull(action, "action cannot be null");
        if (mRefreshToken == null) {
            throw new IllegalStateException("No refresh token available");
        }

        if (!getNeedsTokenRefresh()) {
            action.execute(getAccessToken(), getIdToken(), null);
            return;
        }

        service.performTokenRequest(createTokenRefreshRequest(refreshTokenAdditionalParams),
                new AuthorizationService.TokenResponseCallback() {
                    @Override
                    public void onTokenRequestCompleted(
                            @Nullable TokenResponse response,
                            @Nullable AuthorizationException ex) {
                        update(response, ex);
                        if (ex == null) {
                            mNeedsTokenRefreshOverride = false;
                            action.execute(getAccessToken(), getIdToken(), null);
                        } else {
                            action.execute(null, null, ex);
                        }
                    }
                });
    }

    /**
     * Creates a token request for new tokens using the current refresh token.
     */
    public TokenRequest createTokenRefreshRequest() {
        return createTokenRefreshRequest(Collections.<String, String>emptyMap());
    }

    /**
     * Creates a token request for new tokens using the current refresh token, adding the
     * specified additional parameters.
     */
    public TokenRequest createTokenRefreshRequest(
            @NonNull Map<String, String> additionalParameters) {
        if (mRefreshToken == null) {
            throw new IllegalStateException("No refresh token available for refresh request");
        }
        if (mLastAuthorizationResponse == null) {
            throw new IllegalStateException(
                    "No authorization configuration available for refresh request");
        }

        return new TokenRequest.Builder(
                mLastAuthorizationResponse.request.configuration,
                mLastAuthorizationResponse.request.clientId)
                .setGrantType(TokenRequest.GRANT_TYPE_REFRESH_TOKEN)
                .setScope(mLastAuthorizationResponse.request.scope)
                .setRefreshToken(mRefreshToken)
                .setAdditionalParameters(additionalParameters)
                .build();
    }

    /**
     * Converts the authorization state to a JSON object for storage or transmission.
     */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        JsonUtil.putIfNotNull(json, KEY_REFRESH_TOKEN, mRefreshToken);
        JsonUtil.putIfNotNull(json, KEY_SCOPE, mScope);
        JsonUtil.putIfNotNull(json, KEY_AUTHORIZATION_EXCEPTION, mAuthorizationExceptionCode);
        if (mLastAuthorizationResponse != null) {
            JsonUtil.put(
                    json,
                    KEY_LAST_AUTHORIZATION_RESPONSE,
                    mLastAuthorizationResponse.toJson());
        }
        if (mLastTokenResponse != null) {
            JsonUtil.put(
                    json,
                    KEY_LAST_TOKEN_RESPONSE,
                    mLastTokenResponse.toJson());
        }
        return json;
    }

    /**
     * Converts the authorization state to a JSON string for storage or transmission.
     */
    public String toJsonString() {
        return toJson().toString();
    }

    /**
     * Restores authorization state from JSON produced by {@link #toJson()}.
     * @throws JSONException if the JSON is malformed or missing required fields.
     */
    public static AuthState fromJson(@NonNull JSONObject json) throws JSONException {
        checkNotNull(json, "json cannot be null");

        AuthState state = new AuthState();
        state.mRefreshToken = JsonUtil.getStringIfDefined(json, KEY_REFRESH_TOKEN);
        state.mScope = JsonUtil.getStringIfDefined(json, KEY_SCOPE);
        state.mAuthorizationExceptionCode =
                JsonUtil.getLongIfDefined(json, KEY_AUTHORIZATION_EXCEPTION);
        if (json.has(KEY_LAST_AUTHORIZATION_RESPONSE)) {
            state.mLastAuthorizationResponse = AuthorizationResponse.fromJson(
                    json.getJSONObject(KEY_LAST_AUTHORIZATION_RESPONSE));
        }
        if (json.has(KEY_LAST_TOKEN_RESPONSE)) {
            state.mLastTokenResponse = TokenResponse.fromJson(
                    json.getJSONObject(KEY_LAST_TOKEN_RESPONSE));
        }

        return state;
    }

    /**
     * Restored authorization state from a JSON string produced by {@link #toJsonString()}.
     * @throws JSONException if the JSON is malformed or missing required fields.
     */
    public static AuthState fromJson(@NonNull String jsonStr) throws JSONException {
        checkNotEmpty(jsonStr, "jsonStr cannot be null or empty");
        return fromJson(new JSONObject(jsonStr));
    }

    /**
     * Interface for actions executed in the context of fresh (non-expired) tokens.
     * @see #performActionWithFreshTokens(AuthorizationService, AuthStateAction)
     */
    public interface AuthStateAction {
        /**
         * Executed in the context of fresh (non-expired) tokens. If new tokens were
         * required to execute the action and could not be acquired, an authorization
         * exception is provided instead. One or both of the access token and ID token will be
         * provided, dependent upon the token types previously negotiated.
         */
        void execute(
                @Nullable String accessToken,
                @Nullable String idToken,
                @Nullable AuthorizationException ex);
    }
}
