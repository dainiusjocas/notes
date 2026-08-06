package lt.jocas.examples;

import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Arrays;
import java.util.List;

public class NumberEmbedder implements Embedder {
    @Override
    public List<Integer> embed(String text, Context context) {
        return List.of();
    }

    /**
     * Converts an array of numbers into a mapped tensor.
     * @param input string is a list of number that can be parsed to double separated by comma.
     * @param context the context which may influence an embedder's behavior
     * @param tensorType the type of the tensor to be returned
     * @return
     */
    @Override
    public Tensor embed(String input, Context context, TensorType tensorType) {
        Tensor.Builder builder = Tensor.Builder.of(tensorType);
        List<Double> numbers = Arrays.stream(input.split(","))
                .map(String::trim)
                .map(Double::parseDouble)
                .toList();
        for (int offset = 0; offset < numbers.size(); offset++) {
            builder.cell(numbers.get(offset), offset);
        }
        return builder.build();
    }
}
