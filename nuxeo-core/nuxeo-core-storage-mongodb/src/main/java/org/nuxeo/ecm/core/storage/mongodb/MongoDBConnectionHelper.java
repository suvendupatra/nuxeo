/*
 * (C) Copyright 2014-2017 Nuxeo SA (http://nuxeo.com/) and others.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *  
 * Contributors:
 *     Florent Guillaume
 *     Kevin Leturc
 *     Funsho David
 */
package org.nuxeo.ecm.core.storage.mongodb;

import java.net.UnknownHostException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.NuxeoException;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.ServerAddress;

/**
 * @since 9.1
 */
public class MongoDBConnectionHelper {

    private static final Log log = LogFactory.getLog(MongoDBConnectionHelper.class);

    private static final String DB_DEFAULT = "nuxeo";

    private static final int MONGODB_OPTION_CONNECTION_TIMEOUT_MS = 30000;

    private static final int MONGODB_OPTION_SOCKET_TIMEOUT_MS = 60000;

    private MongoDBConnectionHelper() {
        // Empty
    }

    public static MongoClient newMongoClient(MongoDBRepositoryDescriptor descriptor) throws UnknownHostException {
        String server = descriptor.server;
        if (StringUtils.isBlank(server)) {
            throw new NuxeoException("Missing <server> in MongoDB repository descriptor");
        }
        MongoClientOptions.Builder optionsBuilder = MongoClientOptions.builder()
                // Can help to prevent firewall disconnects inactive connection, option not available from URI
                .socketKeepAlive(true)
                // don't wait for ever by default, can be overridden using URI options
                .connectTimeout(MONGODB_OPTION_CONNECTION_TIMEOUT_MS)
                .socketTimeout(MONGODB_OPTION_SOCKET_TIMEOUT_MS)
                .description("Nuxeo");
        MongoClient client;
        if (server.startsWith("mongodb://")) {
            // allow mongodb:// URI syntax for the server, to pass everything in one string
            client = new MongoClient(new MongoClientURI(server, optionsBuilder));
        } else {
            client = new MongoClient(new ServerAddress(server), optionsBuilder.build());
        }
        if (log.isDebugEnabled()) {
            log.debug("MongoClient initialized with options: " + client.getMongoClientOptions().toString());
        }
        return client;
    }

    public static DBCollection getCollection(MongoClient mongoClient, String dbname, String collection) {
        if (StringUtils.isBlank(dbname)) {
            dbname = DB_DEFAULT;
        }
        DB db = mongoClient.getDB(dbname);
        return db.getCollection(collection);
    }

}
