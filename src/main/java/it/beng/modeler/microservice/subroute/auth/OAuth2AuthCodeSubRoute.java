package it.beng.modeler.microservice.subroute.auth;

import java.util.logging.Logger;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import it.beng.microservice.common.ServerError;
import it.beng.modeler.config;
import it.beng.modeler.microservice.subroute.AuthSubRoute;

/**
 * <p>This class is a member of <strong>modeler-microservice</strong> project.</p>
 *
 * @author vince
 */
public final class OAuth2AuthCodeSubRoute extends OAuth2SubRoute {

    private static Logger logger = Logger.getLogger(OAuth2AuthCodeSubRoute.class.getName());

    public static final String FLOW_TYPE = "AUTH_CODE";

    public OAuth2AuthCodeSubRoute(Vertx vertx, Router router, config.OAuth2Config oauth2Config) {
        super(vertx, router, oauth2Config, FLOW_TYPE);
    }

    @Override
    protected void init() {

        // create OAuth2 handler
        OAuth2AuthHandler oAuth2Handler = OAuth2AuthHandler.create(oauth2Provider, config.oauth2.origin);
        for (String scope : oauth2Flow.scope.split("(\\s|,)")) {
            oAuth2Handler.addAuthority(scope);
        }
        oAuth2Handler.setupCallback(router.get(baseHref + "oauth2/server/callback"));
        router.route(HttpMethod.GET, path + "login/handler").handler(oAuth2Handler);
        router.route(HttpMethod.GET, path + "login/handler").handler(this::providerLoginHandler);
    }

    private static String getUserId(String[] encodedJsons) {
        String userId = null;
        for (String s : encodedJsons) {
            try {
                String decoded = base64.decode(s);
                JsonObject o = new JsonObject(decoded);
                userId = o.getString("sub");
                if (userId != null) {
                    logger.finest("userId found: " + userId);
                    break;
                }
            } catch (Exception e) {
                continue;
            }
        }
        return userId;
    }

    private void providerLoginHandler(final RoutingContext context) {

        final AccessToken user = (AccessToken) context.user();

        if (user == null) {
            context.next();
            return;
        } else {

            final String userId = getUserId(user.principal().getString("id_token").split("\\."));

            if (userId == null)
                throw new IllegalStateException("user-id not found in user principal!");
            // user.principal().put("id", userId);

            final String token = user.principal().getString("access_token");
            final WebClient client = WebClient.create(vertx,
                new WebClientOptions().setUserAgent("CPD-WebClient/1.0")
                    .setFollowRedirects(false));
            client.requestAbs(HttpMethod.GET, oauth2Flow.getUserProfile.replace("{userId}", userId))
                .putHeader("Accept", "application/json")
                .putHeader("Authorization", "Bearer " + token)
                .as(BodyCodec.jsonObject())
                .send(cr -> {
                    client.close();
                    if (cr.succeeded()) {
                        HttpResponse<JsonObject> response = cr.result();
                        if (response.statusCode() == HttpResponseStatus.OK.code()) {
                            final JsonObject body = response.body();
                            logger.finest("body: " + body.encodePrettily());
                            final JsonObject state = new JsonObject(
                                base64.decode(context.session().remove("encodedState")));
                            final JsonObject loginState = state.getJsonObject("loginState");
                            final String provider = loginState.getString("provider");
                            final String firstName = PROVIDER_MAPS.get(provider).get(FIRST_NAME);
                            final String lastName = PROVIDER_MAPS.get(provider).get(LAST_NAME);
                            final String displayName = PROVIDER_MAPS.get(provider).get(DISPLAY_NAME);
                            final String email = PROVIDER_MAPS.get(provider).get(EMAIL);
                            JsonObject account = new JsonObject();
                            // use email as account ID
                            account.put("id",
                                body.getJsonArray("emails")
                                    .stream()
                                    .filter(item -> item instanceof JsonObject
                                            && "account".equals(((JsonObject) item).getString("type")))
                                    .map(item -> (JsonObject) item)
                                    .findFirst()
                                    .get()
                                    .getString(email, "guest.user@simpatico-project.eu"));
                            account.put(FIRST_NAME, body.getJsonObject("name").getString(firstName, "Guest"));
                            account.put(LAST_NAME, body.getJsonObject("name").getString(lastName, "User"));
                            account.put(DISPLAY_NAME, body.getString(displayName, ""));
                            account.put(DISPLAY_NAME, body.getString(displayName, ""));
                            // generate displayName if it does not exists
                            if ("".equals(account.getString(DISPLAY_NAME, "").trim()))
                                account.put(DISPLAY_NAME,
                                    (account.getString(FIRST_NAME) + " " + account.getString(LAST_NAME))
                                        .trim());
                            // set user account
                            user.principal().put("account", account);
                            // set user roles
                            getUserRoles(account, roles -> {
                                if (roles.succeeded()) {
                                    // redirect
                                    logger.finest(
                                        "auth_code user principal: " + context.user().principal().encodePrettily());
                                    redirect(context, config.server.appPath(context) + loginState.getString("redirect"));
                                } else {
                                    context.fail(roles.cause());
                                }
                            });
                        } else {
                            context.fail(ServerError.message("error while fetching user account"));
                        }
                    } else {
                        context.fail(cr.cause());
                    }
                });
        }

    }

}
