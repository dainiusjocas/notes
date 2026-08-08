package lt.jocas.examples;

import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.List;
import java.util.Optional;

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
        return handleExactMatch(parsed, tensorType)
                .or(() -> handleSingleDimension(parsed, tensorType))
                .or(() -> handleLimitedDimensions(parsed, tensorType))
                .orElseThrow(() -> new IllegalArgumentException("Cannot convert tensor from " + parsed.type() + " to " + tensorType));
    }

    /**
     * When parsed and target tensor types have exactly one mapped and one indexed dimensions,
     * renames the dimensions to the target dimensions.
     * @param parsed tensor to be renamed.
     * @param tensorType target tensor type.
     * @return Optional renamed tensor.
     */
    private Optional<? extends Tensor> handleLimitedDimensions(Tensor parsed, TensorType tensorType) {
        var parsedType = parsed.type();

        if (parsedType.mappedSubtype().dimensions().size() == 1 &&
                parsedType.indexedSubtype().dimensions().size() == 1 &&
                tensorType.mappedSubtype().dimensions().size() == 1 &&
                tensorType.indexedSubtype().dimensions().size() == 1) {

            var parsedMappedDim = parsedType.mappedSubtype().dimensions().get(0).name();
            var parsedIndexedDim = parsedType.indexedSubtype().dimensions().get(0).name();
            var targetMappedDim = tensorType.mappedSubtype().dimensions().get(0).name();
            var targetIndexedDim = tensorType.indexedSubtype().dimensions().get(0).name();

            return Optional.of(parsed
                    .rename(parsedMappedDim, targetMappedDim)
                    .rename(parsedIndexedDim, targetIndexedDim));
        }

        return Optional.empty();
    }

    private Optional<Tensor> handleExactMatch(Tensor tensor, TensorType tensorType) {
        if (tensor.type().equals(tensorType)) return Optional.of(tensor);
        return Optional.empty();
    }

    private Optional<Tensor> handleSingleDimension(Tensor tensor, TensorType tensorType) {
        var parsedType = tensor.type();
        if ((parsedType.dimensions().size() == 1 && tensorType.dimensions().size() == 1) &&
                ((parsedType.hasIndexedDimensions() && tensorType.hasIndexedDimensions()) ||
                        (parsedType.hasMappedDimensions() && tensorType.hasMappedDimensions()))) {
            return Optional.of(tensor.rename(
                    parsedType.dimensions().get(0).name(),
                    tensorType.dimensions().get(0).name()));
        }
        return Optional.empty();
    }
}
