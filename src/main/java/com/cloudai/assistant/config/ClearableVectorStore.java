
package com.cloudai.assistant.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;

/**
 * Extends SimpleVectorStore with clear() and size() for hot-reindex support.
 */
public class ClearableVectorStore extends SimpleVectorStore {

    public ClearableVectorStore(EmbeddingModel embeddingModel) {
        super(embeddingModel);
    }

    public void clear() {
        this.store.clear();
    }

    public int size() {
        return this.store.size();
    }
}
