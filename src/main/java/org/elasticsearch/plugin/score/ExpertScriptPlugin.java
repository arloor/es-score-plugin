package org.elasticsearch.plugin.score;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.script.ScoreScript.LeafFactory;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.search.lookup.LeafDocLookup;
import org.elasticsearch.search.lookup.SearchLookup;
import org.elasticsearch.search.lookup.SourceLookup;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An example script plugin that adds a {@link ScriptEngine} implementing expert scoring.
 */
public class ExpertScriptPlugin extends Plugin implements ScriptPlugin {
    protected static final Logger logger = LogManager.getLogger(ExpertScriptPlugin.class);

    @Override
    public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
        return new MyExpertScriptEngine();
    }

    /**
     * An example {@link ScriptEngine} that uses Lucene segment details to implement pure document frequency scoring.
     */
    // tag::expert_engine
    private static class MyExpertScriptEngine implements ScriptEngine {

        private static final Map<String, ScoreScript.Factory> factoryLookUp = new HashMap<>();

        static {
            factoryLookUp.put("horspool", HorspoolLeafFactory::new);
            factoryLookUp.put("match_score", MatchScoreLeafFactory::new);
            factoryLookUp.put("term_score", TermScoreLeafFactory::new);
        }

        @Override
        public String getType() {
            return "expert_scripts";
        }

        @Override
        public <T> T compile(String scriptName, String scriptSource,
                             ScriptContext<T> context, Map<String, String> params) {
            if (context.equals(ScoreScript.CONTEXT) == false) {
                throw new IllegalArgumentException(getType()
                    + " scripts cannot be used for context ["
                    + context.name + "]");
            }
            ScoreScript.Factory factory = factoryLookUp.get(scriptSource);
            if (factory != null) {
                return context.factoryClazz.cast(factory);
            }
            throw new IllegalArgumentException("Unknown script name "
                + scriptSource);
        }

        @Override
        public void close() {
            // optionally close resources
        }

        private static class HorspoolLeafFactory implements LeafFactory {
            private final Map<String, Object> params;
            private final SearchLookup lookup;
            private final List<String> fields;
            private final String query;

            private HorspoolLeafFactory(
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
            public ScoreScript newInstance(LeafReaderContext context)
                throws IOException {
                LeafReader reader = context.reader();
                SourceLookup source = lookup.getLeafSearchLookup(context).source();
                LeafDocLookup doc = lookup.getLeafSearchLookup(context).doc();
                return new ScoreScript(params, lookup, context) {
                    int currentDocid = -1;

                    @Override
                    public void setDocument(int docid) {
                        currentDocid = docid;
                    }

                    @Override
                    public double execute() {
                        //获取原来的评分
                        double rawScore = this.get_score();
                        final double[] maxScore = {0.0};
                        fields.forEach(fieldWeight -> {
                            String[] split = fieldWeight.split("\\^");
                            String field = split[0];
                            double weight = split.length == 2 ? Double.parseDouble(split[1]) : 1;
                            source.setSegmentAndDocument(context, currentDocid);
                            String value = "";
                            if (source.containsKey(field)) {
                                value = String.valueOf(source.get(field));
                            } else {
                                return;
                            }
                            double score = Horspool.calHorspoolScoreWrapper(value, query);
//                            double score=MatchScore.scoreWrapper(value,query);
                            score = score * weight;
                            if (score > maxScore[0]) {
                                maxScore[0] = score;
                            }
                        });
                        return maxScore[0];
                    }
                };
            }
        }
    }

    private static class MatchScoreLeafFactory implements LeafFactory {
        private final Map<String, Object> params;
        private final SearchLookup lookup;
        private final List<String> fields;
        private final MatchScore.MatchsMetaInfo matchsMetaInfo;

        private MatchScoreLeafFactory(
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
            String query = params.get("query").toString();
            this.matchsMetaInfo = MatchScore.MatchsMetaInfo.parseQuery(query);
        }

        @Override
        public boolean needs_score() {
            return true;  // Return true if the script needs the score
        }

        @Override
        public ScoreScript newInstance(LeafReaderContext context)
            throws IOException {
            LeafReader reader = context.reader();
            SourceLookup source = lookup.getLeafSearchLookup(context).source();
            LeafDocLookup doc = lookup.getLeafSearchLookup(context).doc();
            return new ScoreScript(params, lookup, context) {
                int currentDocid = -1;

                @Override
                public void setDocument(int docid) {
                    currentDocid = docid;
                }

                @Override
                public double execute() {
                    //获取原来的评分
                    double rawScore = this.get_score();
                    final double[] maxScore = {0.0};
                    fields.forEach(fieldWeight -> {
                        String[] split = fieldWeight.split("\\^");
                        String field = split[0];
                        double weight = split.length == 2 ? Double.parseDouble(split[1]) : 1;
                        source.setSegmentAndDocument(context, currentDocid);
                        String value = "";
                        if (source.containsKey(field)) {
                            value = String.valueOf(source.get(field));
                        } else {
                            return;
                        }
                        double score = MatchScore.scoreWrapper(value, matchsMetaInfo);
                        score = score * weight;
                        if (score > maxScore[0]) {
                            maxScore[0] = score;
                        }
                    });
                    return maxScore[0];
                }
            };
        }
    }

    private static class TermScoreLeafFactory implements LeafFactory {
        private final Map<String, Object> params;
        private final SearchLookup lookup;
        private final List<String> fields;
        private final TermScore.TermsMetaInfo queryMetaInfo;

        private TermScoreLeafFactory(
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
            String query = params.get("query").toString();
            this.queryMetaInfo = TermScore.TermsMetaInfo.parseQuery(query);
        }

        @Override
        public boolean needs_score() {
            return true;  // Return true if the script needs the score
        }

        @Override
        public ScoreScript newInstance(LeafReaderContext context)
            throws IOException {
            LeafReader reader = context.reader();
            SourceLookup source = lookup.getLeafSearchLookup(context).source();
            LeafDocLookup doc = lookup.getLeafSearchLookup(context).doc();
            return new ScoreScript(params, lookup, context) {
                int currentDocid = -1;

                @Override
                public void setDocument(int docid) {
                    currentDocid = docid;
                }

                @Override
                public double execute() {
                    //获取原来的评分
                    double rawScore = this.get_score();
                    final double[] maxScore = {0.0};
                    fields.forEach(fieldWeight -> {
                        String[] split = fieldWeight.split("\\^");
                        String field = split[0];
                        double weight = split.length == 2 ? Double.parseDouble(split[1]) : 1;
                        source.setSegmentAndDocument(context, currentDocid);
                        String value = "";
                        if (source.containsKey(field)) {
                            value = String.valueOf(source.get(field));
                        } else {
                            return;
                        }
                        double score = TermScore.scoreWrapper(value, queryMetaInfo);
                        score = score * weight;
                        if (score > maxScore[0]) {
                            maxScore[0] = score;
                        }
                    });
                    return maxScore[0];
                }
            };
        }
    }
    // end::expert_engine
}
