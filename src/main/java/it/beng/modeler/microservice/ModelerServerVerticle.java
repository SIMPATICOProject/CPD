package it.beng.modeler.microservice;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import it.beng.modeler.config;
import it.beng.modeler.microservice.subroute.ApiSubRoute;
import it.beng.modeler.microservice.subroute.AssetsSubRoute;
import it.beng.modeler.microservice.subroute.AuthenticationSubRoute;
import it.beng.modeler.microservice.subroute.RootSubRoute;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>This class is a member of <strong>modeler-microservice</strong> project.</p>
 *
 * @author vince
 */
public class ModelerServerVerticle extends AbstractVerticle {

    static {
//        Typed.init();
//        Diagram.init();
//        SemanticElement.init();
    }

    @Override
    public void start(Future<Void> startFuture) {

        // Create a router object.
        Router router = Router.router(vertx);

        // configure CORS origins and allowed methods
        router.route().handler(
            CorsHandler.create(config.server.allowedOriginPattern)
                       .allowedMethod(HttpMethod.GET)      // select
                       .allowedMethod(HttpMethod.POST)     // insert
                       .allowedMethod(HttpMethod.PUT)      // update
                       .allowedMethod(HttpMethod.DELETE)   // delete
                       .allowedHeader("X-PINGARUNER")
                       .allowedHeader("Content-Type")
        );
        System.out.println("CORS pattern is: " + config.server.allowedOriginPattern);

        // create cookie and session handler
        router.route().handler(CookieHandler.create());
        router.route().handler(
            SessionHandler.create(/*ClusteredSessionStore*/LocalSessionStore.create(vertx, "cpd.web.session.map"))
                          .setSessionCookieName("cpd.web.session")
                          .setCookieHttpOnlyFlag(true)
                          .setCookieSecureFlag(true)
                          .setCookieSecureFlag(true)
                          .setSessionTimeout(TimeUnit.HOURS.toMillis(12))
        );

        // set secure headers in each response
        router.route().handler(rc -> {
            rc.response()
/*
                **X-Content-Type-Options**

                The 'X-Content-Type-Options' HTTP header if set to 'nosniff' stops the browser from guessing the MIME
                type of a file via content sniffing. Without this option set there is a potential increased risk of
                cross-site scripting.

                Secure configuration: Server returns the 'X-Content-Type-Options' HTTP header set to 'nosniff'.
*/
              .putHeader("X-Content-Type-Options", "nosniff")
/*
                **X-XSS-Protection**

                The 'X-XSS-Protection' HTTP header is used by Internet Explorer version 8 and higher. Setting this HTTP
                header will instruct Internet Explorer to enable its inbuilt anti-cross-site scripting filter. If
                enabled, but without 'mode=block' then there is an increased risk that otherwise non exploitable
                cross-site scripting vulnerabilities may potentially become exploitable.

                Secure configuration: Server returns the 'X-XSS-Protection' HTTP header set to '1; mode=block'.
*/
              .putHeader("X-XSS-Protection", "1; mode=block")
/*
                **X-Frame-Options**

                The 'X-Frame-Options' HTTP header can be used to indicate whether or not a browser should be allowed to
                render a page within a <frame> or <iframe>. The valid options are DENY, to deny allowing the page to
                exist in a frame or SAMEORIGIN to allow framing but only from the originating host. Without this option
                set the site is at a higher risk of click-jacking unless application level mitigations exist.

                Secure configuration: Server returns the 'X-Frame-Options' HTTP header set to 'DENY' or 'SAMEORIGIN'.
*/
              .putHeader("X-FRAME-OPTIONS", "DENY")
/*
                **Cache-Control**

                The 'Cache-Control' response header controls how pages can be cached either by proxies or the user's
                browser. Using this response header can provide enhanced privacy by not caching sensitive pages in the
                users local cache at the potential cost of performance. To stop pages from being cached the server sets
                a cache control by returning the 'Cache-Control' HTTP header set to 'no-store'.

                Secure configuration: Either the server sets a cache control by returning the 'Cache-Control' HTTP
                header set to 'no-store, no-cache' or each page sets their own via the 'meta' tag for secure
                connections.

                Updated: The above was updated after our friend Mark got in-touch. Originally we had said no-store was
                sufficient. But as with all things web related it appears Internet Explorer and Firefox work slightly
                differently (so everyone ensure you thank Mark!).
*/
              .putHeader("Cache-Control", "no-store, no-cache")
/*
                **Strict-Transport-Security**

                The 'HTTP Strict Transport Security' (Strict-Transport-Security) HTTP header is used to control if the
                browser is allowed to only access a site over a secure connection and how long to remember the server
                response for thus forcing continued usage.

                Note: This is a draft standard which only Firefox and Chrome support. But it is supported by sites such
                as PayPal. This header can only be set and honoured by web browsers over a trusted secure connection.

                Secure configuration: Return the 'Strict-Transport-Security' header with an appropriate timeout over an
                secure connection.
*/
              .putHeader("Strict-Transport-Security", "max-age=" + 15768000)
/*
                **Access-Control-Allow-Origin**

                The 'Access Control Allow Origin' HTTP header is used to control which sites are allowed to bypass same
                origin policies and send cross-origin requests. This allows cross origin access without web application
                developers having to write mini proxies into their apps.

                Note: This is a draft standard which only Firefox and Chrome support, it is also advocarted by sites
                such as http://enable-cors.org/.

                Secure configuration: Either do not set or return the 'Access-Control-Allow-Origin' header restricting
                it to only a trusted set of sites.
*/
//                .putHeader("Access-Control-Allow-Origin", "a b c")
/*
                 IE8+ do not allow opening of attachments in the context of this resource
*/
              .putHeader("X-Download-Options", "noopen");
            // TODO: logger.debug only
            if (config.develop) System.out.println("[" + rc.request().method() + "] " + rc.request().uri());
            rc.next();
        });

        // create the mongodb client
        MongoClient mongodb = MongoClient.createShared(vertx, config().getJsonObject("mongodb"), "cpd");
        vertx.getOrCreateContext().put("mongodb", mongodb);

        router.route(HttpMethod.GET, "/create-demo-data").handler(this::crateDemoData);

        new AuthenticationSubRoute(vertx, router, mongodb);
        new ApiSubRoute(vertx, router, mongodb);
        new AssetsSubRoute(vertx, router, mongodb);
        new RootSubRoute(vertx, router, mongodb);

        // handle failures
        router.route().failureHandler(rc -> {
            JsonObject error = rc.get("error") != null ? rc.get("error") : ResponseError.json(rc, null);
            System.err.println(Json.encodePrettily(rc.response()));
            System.err.println("ERROR (" + error.getInteger("statusCode") + "): " + error.encodePrettily());
            switch (rc.statusCode()) {
                case 404: {
                    // let root application find the resource or show the 404 not found page
                    rc.reroute("/");
                    break;
                }
                default: {
                    rc.response()
                      .putHeader("content-type", "application/json; charset=utf-8")
                      .setStatusCode(error.getInteger("statusCode"))
                      .end(error.encode());
                }
            }
        });

        vertx.createHttpServer(new HttpServerOptions()
                .setSsl(true)
                .setKeyStoreOptions(new JksOptions()
                    .setPath(config.keystore.filename)
                    .setPassword(config.keystore.password)
                )
                .setTrustStoreOptions(
                    new JksOptions()
                        .setPath(config.keystore.filename)
                        .setPassword(config.keystore.password)
                )
//            .setClientAuth(ClientAuth.REQUIRED)
        )
             .requestHandler(router::accept)
             .listen(config.server.port, ar -> {
                     if (ar.succeeded()) {
                         System.out.println("HTTP Server started: " + config.rootOrigin());
                         startFuture.complete();
                     } else {
                         System.out.println("Cannot start HTTP Server: " + ar.cause().getMessage());
                         startFuture.fail(ar.cause());
                     }
                 }
             );
    }

