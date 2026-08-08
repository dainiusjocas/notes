package lt.jocas.examples;

import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeserializerEmbedderTest {
    Embedder embedder = new DeserializerEmbedder();
    @Test
    void testDeserialize() {
        var expected = Tensor.from("tensor<float>(x[1]):[3]");
        var actual = embedder.embed(
                "tensor<float>(x[1]):[3]",
                null,
                TensorType.fromSpec("tensor<float>(x[1])"));
        assertEquals(expected, actual);
    }
    @Test
    void testDeserializeAndRename() {
        var expected = Tensor.from("tensor<float>(y[1]):[3]");
        var actual = embedder.embed(
                "tensor<float>(x[1]):[3]",
                null,
                TensorType.fromSpec("tensor<float>(y[1])"));
        assertEquals(expected, actual);
    }
    @Test
    void testDeserializeAndRenameMappedToIndexed() {
        assertThrows(IllegalArgumentException.class, () -> embedder.embed(
                "tensor<float>(x{}):{'2': 3}",
                null,
                TensorType.fromSpec("tensor<float>(y[1])")));
    }
    @Test
    void testDeserializeAndRenameIndexed() {
        var expected = Tensor.from("tensor<float>(a{},y[1]):{'test':[3]}");
        var actual = embedder.embed(
                "tensor<float>(a{},x[1]):{'test': [3]}",
                null,
                TensorType.fromSpec("tensor<float>(a{},y[1])"));
        assertEquals(expected, actual);
    }
}
