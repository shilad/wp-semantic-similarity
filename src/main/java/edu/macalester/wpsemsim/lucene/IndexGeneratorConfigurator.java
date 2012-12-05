package edu.macalester.wpsemsim.lucene;


import edu.macalester.wpsemsim.sim.InLinkBooster;
import edu.macalester.wpsemsim.sim.esa.ESAAnalyzer;
import edu.macalester.wpsemsim.sim.esa.ESASimilarity;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import org.apache.commons.lang3.ArrayUtils;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import static edu.macalester.wpsemsim.utils.ConfigurationFile.*;

public class IndexGeneratorConfigurator {
    public static final Logger LOG = Logger.getLogger(IndexGeneratorConfigurator.class.getName());

    /**
     * Loads and configures all the index generators in a configuration file.
     * @param conf
     * @return
     * @throws ConfigurationException
     */
    public List<IndexGenerator> loadGenerators(PageInfo info, ConfigurationFile conf) throws ConfigurationException {
        return loadGenerators(info, conf, conf.getKeys("indexes"));
    }

    /**
     * Loads a specific subset of the index generators in a configuration file.
     * @param conf
     * @param names The names of the desired index generators, or null if all are desired.
     * @return
     * @throws ConfigurationException
     */
    public List<IndexGenerator> loadGenerators(PageInfo info, ConfigurationFile conf, Collection<String> names) throws ConfigurationException {
        List<IndexGenerator> generators = new ArrayList<IndexGenerator>();
        for (String key : conf.getKeys("indexes")) {
            if (key.equals("inputDir") || key.equals("outputDir")) {
                continue;
            }
            if (names == null || names.contains(key)) {
                JSONObject params = conf.get("indexes", key);
                info(conf, "loading metric " + key);
                generators.add(loadGenerator(info, key, params));
            }
        }
        return generators;
    }

    /**
     * Loads a specific index generator.
     * @param info
     * @param name
     * @param params
     * @return
     * @throws ConfigurationFile.ConfigurationException
     */
    private IndexGenerator loadGenerator(PageInfo info, String name, JSONObject params) throws ConfigurationFile.ConfigurationException {
        IndexGenerator g;
        String fields[] = requireListOfStrings(params, "fields").toArray(new String[0]);
        IndexGenerator fg = new IndexGenerator(info, fields);
        if (params.containsKey("minLinks")) {
            fg.setMinLinks(requireInteger(params, "minLinks"));
        }
        if (params.containsKey("minWords")) {
            fg.setMinWords(requireInteger(params, "minWords"));
        }
        if (params.containsKey("titleMultiplier")) {
            fg.setTitleMultiplier(requireInteger(params, "titleMultiplier"));
        }
        if (params.containsKey("addInLinksToText")) {
            fg.setAddInLinksToText(requireBoolean(params, "addInLinksToText"));
        }
        if (params.containsKey("skipDabs")) {
            fg.setSkipDabs(requireBoolean(params, "skipDabs"));
        }
        if (params.containsKey("skipLists")) {
            fg.setSkipLists(requireBoolean(params, "skipLists"));
        }
        if (params.containsKey("skipRedirects")) {
            fg.setSkipRedirects(requireBoolean(params, "skipRedirects"));
        }
        if (params.containsKey("namespaces")) {
            List<Integer> nss = requireListOfIntegers(params, "namespaces");
            fg.setNamespaces(ArrayUtils.toPrimitive(nss.toArray(new Integer[0])));
        }
        if (params.containsKey("similarity")) {
            String sim = requireString(params, "similarity");
            if (sim.equals("ESA")) {
                fg.setSimilarity(new ESASimilarity.LuceneSimilarity());
            } else {
                throw new ConfigurationFile.ConfigurationException("unknown similarity type: " + sim);
            }
        }
        if (params.containsKey("analyzer")) {
            String analyzer = requireString(params, "analyzer");
            if (analyzer.equals("ESA")) {
                fg.setAnalyzer(new ESAAnalyzer());
            } else {
                throw new ConfigurationFile.ConfigurationException("unknown analyzer type: " + analyzer);
            }
        }
        if (params.containsKey("booster")) {
            JSONObject boosterParams = (JSONObject) params.get("booster");
            String type = requireString(boosterParams, "type");
            if (type.equals("inlink")) {
                InLinkBooster booster = new InLinkBooster();
                if (boosterParams.containsKey("numLogs")) {
                    booster.setNumLogs(requireInteger(boosterParams, "numLogs"));
                }
                if (boosterParams.containsKey("pow")) {
                    booster.setPow(requireDouble(boosterParams, "pow"));
                }
                fg.setBooster(booster);
            } else {
                throw new ConfigurationFile.ConfigurationException("unknown booster type: " + type);
            }
        }
        g = fg;
        g.setName(name);
        return g;
    }

    private void info(ConfigurationFile conf, String message) {
        LOG.info("configurator for " + conf.getPath() + ": " + message);
    }
}
