package lt.jocas.examples;

import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.List;

public class NumberEmbedder implements Embedder {
    @Override
    public List<Integer> embed(String text, Context context) {
        return List.of();
    }

    /**
     * Converts an array of numbers into a mapped tensor.
     * @param input string is a list of number that can be parsed to double separated by comma.
     * @param context the context that may influence an embedder's behavior
     * @param tensorType the type of the tensor to be returned
     * @return Tensor of tensorType
     */
    @Override
    public Tensor embed(String input, Context context, TensorType tensorType) {
        var builder = Tensor.Builder.of(tensorType);
        var values = List.of(input.split(","));
        for (int offset = 0; offset < values.size(); offset++)
            builder.cell(Double.parseDouble(values.get(offset).trim()), offset);
        return builder.build();
    }
}
