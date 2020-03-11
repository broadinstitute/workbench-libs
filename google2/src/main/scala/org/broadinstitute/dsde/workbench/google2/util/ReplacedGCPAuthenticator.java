package org.broadinstitute.dsde.workbench.google2.util;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.authenticators.Authenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

// this class implements io.kubernetes.client.util.authenticators.GCPAuthenticator
// It is taken from https://github.com/kubernetes-client/java/issues/290#issuecomment-480205118

//YOU NEED TO CALL REFRESH YOURSELF PERIODICALLY IF YOU USE THIS
// DOCUMENTATION BELOW TAKEN FROM KubeConfig.java
   // TODO cache tokens between calls, up to .status.expirationTimestamp
    // TODO a 401 is supposed to force a refresh,
    // but KubeconfigAuthentication hardcodes AccessTokenAuthentication which does not support that
    // and anyway ClientBuilder only calls Authenticator.provide once per ApiClient;
    // we would need to do it on every request

public class ReplacedGCPAuthenticator implements Authenticator {
    private static final Logger log;
    private static final String ACCESS_TOKEN = "access-token";
    private static final String EXPIRY = "expiry";

    static {
        log = LoggerFactory.getLogger(io.kubernetes.client.util.authenticators.GCPAuthenticator.class);
    }

    private final GoogleCredentials credentials;

    public ReplacedGCPAuthenticator(GoogleCredentials credentials) {
        this.credentials = credentials;
    }

    public String getName() {
        return "gcp";
    }

    public String getToken(Map<String, Object> config) {
        return (String) config.get("access-token");
    }

    public boolean isExpired(Map<String, Object> config) {
        Object expiryObj = config.get("expiry");
        Instant expiry = null;
        if (expiryObj instanceof Date) {
            expiry = ((Date) expiryObj).toInstant();
        } else if (expiryObj instanceof Instant) {
            expiry = (Instant) expiryObj;
        } else {
            if (!(expiryObj instanceof String)) {
                throw new RuntimeException("Unexpected object type: " + expiryObj.getClass());
            }

            expiry = Instant.parse((String) expiryObj);
        }

        return expiry != null && expiry.compareTo(Instant.now()) <= 0;
    }

    public Map<String, Object> refresh(Map<String, Object> config) {
        try {
            AccessToken accessToken = this.credentials.refreshAccessToken();

            config.put(ACCESS_TOKEN, accessToken.getTokenValue());
            config.put(EXPIRY, accessToken.getExpirationTime());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return config;
    }
}