    private void crateDemoData(RoutingContext rc) {
        final String PATH = "web/assets/db/demo-data/";
        final MongoClient mongodb = vertx.getOrCreateContext().get("mongodb");
        mongodb.getCollections(ar -> {
            if (ar.failed()) throw new ResponseError(rc, ar.cause());
            else {
                StringBuffer result = new StringBuffer();
                List<String> collections = ar.result();
                if (collections.contains("users"))
                    result.append("users collection already exists (skipped)\n");
                else {
                    vertx.fileSystem().readFile(PATH + "users.json", tr -> {
                        if (tr.succeeded()) {
                            final JsonArray types = new JsonArray(tr.result().toString());
                            for (Object o : types.getList()) {
                                mongodb.save("users", new JsonObject(Json.encode(o)), ur -> {});
                            }
                            result.append("users collection written\n");
                        } else {
                            throw new ResponseError(rc, tr.cause());
                        }
                    });
                }
                if (collections.contains("types"))
                    result.append("types collection already exists (skipped)\n");
                else {
                    vertx.fileSystem().readFile(PATH + "types.json", tr -> {
                        if (tr.succeeded()) {
                            final JsonArray types = new JsonArray(tr.result().toString());
                            for (Object o : types.getList()) {
                                mongodb.save("types", new JsonObject(Json.encode(o)), mr -> {});
                            }
                            result.append("types collection written\n");
                        } else {
                            throw new ResponseError(rc, tr.cause());
                        }
                    });
                }
                if (collections.contains("diagrams"))
                    result.append("diagrams collection already exists (skipped)\n");
                else {
                    vertx.fileSystem().readFile(PATH + "diagrams.json", tr -> {
                        if (tr.succeeded()) {
                            final JsonArray diagrams = new JsonArray(tr.result().toString());
                            for (Object o : diagrams.getList()) {
                                mongodb.save("diagrams", new JsonObject(Json.encode(o)), mr -> {});
                            }
                            result.append("diagrams collection written\n");
                        } else {
                            throw new ResponseError(rc, tr.cause());
                        }
                    });
                }
                if (collections.contains("diagram.elements"))
                    result.append("diagram.elements collection already exists (skipped)\n");
                else {
                    vertx.fileSystem().readFile(PATH + "diagram.elements.json", tr -> {
                        if (tr.succeeded()) {
                            final JsonArray diagrams = new JsonArray(tr.result().toString());
                            for (Object o : diagrams.getList()) {
                                mongodb.save("diagram.elements", new JsonObject(Json.encode(o)), mr -> {});
                            }
                            result.append("diagram.elements collection written\n");
                        } else {
                            throw new ResponseError(rc, tr.cause());
                        }
                    });
                }
                if (collections.contains("semantic.elements"))
                    result.append("semantic.elements collection already exists (skipped)\n");
                else {
                    vertx.fileSystem().readFile(PATH + "semantic.elements.json", tr -> {
                        if (tr.succeeded()) {
                            final JsonArray diagrams = new JsonArray(tr.result().toString());
                            for (Object o : diagrams.getList()) {
                                mongodb.save("semantic.elements", new JsonObject(Json.encode(o)), mr -> {});
                            }
                            result.append("semantic.elements collection written\n");
                        } else {
                            throw new ResponseError(rc, tr.cause());
                        }
                    });
                }
                rc.response().end(result.toString());
            }
        });
    }

}