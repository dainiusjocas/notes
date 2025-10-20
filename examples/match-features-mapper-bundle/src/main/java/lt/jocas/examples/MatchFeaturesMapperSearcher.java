package lt.jocas.examples;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

public class MatchFeaturesMapperSearcher extends Searcher {

    private final static String MF = "matchfeatures";
    // From here https://docs.vespa.ai/en/exposing-schema-information.html
    private final SchemaInfo schemaInfo;
    private final JsonNodeFactory jsonNodeFactory = JsonNodeFactory.instance;

    @Inject
    public MatchFeaturesMapperSearcher(SchemaInfo schemaInfo) {
        this.schemaInfo = schemaInfo;
    }

    @Override
    public Result search(Query query, Execution execution) {
        return execution.search(query);
    }

    private boolean isMatchFeaturesInSummary(Result result) {
        return result.getQuery().getPresentation().getSummaryFields().contains(MF);
    }

    private Object arrayFromTensorLabels(Tensor tensor) {
        Set<TensorAddress> tensorAddresses = tensor.cells().keySet();
        List<TextNode> labels = tensorAddresses.stream()
                .map(tensorAddress -> tensorAddress.label(0))
                .map(jsonNodeFactory::textNode)
                .toList();
        return jsonNodeFactory.arrayNode().addAll(labels);
    }

    private Object fromTensor(Tensor tensor, Field.Type.Kind kind) {
        Set<TensorAddress> tensorAddresses = tensor.cells().keySet();

        if (tensor.type().rank() == 0)
            return tensor.asDouble();

        if (tensorAddresses.size() == 1) {
            return tensorAddresses.iterator().next().label(0);
        }
        return arrayFromTensorLabels(tensor);
    }

    private boolean boolFromTensor(Tensor tensor) {
        Double value = tensor.cells().values().iterator().next();
        return !value.equals(0.0d);
    }

    private long longFromTensor(Tensor tensor) {
        Set<TensorAddress> tensorAddresses = tensor.cells().keySet();
        String label = tensorAddresses.iterator().next().label(0);
        return Long.parseLong(label);
    }

    private void handleHit(Hit hit, Schema schema) {
        var matchFeatures = (FeatureData) hit.fields().get(MF);
        for (String featureName : matchFeatures.featureNames()) {
            Tensor tensor = matchFeatures.getTensor(featureName);

            if (!schema.fields().containsKey(featureName)) {
                // Schema does not have a named field then just remap
                hit.setField(featureName, tensor);
            } else {
                // Schema contains a named field then make values correctly renderable
                Field.Type.Kind kind = schema.fields().get(featureName).type().kind();
                var value = switch (kind) {
//                        case ANNOTATIONREFERENCE -> null;
                    case ARRAY -> arrayFromTensorLabels(tensor);
                    case BOOL -> boolFromTensor(tensor);
                    case BYTE, INT, LONG -> longFromTensor(tensor);
                    case FLOAT, DOUBLE -> tensor.asDouble(); // no need to wrap it
//                        case MAP -> null;
//                        case POSITION -> null;
//                        case PREDICATE -> null;
//                        case RAW -> null;
//                        case REFERENCE -> null;
                    case STRING -> fromTensor(tensor, kind);
//                        case STRUCT -> null;
                    case TENSOR -> tensor;
//                        case URL -> null;
//                        case WEIGHTEDSET -> null;
                    default -> tensor;
                };
                hit.setField(featureName, value);
            }
        }
        hit.removeField(MF);
    }

    private void fromMatchFeatures(Result result) {
        // If matchfeatures are not asked, then we have nothing to do
        if (!isMatchFeaturesInSummary(result)) return;

        Set<String> restrict = result.getQuery().getModel().getRestrict();
        // If more than one schema is searched, then we have to have the sddocname
        // in each hit to properly resolve, which requires summary filling...
        if (restrict.size() != 1) return;

        String schemaName = restrict.iterator().next();
        Schema schema = schemaInfo.schemas().get(schemaName);

        result.hits().forEach(hit -> handleHit(hit, schema));
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        execution.fill(result, summaryClass);
        fromMatchFeatures(result);
    }
}
