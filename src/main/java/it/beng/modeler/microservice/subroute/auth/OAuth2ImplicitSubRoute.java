package it.beng.modeler.microservice.subroute.auth;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.impl.AccessTokenImpl;
import io.vertx.ext.auth.oauth2.impl.OAuth2AuthProviderImpl;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import it.beng.modeler.config;
import it.beng.modeler.microservice.subroute.AuthSubRoute;
import it.beng.modeler.microservice.subroute.SubRoute;

import java.util.Base64;

import static it.beng.modeler.microservice.subroute.AuthSubRoute.loginRedirect;

/**
 * <p>This class is a member of <strong>modeler-microservice</strong> project.</p>
 *
 * @author vince
 */
public final class OAuth2ImplicitSubRoute extends OAuth2SubRoute {

    public static final String FLOW_TYPE = "IMPLICIT";
    public static final String FLOW_TYPE_WEB_CLIENT = FLOW_TYPE + "_WEB_CLIENT";

    public OAuth2ImplicitSubRoute(Vertx vertx, Router router, MongoClient mongodb, config.OAuth2Config oAuth2Config) {
        super(vertx, router, mongodb, oAuth2Config, FLOW_TYPE);
    }

    @Override
    protected void oauth2Init() {
        router.route(HttpMethod.GET, path + "login/handler").handler(this::redirectUrlHandler);
        router.route(HttpMethod.GET, baseHref + "oauth2/client/callback").handler(rc -> {
            redirect(rc, baseHref + config.app.path + SubRoute.locale(rc) + "/oauth2/client/callback");
        });
        router.route(HttpMethod.GET, path + "hash/:hash").handler(this::setupAccess);
    }

    private void redirectUrlHandler(RoutingContext rc) {
        StringBuilder url = new StringBuilder(
            oauth2ClientOptions.getSite() + oauth2ClientOptions.getAuthorizationPath());
        url.append("?");
        url.append("response_type=token");
        url.append("&");
        url.append("redirect_uri=");
        url.append(config.server.pub.origin());
        url.append(baseHref + "oauth2/client/callback");
        url.append("&");
        url.append("client_id=");
        url.append(oauth2ClientOptions.getClientID());
        url.append("&");
        url.append("scope=");
        url.append(oauth2Flow.scope.replace(" ", ","));
        url.append("&");
        url.append("state=");
        url.append(AuthSubRoute.getBase64EncodedState(rc));
        redirect(rc, url.toString());
    }

    private void setupAccess(RoutingContext rc) {

        String encodedHash = rc.request().getParam("hash");

        JsonObject hash = new JsonObject(new String(Base64.getDecoder().decode(encodedHash)));

        AuthSubRoute.checkState(rc, hash.getString("state"));

        WebClient client = WebClient.create(
            vertx,
            new WebClientOptions()
                .setUserAgent("CPD-WebClient/1.0")
                .setFollowRedirects(false)
        );
        client.requestAbs(HttpMethod.GET, oauth2Flow.getUserProfile)
              .putHeader("Accept", "application/json")
              .putHeader("Authorization", "Bearer " + hash.getString("access_token"))
              .putHeader("scope", String.join(",", oauth2Flow.scope.split("(\\s|,)")))
              .as(BodyCodec.jsonObject())
              .send(cr -> {
                  if (cr.succeeded()) {
                      AccessToken accessToken = new AccessTokenImpl((OAuth2AuthProviderImpl) oauth2Provider,
                          hash.put("scope", oauth2Flow.scope));
                      rc.setUser(accessToken);
                      HttpResponse<JsonObject> response = cr.result();
                      JsonObject profile = new JsonObject();
                      if (response.statusCode() == HttpResponseStatus.OK.code()) {
                          profile.mergeIn(response.body());
                          if (profile.getString("displayName") == null)
                              profile.put("displayName",
                                  (profile.getString("name", "") + " " +
                                      profile.getString("surname", "")).trim());
                      }
                      profile.put("provider", oauth2Config.provider)
                             .put("token", hash.getString("access_token"))
                             .put("position", config.model.roles.position.citizen)
                             .put("diagramRoles", new JsonObject()
                                 .put("*", new JsonArray()
                                     .add(config.model.roles.diagramRole.observer)));
                      accessToken.principal().put("profile", profile);
                      if (config.develop) System.out.println(Json.encodePrettily(rc.user().principal()));
                  }
                  client.close();
                  loginRedirect(rc, baseHref + config.app.path);
              });
    }

}
