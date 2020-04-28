package org.elasticsearch.plugin.score;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MatchScore {

    private static final int weight =5;

    public static void main(String[] args) {
        String query = "有重复字符串哈哈哈的重复测试串";
        String value = "测试字符串以获得哈哈最小的误差重复测试";


        QueryMetaInfo queryMetaInfo = QueryMetaInfo.parseQuery(query);
        System.out.println(scoreWrapper(value, queryMetaInfo));
    }

    public static long scoreWrapper(String value, QueryMetaInfo queryMetaInfo) {
        value = value.toLowerCase();
        return score(value, queryMetaInfo);
    }

    private static long score(String value, QueryMetaInfo queryMetaInfo) {
        long matchScore = 0;
        // 将value串转换为index表
        char[] values = value.toCharArray();
        for (int j = 0; j < values.length; ) {
            List<Integer> queryIndexs = queryMetaInfo.getCharIndexs().get(values[j]);
            if (queryIndexs == null) {//query串没有出现
                j++;
                continue;
            } else {
                int maxLength = 0;
                int maxUnmatch = -1;
                String maxLengthPhase = "";
                for (int i = 0; i < queryIndexs.size(); i++) {
                    int queryIndex = queryIndexs.get(i);
                    int firstUnmatch = findFirstUnmatch(j, values, queryIndex, queryMetaInfo.getQueryChars());
                    int length = firstUnmatch - j;
//                    String phase = value.substring(j, firstUnmatch);
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
                matchScore += Math.pow(weight, maxLength);
                j = maxUnmatch;
            }
            //
        }
        return matchScore;
    }

    private static int findFirstUnmatch(int valueIndex, char[] valueChars, int queryindex, char[] queryChars) {
        for (; valueIndex < valueChars.length && queryindex < queryChars.length; ) {
            if (valueChars[valueIndex] == queryChars[queryindex]) {
                valueIndex++;
                queryindex++;
            } else {
                return valueIndex;
            }
        }
        if (valueIndex == valueChars.length)
            return valueChars.length;
        else {
            return valueIndex;
        }
    }


    public static class QueryMetaInfo {
        private final char[] queryChars;
        private final Map<Character, List<Integer>> charIndexs;

        private QueryMetaInfo(char[] queryChars, Map<Character, List<Integer>> charIndexs) {
            this.queryChars = queryChars;
            this.charIndexs = charIndexs;
        }

        public static final QueryMetaInfo parseQuery(String query) {
            query = query.toLowerCase();
            if (query.length() > 32) {
                query = query.substring(0, 32);
            }

            // 计算query串中的位置信息
            char[] querys = query.toCharArray();
            Map<Character, List<Integer>> charIndexs = new HashMap<>(32);
            for (int i = 0; i < querys.length; i++) {
                charIndexs.computeIfAbsent(querys[i], ArrayList::new);
                charIndexs.get(querys[i]).add(i);
            }
            return new QueryMetaInfo(querys, charIndexs);
        }


        public char[] getQueryChars() {
            return queryChars;
        }

        public Map<Character, List<Integer>> getCharIndexs() {
            return charIndexs;
        }
    }
}
