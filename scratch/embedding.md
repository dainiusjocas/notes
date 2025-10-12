# Embedding

Vespa supports embedding strings (or array of strings) with a huggingface embedder.
This enables nearest neighbor search.


However, if your model needs more inputs than text you have to write a custom component and use it in the document processing chain of in a searcher. And it must be deployed in the container nodes.

This means that you are calling the ONNX model evaluator.

Surprisingly, this is the same as calling a ranking function in the Java code. And ranking functions can be evaluated in both container and content nodes.

Which means that you could do embedding also in the content node!
In this case you query for a specific document, take the required fields, query input params, call onnx function on the doc, return the output in the `match-features`. Open question what to do with the item score.
