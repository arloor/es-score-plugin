package org.elasticsearch.plugin.score;

import org.apache.lucene.index.LeafReaderContext;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.search.lookup.SearchLookup;

import java.util.List;
import java.util.Map;

public abstract class ScoreLeafFactory implements ScoreScript.LeafFactory {
    protected final Map<String, Object> params;
    protected final SearchLookup lookup;
    protected final List<String> fields;
    protected final String query;

    public ScoreLeafFactory(
            Map<String, Object> params, SearchLookup lookup) {
        if (params.containsKey("field") == false) {
            throw new IllegalArgumentException(
                "Missing parameter [field]");
        }
        if (params.containsKey("query") == false) {
            throw new IllegalArgumentException(
                "Missing parameter [query]");
        }

        this.params = params;
        this.lookup = lookup;
        fields = (List<String>) params.get("field");
        query = params.get("query").toString();
    }

    @Override
    public boolean needs_score() {
        return true;  // Return true if the script needs the score
    }

    @Override
    public abstract ScoreScript newInstance(LeafReaderContext context);
}
