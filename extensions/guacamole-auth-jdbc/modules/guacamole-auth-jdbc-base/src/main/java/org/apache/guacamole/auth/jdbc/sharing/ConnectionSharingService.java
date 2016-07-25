/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.guacamole.auth.jdbc.sharing;

import com.google.inject.Inject;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;
import org.apache.guacamole.auth.jdbc.user.AuthenticatedUser;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleSecurityException;
import org.apache.guacamole.auth.jdbc.sharingprofile.ModeledSharingProfile;
import org.apache.guacamole.auth.jdbc.sharingprofile.SharingProfileService;
import org.apache.guacamole.auth.jdbc.tunnel.ActiveConnectionRecord;
import org.apache.guacamole.form.Field;
import org.apache.guacamole.net.auth.AuthenticationProvider;
import org.apache.guacamole.net.auth.Credentials;
import org.apache.guacamole.net.auth.credentials.CredentialsInfo;
import org.apache.guacamole.net.auth.credentials.UserCredentials;

/**
 * Service which provides convenience methods for sharing active connections.
 *
 * @author Michael Jumper
 */
public class ConnectionSharingService {

    /**
     * The name of the query parameter that is used when authenticating obtain
     * temporary access to a connection.
     */
    public static final String SHARE_KEY_NAME = "key";

    /**
     * Generator for sharing keys.
     */
    @Inject
    private ShareKeyGenerator keyGenerator;

    /**
     * Map of all currently-shared connections.
     */
    @Inject
    private SharedConnectionMap connectionMap;

    /**
     * Service for retrieving and manipulating sharing profile objects.
     */
    @Inject
    private SharingProfileService sharingProfileService;

    /**
     * The credentials expected when a user is authenticating using temporary
     * credentials in order to obtain access to a single connection.
     */
    public static final CredentialsInfo SHARE_KEY =
            new CredentialsInfo(Collections.<Field>singletonList(
                new Field(SHARE_KEY_NAME, Field.Type.QUERY_PARAMETER)
            ));

    /**
     * Generates a set of temporary credentials which can be used to connect to
     * the given connection using the given sharing profile. If the user does
     * not have permission to share the connection via the given sharing
     * profile, permission will be denied.
     *
     * @param user
     *     The user sharing the connection.
     *
     * @param activeConnection
     *     The active connection being shared.
     *
     * @param sharingProfileIdentifier
     *     The identifier of the sharing profile dictating the semantics or
     *     restrictions applying to the shared session.
     *
     * @return
     *     A newly-generated set of temporary credentials which can be used to
     *     connect to the given connection.
     *
     * @throws GuacamoleException
     *     If permission to share the given connection is denied.
     */
    public UserCredentials generateTemporaryCredentials(AuthenticatedUser user,
            ActiveConnectionRecord activeConnection,
            String sharingProfileIdentifier) throws GuacamoleException {

        // Pull sharing profile (verifying access)
        ModeledSharingProfile sharingProfile =
                sharingProfileService.retrieveObject(user,
                        sharingProfileIdentifier);

        // Verify that this profile is indeed a sharing profile for the
        // requested connection
        String connectionIdentifier = activeConnection.getConnectionIdentifier();
        if (sharingProfile == null || !sharingProfile.getPrimaryConnectionIdentifier().equals(connectionIdentifier))
            throw new GuacamoleSecurityException("Permission denied.");

        // Generate a share key for the requested connection
        String key = keyGenerator.getShareKey();
        connectionMap.add(new SharedConnectionDefinition(activeConnection,
                sharingProfile, key));

        // Ensure the share key is properly invalidated when the original
        // connection is closed
        activeConnection.registerShareKey(key);

        // Return credentials defining a single expected parameter
        return new UserCredentials(SHARE_KEY,
                Collections.singletonMap(SHARE_KEY_NAME, key));

    }

    /**
     * Returns a SharedConnectionUser (an implementation of AuthenticatedUser)
     * if the given credentials contain a valid share key. The returned user
     * will be associated with the single shared connection to which they have
     * been granted temporary access. If the share key is invalid, or no share
     * key is contained within the given credentials, null is returned.
     *
     * @param authProvider
     *     The AuthenticationProvider on behalf of which the user is being
     *     retrieved.
     *
     * @param credentials
     *     The credentials which are expected to contain the share key.
     *
     * @return
     *     A SharedConnectionUser with access to a single shared connection, if
     *     the share key within the given credentials is valid, or null if the
     *     share key is invalid or absent.
     */
    public SharedConnectionUser retrieveSharedConnectionUser(
            AuthenticationProvider authProvider, Credentials credentials) {

        // Pull associated HTTP request
        HttpServletRequest request = credentials.getRequest();
        if (request == null)
            return null;

        // Retrieve the share key from the request
        String shareKey = request.getParameter(ConnectionSharingService.SHARE_KEY_NAME);
        if (shareKey == null)
            return null;

        // Pull the connection definition describing the connection these
        // credentials provide access to (if any)
        SharedConnectionDefinition definition = connectionMap.get(shareKey);
        if (definition == null)
            return null;

        // Return temporary in-memory user with access only to the shared connection
        return new SharedConnectionUser(authProvider, definition, credentials);

    }
    
}
