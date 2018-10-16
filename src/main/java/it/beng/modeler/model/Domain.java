package it.beng.modeler.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>This class is a member of <strong>modeler-microservice</strong> project.</p>
 *
 * @author vince
 */
public class Domain {
    private static final Log logger = LogFactory.getLog(Domain.class);

    public static class Collection {
        public static final String DIS = "dis";
        public static final String MODELS = "models";
        public static final String NOTATIONS = "notations";
        public static final String SCHEMAS = "schemas";
        public static final String USERS = "users";
        public static final String USER_FEEDBACKS = "user.feedbacks";

        private Collection() {}
    }

    public static final class Definition {
        public static final String DIAGRAM = "diagram";
        public static final String PLANE = "plane";
        public static final String ELEMENT = "element";
        public static final String ROOT = "root";
        public static final String CHILD = "child";

        private Definition() {}
    }

    private static final Map<String, Domain> DEFINITIONS = new HashMap<>();
    private static final Map<String, Domain> DOMAINS = new HashMap<>();

    static {
        // TODO: this will be auto-generated from schemas => add a $collection field in schemas, mandatory for non-abstract schemas
        DEFINITIONS.put(Definition.DIAGRAM, new Domain(Definition.DIAGRAM, Collection.MODELS, Arrays.asList(
            "Model.Example.Diagram",
            "Model.BPMN.Diagram",
            "Model.FPMN.Diagram"
        )));
        DEFINITIONS.put(Definition.PLANE, new Domain(Definition.PLANE, Collection.DIS, Arrays.asList(
            "Di.Plane"
        )));
        DEFINITIONS.put(Definition.ELEMENT, new Domain(Definition.ELEMENT, Collection.DIS, Arrays.asList(
            "Di.Shape",
            "Di.Edge"
        )));
        DEFINITIONS.put(Definition.ROOT, new Domain(Definition.ROOT, Collection.MODELS, Arrays.asList(
            "Model.FPMN.Procedure"
        )));
        DEFINITIONS.put(Definition.CHILD, new Domain(Definition.CHILD, Collection.MODELS, Arrays.asList(
            "Model.FPMN.Label",
            "Model.FPMN.Phase",
            "Model.FPMN.Interaction.Start",
            "Model.FPMN.Interaction.End",
            "Model.FPMN.Interaction.Task",
            "Model.FPMN.Interaction.Task.Deadline",
            "Model.FPMN.Interaction.Decision",
            "Model.FPMN.Interaction.Sequence"
        )));
    }

    public static Domain ofDefinition(String concept) {
        return DEFINITIONS.get(concept);
    }

    public static Domain get(String $domain) {
        return DOMAINS.get($domain);
    }

    private final String definition;
    private final String collection;
    private final List<String> domains;
    private final JsonObject query;

    private Domain(String definition, String collection, List<String> domains) {
        this.definition = definition;
        this.collection = collection;
        this.domains = Collections.unmodifiableList(domains);
        this.query = new JsonObject()
            .put("$or", new JsonArray(
                domains.stream()
                       .map(domain -> new JsonObject().put("$domain", domain))
                       .collect(Collectors.toList())
            ));
        domains.forEach(domain -> {
            if (DOMAINS.containsKey(domain)) {
                logger.error("this call will overwrite the already registered domain \"" + domain + "\"");
            }
            DOMAINS.put(domain, this);
        });
        logger.debug("added domains for collection \"" + collection + "\": "
            + new JsonArray(domains).encodePrettily());
    }

    public String getDefinition() {
        return definition;
    }

    public String getCollection() {
        return collection;
    }

    public List<String> getDomains() {
        return domains;
    }

    public JsonObject getQuery() {
        return this.query;
    }

}