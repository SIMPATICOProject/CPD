package it.beng.modeler.microservice.auth.local.impl;

import java.util.logging.Logger;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import it.beng.microservice.db.MongoDB;
import it.beng.modeler.config;
import it.beng.modeler.microservice.auth.local.LocalAuthProvider;

/**
 * <p>This class is a member of <strong>modeler-microservice</strong> project.</p>
 *
 * @author vince
 */
public class LocalAuthProviderImpl implements LocalAuthProvider {

    private static Logger logger = Logger.getLogger(LocalAuthProviderImpl.class.getName());

    // private final Vertx vertx;
    private final MongoDB mongodb;

    public LocalAuthProviderImpl(Vertx vertx) {
        // this.vertx = vertx;
        this.mongodb = config.mongoDB();
        if (mongodb == null)
            throw new IllegalStateException("could not find mongodb in current context");
    }

    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> resultHandler) {

        if (authInfo == null) {
            resultHandler.handle(Future.failedFuture("no authInfo provided"));
            return;
        }

        logger.finest("authenticating " + authInfo.encodePrettily());

        String id = authInfo.getString("username");
        if (id == null || "".equals(id.trim())) {
            resultHandler.handle(Future.failedFuture("no username provided"));
            return;
        }

        String password = authInfo.getString("password");
        if (password == null || "".equals(password.trim())) {
            resultHandler.handle(Future.failedFuture("no password provided"));
            return;
        }

        JsonObject auth = new JsonObject().put("id", id).put("password", password);
        mongodb.findOne(config.USER_COLLECTION, auth, new JsonObject(), ar -> {
            if (ar.succeeded()) {
                JsonObject account = ar.result();
                if (account != null/*  && password.equals(user.getString("password")) */) {
                    User user = new LocalUser(new JsonObject(), this);
                    user.principal().put("account", account);
                    resultHandler.handle(Future.succeededFuture(user));
                } else {
                    resultHandler.handle(Future.failedFuture("Invalid username/password"));
                }
            } else {
                resultHandler.handle(Future.failedFuture(ar.cause()));
            }
        });

    }

}
