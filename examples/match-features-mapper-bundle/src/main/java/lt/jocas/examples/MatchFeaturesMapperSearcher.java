package lt.jocas.examples;

import com.yahoo.prelude.fastsearch.PartialSummaryHandler;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.yahoo.prelude.fastsearch.PartialSummaryHandler.PRESENTATION;

public class MatchFeaturesMapperSearcher extends Searcher {

    private final static String MF = "matchfeatures";
    private final SchemaInfo schemaInfo;

    @Inject
    public MatchFeaturesMapperSearcher(SchemaInfo schemaInfo) {
        this.schemaInfo = schemaInfo;
    }

    @Override
    public Result search(Query query, Execution execution) {
        return execution.search(query);
    }

    private static final Set<String> IGNORED_SUMMARY_FIELDS = Set.of(MF);

    private boolean isFillIgnorable(String summaryClass, Result result) {
        return PRESENTATION.equals(summaryClass) &&
                result.getQuery().getPresentation().getSummaryFields().equals(IGNORED_SUMMARY_FIELDS);
    }

    public Result fromMatchFeatures(Result result) {
        for (var hit : result.hits().asList()) {
            String source = result.getQuery().getModel().getSources().iterator().next(); // or maybe from the "restrict" property?
            Schema schema = schemaInfo.schemas().get(source);
            Map<String, Field> schemaFields = schema.fields();
            Map<String, Object> fields = hit.fields();
            var mfField = (FeatureData) fields.get(MF);
            for (String featureName : mfField.featureNames()) {
                Tensor tensor = mfField.getTensor(featureName);
                TensorType type = tensor.type();
                if (type.rank() == 0) {
                    hit.setField(featureName, tensor.asDouble());
                } else if (type.rank() == 1) {
                    if (schemaFields.containsKey(featureName)
                            && schemaFields.get(featureName).type().kind().equals(Field.Type.Kind.TENSOR)) {
                        hit.setField(featureName, tensor);
                    } else {
                        Set<TensorAddress> tensorAddresses = tensor.cells().keySet();
                        if (tensorAddresses.size() == 1) {
                            String label = tensorAddresses.iterator().next().label(0);
                            hit.setField(featureName, label);
                        } else {
                            List<String> labels = tensorAddresses.stream()
                                    .map(tensorAddress -> tensorAddress.label(0))
                                    .toList();
                            hit.setField(featureName, labels);
                        }
                    }

                }
            }
            hit.removeField(MF);
        }
        return result;
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        var adjustedSummaryClass = summaryClass;
        if (isFillIgnorable(summaryClass, result)) {
            // The `default` class works here because later Searcher class Vespa
            // checks that only some fields are needed
            // https://github.com/vespa-engine/vespa/blob/5dbf6c9d7feecd452dff7d494c540b0d31cf02bc/container-search/src/main/java/com/yahoo/prelude/fastsearch/PartialSummaryHandler.java#L239-L248
            // and creates synthetic names for which it checks
            // https://github.com/vespa-engine/vespa/blob/74e5c519e3d392b8ef34ced618fb90c7910adeb9/container-search/src/main/java/com/yahoo/search/Searcher.java#L164
            // whether they are already fetched,
            // thus declaring that for `.fill()` there is nothing to do.
            // Or this can be set to null, the effect is the same.
            adjustedSummaryClass = PartialSummaryHandler.DEFAULT_CLASS;
        }
        result = fromMatchFeatures(result);
        execution.fill(result, adjustedSummaryClass);
    }
}
