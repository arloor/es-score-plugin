package org.elasticsearch.plugin.score;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MatchScore {

    private static final int weight = 4;

    public static void main(String[] args) {
        String query = "有重复字符串哈哈哈的重复测试串";
        String value = "测试字符小的误差重复测试";

        MatchsMetaInfo matchsMetaInfo = MatchsMetaInfo.parseQuery(query);
        System.out.println(scoreWrapper(value, matchsMetaInfo));
    }

    /**
     * 模式串： query
     * 匹配串: value
     * 1. 预处理，生成模式串的char，indexList映射
     * 2. 对匹配串的每一个char{
     * getIndexList(char);获取模式串中indexList
     * 对indexList中的每个index{
     * 执行匹配，直到不匹配的时候，获得一个phase
     * }
     * 比较这些phase的长度，得到匹配串该char开头的最长phase
     * continue：从匹配串最长匹配的下一char继续(这么做是为了避免重复积分和提升效率)
     * }
     *
     * @param value aaa
     * @param matchsMetaInfo aa
     * @return  aaaa
     */
    public static long scoreWrapper(String value, MatchsMetaInfo matchsMetaInfo) {
        value = value.toLowerCase();
        return score(value, matchsMetaInfo);
    }

    private static long score(String value, MatchsMetaInfo matchsMetaInfo) {
        long matchScore = 0;
        // 将value串转换为index表
        char[] values = value.toCharArray();
        for (int j = 0; j < values.length; ) {
            List<Integer> queryIndexs = matchsMetaInfo.getCharIndexs().get(values[j]);
            if (queryIndexs == null) {//query串没有出现
                j++;
                continue;
            } else {
                int maxLength = 0;
                int maxUnmatch = -1;
//                String maxLengthPhase = "";
                for (int i = 0; i < queryIndexs.size(); i++) {
                    int queryIndex = queryIndexs.get(i);
                    int firstUnmatch = findFirstUnmatch(j, values, queryIndex, matchsMetaInfo.getQueryChars());
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
                continue;
            }
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


    public static class MatchsMetaInfo {
        private final char[] queryChars;
        private final Map<Character, List<Integer>> charIndexs;

        private MatchsMetaInfo(char[] queryChars, Map<Character, List<Integer>> charIndexs) {
            this.queryChars = queryChars;
            this.charIndexs = charIndexs;
        }

        public static final MatchsMetaInfo parseQuery(String query) {
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
            return new MatchsMetaInfo(querys, charIndexs);
        }


        public char[] getQueryChars() {
            return queryChars;
        }

        public Map<Character, List<Integer>> getCharIndexs() {
            return charIndexs;
        }
    }
}
