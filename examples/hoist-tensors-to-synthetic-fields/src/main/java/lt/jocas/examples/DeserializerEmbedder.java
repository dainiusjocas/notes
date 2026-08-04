package lt.jocas.examples;

import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.List;

public class DeserializerEmbedder implements Embedder {

    @Override
    public List<Integer> embed(String text, Context context) {
        return List.of();
    }

    @Override
    public Tensor embed(String text, Context context, TensorType tensorType) {
        // TODO: we can adjust to the result tensorType in here
        return Tensor.from(text);
    }
}
