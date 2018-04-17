package it.beng.modeler.microservice.actions;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public abstract class IncomingAction extends Action {
    public IncomingAction(JsonObject action) {
        super(action);
    }

    protected abstract String innerType();

    @Override
    public boolean isValid() {
        return super.isValid() && type().equals(innerType());
    }

    public abstract void handle(JsonObject account, Handler<AsyncResult<JsonObject>> handler);
}
