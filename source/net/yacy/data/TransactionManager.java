// TransactionManager.java
// Copyright 2017 by luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;

import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.http.servlets.DisallowedMethodException;
import net.yacy.http.servlets.TemplateMissingParameterException;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;

/**
 * This class provides transaction tokens generation and checking for protected operations.
 * These tokens should be designed to be hard to forge by an unauthenticated user.
 */
public class TransactionManager {

    /** Parameter name of the transaction token */
    public static final String TRANSACTION_TOKEN_PARAM = "transactionToken";

    /** Secret signing key valid until next server restart */
    private static final String SIGNING_KEY = UUID.randomUUID().toString();

    /** Random token seed valid until next server restart */
    private static final String TOKEN_SEED = UUID.randomUUID().toString();

    /**
     * @param header
     *            current request header. Must not be null.
     * @return the name of the currently authenticated user (administrator user
     *         name when the request comes from local host and unauthenticated local
     *         access as administrator is enabled), or null when no authenticated.
     * @throws NullPointerException
     *             when header parameter is null.
     */
    private static String getUserName(final RequestHeader header) {
        String userName = header.getRemoteUser();
        if (userName == null) userName = "admin"; // set a default to be able to create a transaction token
        Switchboard sb = Switchboard.getSwitchboard();

        if (sb != null) {
            final String adminAccountBase64MD5 = sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "");
            final String adminAccountUserName = sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin");

            if (header.accessFromLocalhost()) {

                if (sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false)) {
                    /* Unauthenticated local access as administrator can be enabled */
                    userName = adminAccountUserName;
                } else {
                    /* authorization by encoded password, only for localhost access (used by bash scripts)*/
                    String pass = Base64Order.standardCoder.encodeString(adminAccountUserName + ":" + adminAccountBase64MD5);

                    /* get the authorization string from the header */
                    final String realmProp = (header.get(RequestHeader.AUTHORIZATION, "")).trim();
                    final String realmValue = realmProp.isEmpty() ? null : realmProp.substring(6); // take out "BASIC "

                    if (pass.equals(realmValue)) { // assume realmValue as is in cfg
                        userName = adminAccountUserName;
                    }
                }
            }
        }

        return userName;
    }

    /**
     * Get a transaction token to be used later on a protected HTTP post method
     * call on the same path with the currently authenticated user.
     * 
     * @param header
     *            current request header
     * @return a transaction token
     * @throws IllegalArgumentException
     *             when header parameter is null or when the user is not authenticated.
     */
    public static String getTransactionToken(final RequestHeader header) {
        if (header == null) {
                throw new IllegalArgumentException("Missing required header parameter");
        }

        return getTransactionToken(header, header.getPathInfo());
    }

    /**
     * Get a transaction token to be used later on a protected HTTP post method
     * call on the specified path with the currently authenticated user.
     * 
     * @param header
     *            current request header
     * @param path the relative path for which the token will be valid
     * @return a transaction token for the specified path
     * @throws IllegalArgumentException
     *             when a parameter is null or when the user is not authenticated.
     */
    public static String getTransactionToken(final RequestHeader header, final String path) {
        if (header == null) {
            throw new IllegalArgumentException("Missing required header parameter");
        }

        /* Check this comes from an authenticated user */
        final String userName = getUserName(header);
        if (userName == null) {
            throw new IllegalArgumentException("User is not authenticated");
        }

        /* Produce a token by signing a message with the server secret key : 
         * The token is not unique per request and thus keeps the service stateless 
         * (no need to store tokens until they are consumed).
         * On the other hand, it is supposed to remain hard enough to forge because the secret key and token seed 
         * are initialized with a random value at each server startup */
        final String token = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, SIGNING_KEY)
                        .hmacHex(TOKEN_SEED + userName + path);

        return token;
    }

    /**
     * Check the current request is a valid HTTP POST transaction : the current user is authenticated, 
     * and the request post parameters contain a valid transaction token.
     * @param header current request header
     * @param post request parameters
     * @throws IllegalArgumentException when a parameter is null.
     * @throws DisallowedMethodException when the HTTP method is something else than post
     * @throws TemplateMissingParameterException when the transaction token is missing
     * @throws BadTransactionException when a condition for valid transaction is not met.
     */
    public static void checkPostTransaction(final RequestHeader header, final serverObjects post) {
        if (header == null)
             throw new IllegalArgumentException("Missing required header parameters.");

        if (header.accessFromLocalhost()) return; // this is one exception that we accept if basc authentication is gven

        if (post == null) // non-local requests must use POST parameters
            throw new IllegalArgumentException("Missing required post parameters.");

        if (!HeaderFramework.METHOD_POST.equals(header.getMethod())) // non-local users must use POST protocol
                throw new DisallowedMethodException("HTTP POST method is the only one authorized.");

        String userName = getUserName(header);
        if (userName == null)
                throw new BadTransactionException("User is not authenticated.");

        final String transactionToken = post.get(TRANSACTION_TOKEN_PARAM);
        if (transactionToken == null)
                throw new TemplateMissingParameterException("Missing transaction token.");

        final String token = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, SIGNING_KEY)
                        .hmacHex(TOKEN_SEED + userName + header.getPathInfo());

        /* Compare the server generated token with the one received in the post parameters, 
         * using a time constant function */
        if(!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), transactionToken.getBytes(StandardCharsets.UTF_8))) {
                throw new BadTransactionException("Invalid transaction token.");
        }
    }

}
