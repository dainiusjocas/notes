package lt.jocas.examples;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberToTensorEnbedderTest {

    NumberEmbedder embedder = new NumberEmbedder();

    @Test
    void testParsing() {
        var expected = Tensor.from("tensor<float>(x{}):{'0': 123}");
        var actual = embedder.embed("123", null, TensorType.fromSpec("tensor<float>(x{})"));
        assertEquals(expected, actual);

        expected = Tensor.from("tensor<float>(x{}):{'0': 123, '1': 456}");
        actual = embedder.embed("123, 456", null, TensorType.fromSpec("tensor<float>(x{})"));
        assertEquals(expected, actual);

        expected = Tensor.from("tensor<float>(offset{}):{'0': 123, '1': 456}");
        actual = embedder.embed("123, 456", null, TensorType.fromSpec("tensor<float>(offset{})"));
        assertEquals(expected, actual);
    }
}
