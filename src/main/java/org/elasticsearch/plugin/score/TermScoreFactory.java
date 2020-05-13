package org.elasticsearch.plugin.score;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.elasticsearch.plugin.score.impl.TermScore;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.search.lookup.LeafDocLookup;
import org.elasticsearch.search.lookup.SearchLookup;
import org.elasticsearch.search.lookup.SourceLookup;

import java.util.Map;

public class TermScoreFactory extends ScoreLeafFactory {
    private final TermScore.TermsMetaInfo queryMetaInfo;

    protected TermScoreFactory(Map<String, Object> params, SearchLookup lookup) {
        super(params, lookup);
        this.queryMetaInfo = TermScore.TermsMetaInfo.parseQuery(query);
    }

    @Override
    public ScoreScript newInstance(LeafReaderContext context) {
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
