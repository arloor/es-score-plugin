package org.elasticsearch.plugin.score;

import java.util.HashMap;
import java.util.Map;

/**
 * 模式匹配算法
 * 查询词作为模式串滑动匹配待搜索文档，匹配上则记录匹配上的文本和文本长度
 * 文本去重，并按长度计算分值，分值计算逻辑为：10^n
 * 最后将分值总和减去未匹配上文本长度的加权值：∑10^k - sum(未匹配字数) * weight
 */
public class Horspool {

    /**
     * @param text    待匹配文本
     * @param pattern 模式串
     * @return score 分值
     */
    public static long calHorspoolScore(String text, String pattern) {
        text = text.toLowerCase();
        pattern = pattern.toLowerCase();

        long score = 0L;
        int m = pattern.length();
        int n = text.length();
        // 游标从0开始，以待匹配文本下标作为游标
        int cusor = 0;
        // 游标大于endFlag表示结束，模式串已完全滑过待匹配文本
        int endFlag = (m - 1) + (n - 1);
        // 分值暂存集合，key：匹配上的子串；value：匹配上的子串长度
        Map<String, Integer> groups = new HashMap<>();
        // 记录匹配的文本字数，用于反向计算未匹配文本字数
        int matchChars = 0;

        while (cusor <= endFlag) {
            // 模式子串匹配待匹配文本的起始下标
            int startMatchIndex = 0;
            // 模式子串匹配待匹配文本的截止下标，设置初始值未-1，是为了解决当模式子串第一个字符和待匹配文本匹配时，endMatchIndex = 0不代表未匹配的特殊场景
            int endMatchIndex = -1;
            // 连续匹配的子串长度
            int times = 0;

            // 循环子串计算对应字符是否匹配
            for (int i = 0; i < m; i++) {
                // 带匹配文本下标
                int textIndex = cusor - i;
                if (textIndex < 0 || textIndex >= n) {
                    continue;
                }

                char T = text.charAt(textIndex);
                char P = pattern.charAt(m - i - 1);

                if (T == P) {
                    times = times + 1;
                    // 匹配文本字数+1
                    matchChars = matchChars + 1;
                    if (endMatchIndex == -1) {
                        endMatchIndex = m - i - 1;
                    }
                    startMatchIndex = m - i - 1;
                } else {
                    if (times > 0) {
                        String matchPhase = pattern.substring(startMatchIndex, endMatchIndex + 1);
                        groups.put(matchPhase, matchPhase.length());
                        endMatchIndex = -1;
                    }
                    times = 0;
                }
            }

            if (endMatchIndex >= 0) {
                String matchPhase = pattern.substring(startMatchIndex, endMatchIndex + 1);
                groups.put(matchPhase, matchPhase.length());
            }

            cusor++;
        }

        for (Map.Entry<String, Integer> entry : groups.entrySet()) {
            score = score + (long) Math.pow(10, entry.getValue());
        }

        score = Math.max(score / 2, score - (n - matchChars) * 2);
        return score;
    }


    public static long cal(String text, String pattern) {
        long score = 0L;
        int m = pattern.length();
        int n = text.length();
        int endFlag = (m - 1) + (n - 1);

        int times = 0;
        int cusor = 0;
        while (cusor <= endFlag) {
            for (int i = 0; i < m; i++) {
                int textIndex = cusor - i;
                if (textIndex < 0 || textIndex >= n) {
                    continue;
                }

                char T = text.charAt(textIndex);
                char P = pattern.charAt(m - i - 1);

                if (T == P) {
                    times = times + 1;
                    score = score + (long) Math.pow(10, times);
                } else {
                    times = 0;
                }
            }
            cusor++;
        }

        return score;
    }

    private static boolean empty(String s) {
        return s == null || s.trim().length() == 0;
    }

    public static long calHorspoolScoreWrapper(String text, String pattern) {
        if (empty(text) || empty(pattern)) {
            return 0L;
        }
        // 超过30截断只取前30位匹配
        if (text.length() > 30) {
            text = text.substring(0, 30);
        }

        return calHorspoolScore(text, pattern);
    }

    public static void main(String[] args) {
        String text = "测试新建";

        String pattern = "测试";
        print(text, pattern);
    }

    public static void print(String text, String pattern) {
        long score = Horspool.calHorspoolScoreWrapper(text, pattern);
        System.out.println(pattern + ": " + score);
    }
}
