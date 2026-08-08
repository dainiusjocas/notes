package lt.jocas.examples;

import com.google.common.collect.Sets;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.List;

public class DeserializerEmbedder implements Embedder {

    @Override
    public List<Integer> embed(String text, Context context) {
        return List.of();
    }

    /**
     * Converts a serialized tensor into a tensor.
     * In case the target type differs in one dimension, renames the dimension.
     * @param text serialized tensor.
     * @param context the context that may influence an embedder's behavior
     * @param tensorType the type of the tensor to be returned
     * @return parsed and adapted tensor.
     */
    @Override
    public Tensor embed(String text, Context context, TensorType tensorType) {
        var parsed = Tensor.from(text);
        var parsedType = parsed.type();

        if (parsedType.equals(tensorType)) return parsed;

        if (parsedType.dimensions().size() == 1 && tensorType.dimensions().size() == 1) {
            return parsed.rename(
                    parsed.type().dimensions().get(0).name(),
                    tensorType.dimensions().get(0).name());
        }
        throw new IllegalArgumentException("Cannot convert tensor from " + parsedType + " to " + tensorType);
    }
}
