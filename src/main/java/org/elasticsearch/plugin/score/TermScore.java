package org.elasticsearch.plugin.score;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TermScore {
    private static final int weight = 4;

    public static void main(String[] args) {
        String query = "asdas 100.00 有重复字符串哈哈哈的重复测试串";
        String value = "asdas 100.00测试字符小的误差重复测试";
        TermsMetaInfo queryMetaInfo = TermsMetaInfo.parseQuery(query);
        System.out.println(scoreWrapper(value, queryMetaInfo));
    }

    /**
     * 模式串： query
     * 匹配串: value
     * 1. 预处理: 使用standard分词找到terms，生成模式串的term，indexList映射
     * 2. 对匹配串的每一个term{
     * getIndexList(term);获取模式串中indexList
     * 对indexList中的每个index{
     * 执行匹配，直到不匹配的时候，获得一个phase
     * }
     * 比较这些phase的个数，得到匹配串该term开头的最长phase
     * continue：从匹配串最长匹配的下一term继续(这么做是为了避免重复积分和提升效率)
     * }
     *
     * @param value  aa
     * @param termsMetaInfo  aaa
     * @return aaaa
     */

    public static long scoreWrapper(String value, TermsMetaInfo termsMetaInfo) {
        value = value.toLowerCase();
        return score(value, termsMetaInfo);
    }

    private static long score(String value, TermsMetaInfo queryMetaInfo) {
        long termScore = 0;
        // 将value串转换为index表

        StringReader reader = new StringReader(value);
        // 分词
        Analyzer analyzer = new StandardAnalyzer();
        TokenStream ts = analyzer.tokenStream("", reader);
        CharTermAttribute term = ts.getAttribute(CharTermAttribute.class);
        TypeAttribute type = ts.getAttribute(TypeAttribute.class);
        OffsetAttribute offset = ts.getAttribute(OffsetAttribute.class);
        // 遍历分词数据
        List<String> terms = new ArrayList<>();
        try {
            ts.reset();
            while (ts.incrementToken()) {
                String termStr = term.toString();
                terms.add(termStr);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            reader.close();
            analyzer.close();
        }

        for (int j = 0; j < terms.size(); ) {
            List<Integer> queryIndexs = queryMetaInfo.termIndexs.get(terms.get(j));
            if (queryIndexs == null) {//query串没有出现
                j++;
                continue;
            } else {
                int maxLength = 0;
                int maxUnmatch = -1;
//                String maxLengthPhase = "";
                for (int i = 0; i < queryIndexs.size(); i++) {
                    int queryIndex = queryIndexs.get(i);
                    int firstUnmatch = findFirstUnmatch(j, terms, queryIndex, queryMetaInfo.terms);
                    int length = firstUnmatch - j;

//                    String phase = "";
//                    for (int k = j; k < firstUnmatch; k++) {
//                        phase += terms.get(k) + "^";
//                    }
                    // 打印每个phase
//                    System.out.println(j + " " + length + " " + phase);
                    if (length > maxLength) {
                        maxLength = length;
//                        maxLengthPhase = phase;
                    }
                    if (firstUnmatch > maxUnmatch) {
                        maxUnmatch = firstUnmatch;
                    }
                }
                // 打印最长的phase
//                System.out.println("==max" + " " + maxLengthPhase);
                termScore += Math.pow(weight, maxLength);
                j = maxUnmatch;
                continue;
            }
        }
        return termScore;
    }

    private static int findFirstUnmatch(int valueIndex, List<String> valueTerms, int queryindex, List<String> queryTerms) {
        for (; valueIndex < valueTerms.size() && queryindex < queryTerms.size(); ) {
            if (valueTerms.get(valueIndex).equals(queryTerms.get(queryindex))) {
                valueIndex++;
                queryindex++;
            } else {
                return valueIndex;
            }
        }
        if (valueIndex == valueTerms.size())
            return valueTerms.size();
        else {
            return valueIndex;
        }
    }


    public static class TermsMetaInfo {
        protected final List<String> terms;
        protected final Map<String, List<Integer>> termIndexs;

        public TermsMetaInfo(List<String> terms, Map<String, List<Integer>> termIndexs) {
            this.terms = terms;
            this.termIndexs = termIndexs;
        }

        public static final TermsMetaInfo parseQuery(String query) {
            query = query.toLowerCase();
            if (query.length() > 32) {
                query = query.substring(0, 32);
            }
            // 计算query串中的位置信息
            StringReader reader = new StringReader(query);
            // 分词
            Analyzer analyzer = new StandardAnalyzer();
            TokenStream ts = analyzer.tokenStream("", reader);
            CharTermAttribute term = ts.getAttribute(CharTermAttribute.class);
            TypeAttribute type = ts.getAttribute(TypeAttribute.class);
            OffsetAttribute offset = ts.getAttribute(OffsetAttribute.class);
            // 遍历分词数据
            List<String> terms = new ArrayList<>();
            Map<String, List<Integer>> termIndexs = new HashMap<>(32);
            try {
                ts.reset();
                int index = 0;
                while (ts.incrementToken()) {
//                    System.out.println(offset.startOffset() + " - " + offset.endOffset() + " : " + term.toString() + " | " + type.type());
                    String termStr = term.toString();
                    terms.add(termStr);
                    termIndexs.computeIfAbsent(termStr, (cell) -> new ArrayList<>());
                    termIndexs.get(termStr).add(index);
                    index++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                reader.close();
                analyzer.close();
            }

            return new TermsMetaInfo(terms, termIndexs);
        }
    }
}
