# How to defer chunking with Vespa?

We could avoid chunking by storing only tokens of the text.

To some extent also image patches might work.

We would need to use Vespa's [Huggingface tokenizer embedder](https://docs.vespa.ai/en/reference/embedding-reference.html#huggingface-tokenizer-embedder) and then store raw token ids in the content nodes.

Benefits:
- We can avoid a decision of specifying the exact model
- We avoid chunking decision upfront
- Vespa 2nd phase ranking could be used


Downsides:
- No HNSW
- No embeddings to store (int is good enough for multilingual)
- Latency increases by doing inference in the content nodes

Open questions:
- Maybe we could use WAND if we also store it as a weighted set?
- Maybe it would be possible to get matched keyword position and get the tokenized tokens based on that for further inference?
