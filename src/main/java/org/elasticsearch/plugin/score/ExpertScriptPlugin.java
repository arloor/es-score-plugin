/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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
            // we use the script "source" as the script identifier
            if ("horspool".equals(scriptSource)) {
                ScoreScript.Factory factory = HorspoolLeafFactory::new;
                return context.factoryClazz.cast(factory);
            }
            if ("match_score".equals(scriptSource)) {
                ScoreScript.Factory factory = MatchScoreLeafFactory::new;
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
        private final MatchScore.QueryMetaInfo queryMetaInfo;

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
            query = query.toLowerCase();
            if (query.length() > 30) {
                query = query.substring(0, 30);
            }
            this.queryMetaInfo = MatchScore.QueryMetaInfo.parseQuery(query);
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
                            double score= MatchScore.scoreWrapper(value,queryMetaInfo);
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
