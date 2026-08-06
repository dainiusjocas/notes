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
     * @param context the context which may influence an embedder's behavior
     * @param tensorType the type of the tensor to be returned
     * @return
     */
    @Override
    public Tensor embed(String text, Context context, TensorType tensorType) {
        var parsed = Tensor.from(text);
        if (parsed.type().equals(tensorType)) {
            return parsed;
        } else {
            // only handles 1-dimensional tensor case, i.e., rename
            if (parsed.type().dimensionNames().size() == 1 && tensorType.dimensions().size() == 1) {
                return parsed.rename(
                        parsed.type().dimensions().get(0).name(),
                        tensorType.dimensions().get(0).name());
            }
        }
        throw new IllegalArgumentException("Cannot convert tensor from " + parsed.type() + " to " + tensorType);
    }
}
