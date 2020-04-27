## elasticsearch horspool评分插件

拓展script_score来实现自定义评分方式的es插件

使用horspool来计算文档评分，而不是es内置的bm25算法

使用:

```json
{
  "query": {
    "function_score": {
      "query": {
        "match": {
          "body": "查询词"
        }
      },
      "boost_mode": "replace",
      "functions": [
        {
          "script_score": {
            "script": {
              "source": "horspool",
              "lang": "expert_scripts",
              "params": {
                "field": ["title^2","body^1"],
                "query": "查询词"
              }
            }
          }
        }
      ]
    }
  }
}
```