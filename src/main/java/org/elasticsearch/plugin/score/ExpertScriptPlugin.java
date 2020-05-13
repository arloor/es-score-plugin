package org.elasticsearch.plugin.score;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;

import java.util.Collection;
import java.util.HashMap;
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
            factoryLookUp.put("horspool", HorspoolFactory::new);
            factoryLookUp.put("match_score", MatchScoreFactory::new);
            factoryLookUp.put("term_score", TermScoreFactory::new);
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
    }
    // end::expert_engine
}
